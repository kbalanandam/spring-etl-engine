<#
.SYNOPSIS
Generate job-scoped XML model classes for all job bundles under one or more folder roots.

.DESCRIPTION
Scans folder roots for job-config.yaml files, optionally performs a one-time Maven clean,
then runs the xml-generation profile for each discovered job config.

Examples:
  # Preserved bundles only (default root)
  powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-models-batch.ps1

  # Include private bundles and continue when one job fails
  powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-models-batch.ps1 -IncludePrivateJobs -ContinueOnError

  # Preview only
  powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-models-batch.ps1 -DryRun
#>

param(
    [string[]]$RootPaths = @("src/main/resources/config-jobs"),
    [switch]$IncludePrivateJobs,
    [switch]$SkipClean,
    [switch]$ContinueOnError,
    [switch]$AllowDuplicateJobNames,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Resolve-PathForMaven {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PathValue,
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $resolved = Resolve-Path $PathValue -ErrorAction SilentlyContinue
    if (-not $resolved) {
        $candidate = Join-Path $RepoRoot $PathValue
        $resolved = Resolve-Path $candidate -ErrorAction SilentlyContinue
    }
    if (-not $resolved) {
        throw "Path not found: '$PathValue'."
    }

    $absolute = $resolved.Path
    if ($absolute.StartsWith($RepoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $absolute.Substring($RepoRoot.Length).TrimStart([char[]]"\\/") -replace '\\', '/'
    }

    return $absolute
}

function Invoke-Maven {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [switch]$DryRun
    )

    $display = "mvn " + ($Arguments -join " ")
    Write-Host "> $display"
    if ($DryRun) {
        return
    }

    & mvn @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven command failed with exit code $LASTEXITCODE."
    }
}

function Get-JobName {
    param([Parameter(Mandatory = $true)][string]$JobConfigFile)

    $line = Select-String -Path $JobConfigFile -Pattern '^\s*name\s*:\s*(.+)$' | Select-Object -First 1
    if (-not $line) {
        return ""
    }
    return [string]$line.Matches[0].Groups[1].Value.Trim()
}

$repoRoot = Resolve-RepoRoot
Set-Location $repoRoot

$scanRoots = [System.Collections.Generic.List[string]]::new()
foreach ($root in $RootPaths) {
    if ([string]::IsNullOrWhiteSpace($root)) {
        continue
    }
    $scanRoots.Add((Resolve-PathForMaven -PathValue $root -RepoRoot $repoRoot))
}
if ($IncludePrivateJobs) {
    $scanRoots.Add((Resolve-PathForMaven -PathValue "private-jobs" -RepoRoot $repoRoot))
}

$scanRoots = @($scanRoots | Sort-Object -Unique)
if ($scanRoots.Count -eq 0) {
    throw "No scan roots were resolved."
}

Write-Host "Repo root: $repoRoot"
Write-Host "Scanning roots:"
$scanRoots | ForEach-Object { Write-Host " - $_" }

$jobConfigFiles = @()
foreach ($scanRoot in $scanRoots) {
    $absoluteRoot = Resolve-PathForMaven -PathValue $scanRoot -RepoRoot $repoRoot
    $rootAbsolutePath = if ($absoluteRoot -match '^[A-Za-z]:/') { $absoluteRoot -replace '/', '\\' } else { Join-Path $repoRoot ($absoluteRoot -replace '/', '\\') }
    if (-not (Test-Path $rootAbsolutePath)) {
        continue
    }

    $jobConfigFiles += Get-ChildItem -Path $rootAbsolutePath -Recurse -File -Filter "job-config.yaml" | Select-Object -ExpandProperty FullName
}

$jobConfigFiles = @($jobConfigFiles | Sort-Object -Unique)
if ($jobConfigFiles.Count -eq 0) {
    throw "No job-config.yaml files were found under the selected roots."
}

$jobs = @()
foreach ($jobConfigFile in $jobConfigFiles) {
    $mavenPath = Resolve-PathForMaven -PathValue $jobConfigFile -RepoRoot $repoRoot
    $jobName = Get-JobName -JobConfigFile $jobConfigFile
    $jobs += [pscustomobject]@{
        JobConfigAbsolutePath = $jobConfigFile
        JobConfigMavenPath = $mavenPath
        JobName = $jobName
        JobNameKey = $jobName.ToLowerInvariant()
    }
}

$duplicateGroups = $jobs |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_.JobNameKey) } |
    Group-Object -Property JobNameKey |
    Where-Object { $_.Count -gt 1 }

if ($duplicateGroups.Count -gt 0) {
    Write-Warning "Duplicate job names detected. These may map to the same generated package path."
    foreach ($group in $duplicateGroups) {
        Write-Warning " - job name '$($group.Name)' appears in:"
        $group.Group | ForEach-Object { Write-Warning "   * $($_.JobConfigMavenPath)" }
    }
    if (-not $AllowDuplicateJobNames) {
        throw "Duplicate job names detected. Rerun with -AllowDuplicateJobNames to continue intentionally."
    }
}

Write-Host "Found $($jobs.Count) job config(s)."

if (-not $SkipClean) {
    Invoke-Maven -DryRun:$DryRun -Arguments @(
        "--no-transfer-progress",
        "clean",
        "resources:resources"
    )
}

$successes = [System.Collections.Generic.List[string]]::new()
$failures = [System.Collections.Generic.List[string]]::new()

foreach ($job in $jobs) {
    Write-Host "Generating models for: $($job.JobConfigMavenPath)"
    try {
        Invoke-Maven -DryRun:$DryRun -Arguments @(
            "--no-transfer-progress",
            "-Pxml-generation",
            "-Detl.xml.generation.jobConfig=$($job.JobConfigMavenPath)",
            "process-classes"
        )
        $successes.Add($job.JobConfigMavenPath)
    } catch {
        $message = "$($job.JobConfigMavenPath) :: $($_.Exception.Message)"
        $failures.Add($message)
        Write-Error $message
        if (-not $ContinueOnError) {
            throw
        }
    }
}

Write-Host ""
Write-Host "Generation summary"
Write-Host " - Success: $($successes.Count)"
Write-Host " - Failed : $($failures.Count)"

if ($failures.Count -gt 0) {
    Write-Host "Failed items:"
    $failures | ForEach-Object { Write-Host " - $_" }
    throw "Batch model generation completed with failures."
}

Write-Host "Batch model generation completed successfully."

