<#
    Removes one job bundle and its job-scoped generated model artifacts.

    Purpose:
    - delete the selected bundle rooted at the chosen job-config folder
    - delete matching generated sources under target/generated-sources/etl
    - delete matching compiled generated classes under target/classes and target/test-classes

    Safety:
    - private bundles under private-jobs/ can be removed directly
    - shared or preserved bundles require -DeleteSharedBundle
    - generated packages still referenced by other job bundles are skipped unless -ForcePackageCleanup is supplied
    - use -WhatIf first when you want a dry run

    Example (private bundle):
    powershell.exe -ExecutionPolicy Bypass -File .\scripts\remove-job-bundle.ps1 `
        -JobConfigPath .\private-jobs\local-verification\transaction-xml-to-json\config\job-config.yaml

    Example (shared preserved bundle, explicit opt-in):
    powershell.exe -ExecutionPolicy Bypass -File .\scripts\remove-job-bundle.ps1 `
        -JobConfigPath .\src\main\resources\config-jobs\xml-to-json-events\job-config.yaml `
        -DeleteSharedBundle -WhatIf
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [string]$JobConfigPath,

    [string]$RepoRoot = "C:\spring-etl-engine",

    [switch]$DeleteSharedBundle,

    [switch]$ForcePackageCleanup,

    [switch]$PassThru
)

$ErrorActionPreference = 'Stop'

function Resolve-NormalizedPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [string]$BasePath
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "Path must not be blank."
    }

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    if ([string]::IsNullOrWhiteSpace($BasePath)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $BasePath $Path))
}

function Get-YamlScalarValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string]$Key
    )

    if (-not (Test-Path $FilePath)) {
        return $null
    }

    $pattern = '(?m)^\s*' + [regex]::Escape($Key) + '\s*:\s*(.+?)\s*$'
    $match = [regex]::Match((Get-Content -Path $FilePath -Raw), $pattern)
    if (-not $match.Success) {
        return $null
    }

    $value = $match.Groups[1].Value.Trim()
    if ($value.Contains('#')) {
        $value = $value.Split('#')[0].Trim()
    }
    if (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"'))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    return $value
}

function Get-YamlPackageNames {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath
    )

    if (-not (Test-Path $FilePath)) {
        return @()
    }

    $content = Get-Content -Path $FilePath -Raw
    $matches = [regex]::Matches($content, '(?m)^\s*packageName\s*:\s*(.+?)\s*$')
    $packages = New-Object System.Collections.Generic.List[string]
    foreach ($match in $matches) {
        $value = $match.Groups[1].Value.Trim()
        if ($value.Contains('#')) {
            $value = $value.Split('#')[0].Trim()
        }
        if (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"'))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if (-not [string]::IsNullOrWhiteSpace($value) -and -not $packages.Contains($value)) {
            $packages.Add($value) | Out-Null
        }
    }
    return $packages.ToArray()
}

function Get-YamlEntryCount {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath
    )

    if (-not (Test-Path $FilePath)) {
        return 0
    }

    return ([regex]::Matches((Get-Content -Path $FilePath -Raw), '(?m)^\s*-\s+format\s*:')).Count
}

function Normalize-JobPackageSegment {
    param(
        [string]$JobName
    )

    if ([string]::IsNullOrWhiteSpace($JobName)) {
        return 'selectedjob'
    }

    $builder = New-Object System.Text.StringBuilder
    foreach ($character in $JobName.Trim().ToCharArray()) {
        if (($character -ge 'a' -and $character -le 'z') -or ($character -ge 'A' -and $character -le 'Z') -or ($character -ge '0' -and $character -le '9')) {
            [void]$builder.Append([char]::ToLowerInvariant($character))
        }
    }

    $normalized = $builder.ToString()
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        return 'selectedjob'
    }

    if ($normalized[0] -ge '0' -and $normalized[0] -le '9') {
        return 'job' + $normalized
    }

    return $normalized
}

function Get-DefaultGeneratedPackages {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JobName
    )

    $segment = Normalize-JobPackageSegment -JobName $JobName
    return @(
        "com.etl.generated.job.$segment.source",
        "com.etl.generated.job.$segment.target"
    )
}

function Get-BundleRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedJobConfigPath
    )

    $jobConfigDirectory = Split-Path -Parent $ResolvedJobConfigPath
    if ((Split-Path -Leaf $jobConfigDirectory).Equals('config', [System.StringComparison]::OrdinalIgnoreCase)) {
        return Split-Path -Parent $jobConfigDirectory
    }
    return $jobConfigDirectory
}

function Test-PathIsUnderRoot {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CandidatePath,
        [Parameter(Mandatory = $true)]
        [string]$RootPath
    )

    $normalizedCandidate = [System.IO.Path]::GetFullPath($CandidatePath).TrimEnd('\') + '\'
    $normalizedRoot = [System.IO.Path]::GetFullPath($RootPath).TrimEnd('\') + '\'
    return $normalizedCandidate.StartsWith($normalizedRoot, [System.StringComparison]::OrdinalIgnoreCase)
}

function Remove-EmptyAncestorDirectories {
    param(
        [string]$StartingPath,
        [string]$StopPath
    )

    if ([string]::IsNullOrWhiteSpace($StartingPath) -or [string]::IsNullOrWhiteSpace($StopPath)) {
        return
    }

    $currentPath = [System.IO.Path]::GetFullPath($StartingPath)
    $normalizedStopPath = [System.IO.Path]::GetFullPath($StopPath).TrimEnd('\')

    while ($currentPath.Length -gt 0 -and $currentPath.StartsWith($normalizedStopPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        if (-not (Test-Path $currentPath)) {
            $currentPath = Split-Path -Parent $currentPath
            continue
        }

        $children = @(Get-ChildItem -Path $currentPath -Force -ErrorAction SilentlyContinue)
        if ($children.Count -gt 0) {
            break
        }

        Remove-Item -Path $currentPath -Force
        if ($currentPath.Equals($normalizedStopPath, [System.StringComparison]::OrdinalIgnoreCase)) {
            break
        }
        $currentPath = Split-Path -Parent $currentPath
    }
}

function Remove-PathIfPresent {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Description,
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [System.Collections.Generic.List[string]]$RemovedPaths,
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [System.Collections.Generic.List[string]]$MissingPaths,
        [string]$CleanupRoot
    )

    if (-not (Test-Path $Path)) {
        $MissingPaths.Add($Path) | Out-Null
        return
    }

    if ($PSCmdlet.ShouldProcess($Path, "Remove $Description")) {
        Remove-Item -Path $Path -Recurse -Force
        $RemovedPaths.Add($Path) | Out-Null
        if (-not [string]::IsNullOrWhiteSpace($CleanupRoot)) {
            Remove-EmptyAncestorDirectories -StartingPath (Split-Path -Parent $Path) -StopPath $CleanupRoot
        }
    }
}

function Get-BundleKind {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BundleRoot,
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $privateJobsRoot = Join-Path $RepoRoot 'private-jobs'
    $preservedRoot = Join-Path $RepoRoot 'src\main\resources\config-jobs'

    if (Test-PathIsUnderRoot -CandidatePath $BundleRoot -RootPath $privateJobsRoot) {
        return 'private'
    }
    if (Test-PathIsUnderRoot -CandidatePath $BundleRoot -RootPath $preservedRoot) {
        return 'preserved'
    }
    if (Test-PathIsUnderRoot -CandidatePath $BundleRoot -RootPath $RepoRoot) {
        return 'repo-shared'
    }
    return 'external'
}

function Get-JobDescriptor {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedJobConfigPath,
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    if (-not (Test-Path $ResolvedJobConfigPath)) {
        throw "Job config path not found: $ResolvedJobConfigPath"
    }

    $bundleRoot = Get-BundleRoot -ResolvedJobConfigPath $ResolvedJobConfigPath
    $jobConfigDirectory = Split-Path -Parent $ResolvedJobConfigPath
    $jobName = Get-YamlScalarValue -FilePath $ResolvedJobConfigPath -Key 'name'
    if ([string]::IsNullOrWhiteSpace($jobName)) {
        $jobName = Split-Path -Leaf $bundleRoot
    }

    $sourceConfigReference = Get-YamlScalarValue -FilePath $ResolvedJobConfigPath -Key 'sourceConfigPath'
    $targetConfigReference = Get-YamlScalarValue -FilePath $ResolvedJobConfigPath -Key 'targetConfigPath'

    $sourceConfigPath = if ($sourceConfigReference) { Resolve-NormalizedPath -Path $sourceConfigReference -BasePath $jobConfigDirectory } else { $null }
    $targetConfigPath = if ($targetConfigReference) { Resolve-NormalizedPath -Path $targetConfigReference -BasePath $jobConfigDirectory } else { $null }

    $packages = New-Object System.Collections.Generic.List[string]
    foreach ($packageName in @(Get-YamlPackageNames -FilePath $sourceConfigPath)) {
        if (-not $packages.Contains($packageName)) {
            $packages.Add($packageName) | Out-Null
        }
    }
    foreach ($packageName in @(Get-YamlPackageNames -FilePath $targetConfigPath)) {
        if (-not $packages.Contains($packageName)) {
            $packages.Add($packageName) | Out-Null
        }
    }

    $sourceEntryCount = Get-YamlEntryCount -FilePath $sourceConfigPath
    $targetEntryCount = Get-YamlEntryCount -FilePath $targetConfigPath
    $sourceExplicitPackageCount = @(Get-YamlPackageNames -FilePath $sourceConfigPath).Count
    $targetExplicitPackageCount = @(Get-YamlPackageNames -FilePath $targetConfigPath).Count

    $defaultPackages = Get-DefaultGeneratedPackages -JobName $jobName
    if ($sourceEntryCount -gt $sourceExplicitPackageCount -and -not $packages.Contains($defaultPackages[0])) {
        $packages.Add($defaultPackages[0]) | Out-Null
    }
    if ($targetEntryCount -gt $targetExplicitPackageCount -and -not $packages.Contains($defaultPackages[1])) {
        $packages.Add($defaultPackages[1]) | Out-Null
    }

    [pscustomobject]@{
        JobName = $jobName
        JobConfigPath = $ResolvedJobConfigPath
        BundleRoot = $bundleRoot
        BundleKind = Get-BundleKind -BundleRoot $bundleRoot -RepoRoot $RepoRoot
        SourceConfigPath = $sourceConfigPath
        TargetConfigPath = $targetConfigPath
        Packages = @($packages)
    }
}

function Get-RepoJobConfigPaths {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $searchRoots = @(
        (Join-Path $RepoRoot 'private-jobs'),
        (Join-Path $RepoRoot 'src\main\resources\config-jobs')
    )

    $files = New-Object System.Collections.Generic.List[string]
    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) {
            continue
        }
        foreach ($file in Get-ChildItem -Path $root -Recurse -Filter 'job-config.yaml' -File -ErrorAction SilentlyContinue) {
            $files.Add([System.IO.Path]::GetFullPath($file.FullName)) | Out-Null
        }
    }

    return $files.ToArray()
}

$resolvedRepoRoot = Resolve-NormalizedPath -Path $RepoRoot
$resolvedJobConfigPath = Resolve-NormalizedPath -Path $JobConfigPath -BasePath $resolvedRepoRoot
$currentJob = Get-JobDescriptor -ResolvedJobConfigPath $resolvedJobConfigPath -RepoRoot $resolvedRepoRoot

if ($currentJob.BundleKind -ne 'private' -and -not $DeleteSharedBundle) {
    throw "Bundle '$($currentJob.BundleRoot)' is classified as '$($currentJob.BundleKind)'. Re-run with -DeleteSharedBundle to allow deleting non-private bundles."
}

$packageUsage = @{}
foreach ($otherJobConfigPath in @(Get-RepoJobConfigPaths -RepoRoot $resolvedRepoRoot)) {
    if ($otherJobConfigPath.Equals($currentJob.JobConfigPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        continue
    }

    try {
        $otherDescriptor = Get-JobDescriptor -ResolvedJobConfigPath $otherJobConfigPath -RepoRoot $resolvedRepoRoot
    }
    catch {
        Write-Warning "Skipping package usage scan for '$otherJobConfigPath': $($_.Exception.Message)"
        continue
    }

    foreach ($packageName in $otherDescriptor.Packages) {
        if (-not $packageUsage.ContainsKey($packageName)) {
            $packageUsage[$packageName] = New-Object System.Collections.Generic.List[string]
        }
        $packageUsage[$packageName].Add($otherDescriptor.JobConfigPath) | Out-Null
    }
}

$packagesToDelete = New-Object System.Collections.Generic.List[string]
$skippedSharedPackages = @{}
foreach ($packageName in $currentJob.Packages) {
    if ($ForcePackageCleanup -or -not $packageUsage.ContainsKey($packageName)) {
        $packagesToDelete.Add($packageName) | Out-Null
        continue
    }
    $skippedSharedPackages[$packageName] = @($packageUsage[$packageName].ToArray())
}

$pathsToRemove = New-Object System.Collections.Generic.List[object]
$pathsToRemove.Add([pscustomobject]@{ Path = $currentJob.BundleRoot; Description = "$($currentJob.BundleKind) job bundle"; CleanupRoot = $null }) | Out-Null
foreach ($packageName in $packagesToDelete) {
    $packagePath = $packageName.Replace('.', '\')
    foreach ($basePath in @(
        (Join-Path $resolvedRepoRoot 'target\generated-sources\etl\source'),
        (Join-Path $resolvedRepoRoot 'target\generated-sources\etl\target'),
        (Join-Path $resolvedRepoRoot 'target\classes'),
        (Join-Path $resolvedRepoRoot 'target\test-classes')
    )) {
        $pathsToRemove.Add([pscustomobject]@{
            Path = Join-Path $basePath $packagePath
            Description = "generated package '$packageName'"
            CleanupRoot = $basePath
        }) | Out-Null
    }
}

$removedPaths = New-Object System.Collections.Generic.List[string]
$missingPaths = New-Object System.Collections.Generic.List[string]
$seenPaths = New-Object System.Collections.Generic.HashSet[string]([System.StringComparer]::OrdinalIgnoreCase)

foreach ($entry in $pathsToRemove) {
    if ($seenPaths.Add($entry.Path)) {
        Remove-PathIfPresent -Path $entry.Path -Description $entry.Description -RemovedPaths $removedPaths -MissingPaths $missingPaths -CleanupRoot $entry.CleanupRoot
    }
}

$result = [pscustomobject]@{
    JobName = $currentJob.JobName
    JobConfigPath = $currentJob.JobConfigPath
    BundleRoot = $currentJob.BundleRoot
    BundleKind = $currentJob.BundleKind
    Packages = @($currentJob.Packages)
    RemovedPackages = @($packagesToDelete)
    SkippedSharedPackages = $skippedSharedPackages
    RemovedPaths = @($removedPaths)
    MissingPaths = @($missingPaths)
}

Write-Host "Removed job bundle scope for '$($currentJob.JobName)'." -ForegroundColor Green
Write-Host "- Bundle root: $($currentJob.BundleRoot)"
Write-Host "- Bundle kind: $($currentJob.BundleKind)"
if ($currentJob.Packages.Count -gt 0) {
    Write-Host "- Job packages discovered: $($currentJob.Packages -join ', ')"
}
if ($packagesToDelete.Count -gt 0) {
    Write-Host "- Generated packages removed: $($packagesToDelete -join ', ')"
}
if ($skippedSharedPackages.Count -gt 0) {
    Write-Warning ("Skipped package cleanup for shared packages: " + (($skippedSharedPackages.Keys | Sort-Object) -join ', ') + ". Re-run with -ForcePackageCleanup if you want to remove them anyway.")
}
if ($removedPaths.Count -gt 0) {
    Write-Host "- Removed paths: $($removedPaths.Count)"
}
if ($missingPaths.Count -gt 0) {
    Write-Host "- Paths already absent: $($missingPaths.Count)"
}

if ($PassThru) {
    $result
}



