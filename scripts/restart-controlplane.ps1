param(
    [ValidateSet("Restart", "Start", "Stop", "Status")]
    [string]$Action = "Restart",
    [int]$Port = 8081,
    [string]$Profile = "controlplane",
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
        [switch]$DisableClean
    )

    $mvnArgs = @("--no-transfer-progress")
    if (-not $DisableClean) {
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
    Remove-Item -ErrorAction SilentlyContinue $stdoutPath, $stderrPath

    $joinedArgs = ($mvnArgs | ForEach-Object { '"' + $_ + '"' }) -join " "
    $cmdLine = "mvn $joinedArgs"

    Write-Host "Starting control-plane from $WorkingDirectory ..."
    $process = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $cmdLine) -WorkingDirectory $WorkingDirectory -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru
    Write-Host "Started Maven PID $($process.Id)."

    return @{
        Process = $process
        StdoutPath = $stdoutPath
        StderrPath = $stderrPath
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
        $startInfo = Start-ControlPlane -WorkingDirectory $repoRoot -ActiveProfile $Profile -DisableClean:$NoClean
        if (-not $SkipHealthCheck) {
            $health = Wait-ForHealth -Url $systemInfoUrl -TimeoutSec $StartupTimeoutSec -StartupProcess $startInfo.Process -StdoutPath $startInfo.StdoutPath -StderrPath $startInfo.StderrPath
            Write-Host "Healthy profile=$($health.profile) schedulerEnabled=$($health.schedulerEnabled)"
        }
    }
    "Restart" {
        Stop-ControlPlane -TargetPort $Port
        $startInfo = Start-ControlPlane -WorkingDirectory $repoRoot -ActiveProfile $Profile -DisableClean:$NoClean
        if (-not $SkipHealthCheck) {
            $health = Wait-ForHealth -Url $systemInfoUrl -TimeoutSec $StartupTimeoutSec -StartupProcess $startInfo.Process -StdoutPath $startInfo.StdoutPath -StderrPath $startInfo.StderrPath
            Write-Host "Healthy profile=$($health.profile) schedulerEnabled=$($health.schedulerEnabled)"
        }
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






