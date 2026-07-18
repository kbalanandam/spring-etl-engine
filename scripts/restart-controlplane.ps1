param(
    [ValidateSet("Restart", "Start", "Stop", "Status")]
    [string]$Action = "Restart",
    [int]$Port = 8081,
    [string]$Profile = "controlplane",
    [ValidateSet("Preserve", "Clean")]
    [string]$CleanMode,
    [switch]$Clean,
    [switch]$NoClean,
    [int]$StartupTimeoutSec = 90,
    [switch]$SkipHealthCheck
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$systemInfoUrl = "http://localhost:$Port/api/v1/system/info"

function Get-PortPids {
    param([int]$TargetPort)

    $lines = netstat -ano | Select-String ":$TargetPort"
    $pids = @()
    foreach ($line in $lines) {
        if ($line.ToString() -notmatch "LISTENING") {
            continue
        }
        $parts = ($line.ToString().Trim() -split "\s+")
        if ($parts.Length -ge 5) {
            $candidate = $parts[-1]
            if ($candidate -match "^\d+$" -and $candidate -ne "0") {
                $pids += [int]$candidate
            }
        }
    }

    return @($pids | Sort-Object -Unique)
}

function Stop-ControlPlane {
    param([int]$TargetPort)

    $pids = @(Get-PortPids -TargetPort $TargetPort)
    if ($pids.Count -eq 0) {
        Write-Host "No process is listening on port $TargetPort."
        return
    }

    foreach ($procId in $pids) {
        Write-Host "Stopping PID $procId on port $TargetPort..."
        taskkill /PID $procId /F | Out-Null
    }

    Write-Host "Stopped PID(s): $($pids -join ', ')"
}

function Start-ControlPlane {
    param(
        [string]$WorkingDirectory,
        [string]$ActiveProfile,
        [switch]$EnableClean
    )

    $mvnArgs = @("--no-transfer-progress")
    if ($EnableClean) {
        $mvnArgs += @("clean", "resources:resources")
    }
    $mvnArgs += @(
        "-Dspring-boot.run.main-class=com.etl.controlplane.ControlPlaneApiApplication",
        "-Dspring-boot.run.arguments=--spring.profiles.active=$ActiveProfile",
        "spring-boot:run"
    )

    $logDir = Join-Path $WorkingDirectory "logs\startup"
    if (-not (Test-Path $logDir)) {
        New-Item -Path $logDir -ItemType Directory | Out-Null
    }

    $stdoutPath = Join-Path $logDir "restart-controlplane.stdout.log"
    $stderrPath = Join-Path $logDir "restart-controlplane.stderr.log"
    $startupAppLogPath = Join-Path $logDir "startup.log"
    Remove-Item -ErrorAction SilentlyContinue $stdoutPath, $stderrPath
    # Keep startup logs bounded to the current session for easier troubleshooting.
    Remove-Item -ErrorAction SilentlyContinue $startupAppLogPath

    $joinedArgs = ($mvnArgs | ForEach-Object { '"' + $_ + '"' }) -join " "
    $cmdLine = "mvn $joinedArgs"

    Write-Host "Starting control-plane from $WorkingDirectory ..."
    if ($EnableClean) {
        Write-Host "Startup mode: CLEAN (target/ will be rebuilt; generated model classes must be regenerated afterward)."
    } else {
        Write-Host "Startup mode: PRESERVE (skips clean to keep generated model classes under target/classes)."
    }
    $process = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $cmdLine) -WorkingDirectory $WorkingDirectory -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
    Write-Host "Started Maven PID $($process.Id)."

    return @{
        Process = $process
        StdoutPath = $stdoutPath
        StderrPath = $stderrPath
    }
}

function Resolve-CleanMode {
    param(
        [string]$RequestedCleanMode,
        [switch]$RequestedClean,
        [switch]$RequestedNoClean,
        [hashtable]$BoundParameters
    )

    if ($BoundParameters.ContainsKey("CleanMode")) {
        if ($BoundParameters.ContainsKey("Clean") -or $BoundParameters.ContainsKey("NoClean")) {
            throw "Use either -CleanMode or the legacy -Clean/-NoClean switches, not both."
        }
        return $RequestedCleanMode -eq "Clean"
    }

    if ($BoundParameters.ContainsKey("Clean") -and $BoundParameters.ContainsKey("NoClean")) {
        throw "Use either -Clean or -NoClean, not both."
    }

    if ($BoundParameters.ContainsKey("Clean")) {
        return $true
    }
    if ($BoundParameters.ContainsKey("NoClean")) {
        return $false
    }

    # Default to preserve generated model classes so explicit-job runs remain launch-ready.
    return $false
}

