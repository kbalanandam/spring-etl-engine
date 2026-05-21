<#
.SYNOPSIS
Cross-platform-style helper for explicit job-config flows on Windows.

.DESCRIPTION
Runs one of:
  - prepare : generate/compile job-scoped model classes
  - run     : launch the selected job config
  - both    : prepare then run

Examples (repo root auto-resolved from this script location):
  # Prepare model classes for a new/changed job config
  powershell.exe -ExecutionPolicy Bypass -File .\scripts\job-runner.ps1 -Action prepare -JobConfigPath tmp-test-config/customer-load-reject-demo/job-config.yaml

  # Run that specific job config
  powershell.exe -ExecutionPolicy Bypass -File .\scripts\job-runner.ps1 -Action run -JobConfigPath tmp-test-config/customer-load-reject-demo/job-config.yaml

  # Do both in one call
  powershell.exe -ExecutionPolicy Bypass -File .\scripts\job-runner.ps1 -Action both -JobConfigPath tmp-test-config/customer-load-reject-demo/job-config.yaml
#>

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("prepare", "run", "both")]
    [string]$Action,

    [Parameter(Mandatory = $true)]
    [string]$JobConfigPath,

    [switch]$SkipTests = $true,

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
    # Keep root dynamic: this script works from any current shell location.
    $scriptDir = $PSScriptRoot
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function Resolve-JobConfigForMaven {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ConfiguredPath,
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    # Accept either absolute path, current-shell-relative path, or repo-root-relative path.
    $resolved = Resolve-Path $ConfiguredPath -ErrorAction SilentlyContinue
    if (-not $resolved) {
        $candidate = Join-Path $RepoRoot $ConfiguredPath
        $resolved = Resolve-Path $candidate -ErrorAction SilentlyContinue
    }
    if (-not $resolved) {
        throw "Job config path not found: '$ConfiguredPath'."
    }

    $absolute = $resolved.Path
    if ($absolute.StartsWith($RepoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $relative = $absolute.Substring($RepoRoot.Length).TrimStart([char[]]"\\/")
        return $relative -replace '\\', '/'
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

# Resolve dynamic repo root and normalize the job-config argument before invoking Maven.
$repoRoot = Resolve-RepoRoot
Set-Location $repoRoot

$jobConfigForMaven = Resolve-JobConfigForMaven -ConfiguredPath $JobConfigPath -RepoRoot $repoRoot
Write-Host "Repo root: $repoRoot"
Write-Host "Job config: $jobConfigForMaven"

if ($Action -eq "prepare" -or $Action -eq "both") {
    Invoke-Maven -DryRun:$DryRun -Arguments @(
        "--no-transfer-progress",
        "-Pxml-generation",
        "-Detl.xml.generation.jobConfig=$jobConfigForMaven",
        "process-classes"
    )
}

if ($Action -eq "run" -or $Action -eq "both") {
    $runArgs = @(
        "--no-transfer-progress"
    )

    if ($SkipTests) {
        $runArgs += "-DskipTests"
    }

    $runArgs += "-Dspring-boot.run.jvmArguments=-Detl.config.job=$jobConfigForMaven"
    $runArgs += "spring-boot:run"

    Invoke-Maven -DryRun:$DryRun -Arguments $runArgs
}