function Invoke-ControlPlaneStartFlow {
    param(
        [string]$WorkingDirectory,
        [string]$ActiveProfile,
        [bool]$EnableClean,
        [bool]$PerformStop,
        [int]$TargetPort,
        [bool]$ShouldSkipHealthCheck,
        [string]$HealthUrl,
        [int]$TimeoutSec
    )

    if ($PerformStop) {
        Stop-ControlPlane -TargetPort $TargetPort
    }

    $startInfo = Start-ControlPlane -WorkingDirectory $WorkingDirectory -ActiveProfile $ActiveProfile -EnableClean:$EnableClean
    if (-not $ShouldSkipHealthCheck) {
        $health = Wait-ForHealth -Url $HealthUrl -TimeoutSec $TimeoutSec -StartupProcess $startInfo.Process -StdoutPath $startInfo.StdoutPath -StderrPath $startInfo.StderrPath
        Write-Host "Healthy profile=$($health.profile) schedulerEnabled=$($health.schedulerEnabled)"
    }
}

function Wait-ForHealth {
    param(
        [string]$Url,
        [int]$TimeoutSec,
        [System.Diagnostics.Process]$StartupProcess,
        [string]$StdoutPath,
        [string]$StderrPath
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        if ($null -ne $StartupProcess -and $StartupProcess.HasExited) {
            $StartupProcess.WaitForExit()
            $exitCode = $StartupProcess.ExitCode
            $stderrTail = ""
            if (Test-Path $StderrPath) {
                $stderrTail = (Get-Content -Tail 30 $StderrPath) -join [Environment]::NewLine
            }
            $stdoutTail = ""
            if (Test-Path $StdoutPath) {
                $stdoutTail = (Get-Content -Tail 30 $StdoutPath) -join [Environment]::NewLine
            }
            throw "Control-plane startup process exited (code=$exitCode). See logs: $StdoutPath ; $StderrPath`nSTDERR:`n$stderrTail`nSTDOUT:`n$stdoutTail"
        }
        try {
            $response = Invoke-RestMethod -TimeoutSec 5 -Uri $Url
            if ($null -ne $response) {
                return $response
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    throw "Control-plane did not become healthy within $TimeoutSec seconds."
}

switch ($Action) {
    "Stop" {
        Stop-ControlPlane -TargetPort $Port
    }
    "Start" {
        $cleanModeValue = Resolve-CleanMode -RequestedCleanMode $CleanMode -RequestedClean:$Clean -RequestedNoClean:$NoClean -BoundParameters $PSBoundParameters
        Invoke-ControlPlaneStartFlow -WorkingDirectory $repoRoot -ActiveProfile $Profile -EnableClean:$cleanModeValue -PerformStop:$false -TargetPort $Port -ShouldSkipHealthCheck:$SkipHealthCheck -HealthUrl $systemInfoUrl -TimeoutSec $StartupTimeoutSec
    }
    "Restart" {
        $cleanModeValue = Resolve-CleanMode -RequestedCleanMode $CleanMode -RequestedClean:$Clean -RequestedNoClean:$NoClean -BoundParameters $PSBoundParameters
        Invoke-ControlPlaneStartFlow -WorkingDirectory $repoRoot -ActiveProfile $Profile -EnableClean:$cleanModeValue -PerformStop:$true -TargetPort $Port -ShouldSkipHealthCheck:$SkipHealthCheck -HealthUrl $systemInfoUrl -TimeoutSec $StartupTimeoutSec
    }
    "Status" {
        $pids = @(Get-PortPids -TargetPort $Port)
        if ($pids.Count -eq 0) {
            Write-Host "No process is listening on port $Port."
            return
        }

        Write-Host "Port $Port listener PID(s): $($pids -join ', ')"
        try {
            $health = Invoke-RestMethod -TimeoutSec 5 -Uri $systemInfoUrl
            Write-Host "Healthy profile=$($health.profile) schedulerEnabled=$($health.schedulerEnabled)"
        } catch {
            Write-Warning "Port is in use, but system-info endpoint is not reachable: $($_.Exception.Message)"
        }
    }
}






