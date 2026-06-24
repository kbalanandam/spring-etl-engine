<#
    Runs a small end-to-end smoke verification after code changes.

    What it verifies:
    1. A known-good explicit scenario (`customer-load`) still runs successfully.
    2. A known-bad preserved scenario (`csv-to-sqlserver`) fails for the
       right operational reason and emits failure evidence in logs.

    Main artifacts:
    - target/verify-customer-load.log
    - target/verify-csv-to-sqlserver.log
    - src/main/resources/config-jobs/customer-load/output/customers.xml

    Important behavior:
    - The second scenario is expected to fail.
    - If both the positive and negative checks behave as expected, the script exits
      with code 0 so higher-level automation can treat the overall smoke verification
      as successful.
#>
param(
    [string]$RepoRoot,
    [ValidateRange(1, 720)]
    [int]$ScenarioTimeoutMinutes = 20
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

function Stop-ProcessTree {
    param(
        [int]$RootProcessId
    )

    if ($RootProcessId -le 0) {
        return
    }

    $childProcessIds = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$RootProcessId" -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty ProcessId)
    foreach ($childId in $childProcessIds) {
        Stop-ProcessTree -RootProcessId $childId
    }

    $rootProcess = Get-Process -Id $RootProcessId -ErrorAction SilentlyContinue
    if ($rootProcess) {
        Stop-Process -Id $RootProcessId -Force -ErrorAction SilentlyContinue
    }
}

# Executes one Maven command with timeout enforcement and captures both stdout/stderr.
function Invoke-MavenWithTimeout {
    param(
        [string]$CaptureFile,
        [string[]]$Arguments,
        [int]$TimeoutMinutes,
        [string]$OperationLabel,
        [switch]$AppendOutput
    )

    $stdoutFile = [System.IO.Path]::GetTempFileName()
    $stderrFile = [System.IO.Path]::GetTempFileName()

    try {
        if ($env:ETL_VERIFY_RECENT_CHANGES_TEST_FORCE_TIMEOUT -eq '1') {
            $captureDir = Split-Path -Path $CaptureFile -Parent
            if (-not [string]::IsNullOrWhiteSpace($captureDir) -and -not (Test-Path $captureDir)) {
                New-Item -ItemType Directory -Path $captureDir -Force | Out-Null
            }
            Add-Content -Path $CaptureFile -Value "TIMED_OUT: $OperationLabel forced timeout for test coverage."
            throw "TIMED_OUT: $OperationLabel forced timeout for test coverage. See $CaptureFile"
        }

        # Route Maven through cmd.exe so ExitCode is consistently available on Windows.
        $commandArgs = @('/d', '/c', 'mvn') + $Arguments
        $process = Start-Process -FilePath 'cmd.exe' -ArgumentList $commandArgs -PassThru -NoNewWindow -RedirectStandardOutput $stdoutFile -RedirectStandardError $stderrFile
        $timedOut = -not $process.WaitForExit($TimeoutMinutes * 60 * 1000)

        if ($timedOut) {
            Stop-ProcessTree -RootProcessId $process.Id
        }

        if (-not $AppendOutput.IsPresent -and (Test-Path $CaptureFile)) {
            Remove-Item $CaptureFile -Force
        }

        if (Test-Path $stdoutFile) {
            Get-Content -Path $stdoutFile | Out-File -FilePath $CaptureFile -Encoding utf8 -Append
        }
        if (Test-Path $stderrFile) {
            Get-Content -Path $stderrFile | Out-File -FilePath $CaptureFile -Encoding utf8 -Append
        }

        if ($timedOut) {
            Add-Content -Path $CaptureFile -Value "TIMED_OUT: $OperationLabel exceeded timeout of $TimeoutMinutes minute(s)."
            throw "TIMED_OUT: $OperationLabel exceeded timeout of $TimeoutMinutes minute(s). See $CaptureFile"
        }

        $exitCode = $process.ExitCode
        if ($null -eq $exitCode) {
            $capturedText = if (Test-Path $CaptureFile) { Get-Content -Path $CaptureFile -Raw } else { '' }
            if ($capturedText -match 'BUILD SUCCESS' -and $capturedText -notmatch 'BUILD FAILURE') {
                $exitCode = 0
            } elseif ($capturedText -match 'BUILD FAILURE') {
                $exitCode = 1
            } else {
                throw "$OperationLabel did not produce a usable process exit code or recognizable Maven build marker. See $CaptureFile"
            }
        }

        return $exitCode
    }
    finally {
        Remove-Item $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
    }
}

# Builds selected scenario-scoped generated classes for one explicit job config.
function Invoke-ScenarioGeneration {
    param(
        [string]$ScenarioName,
        [string]$JobConfigPath,
        [string]$CaptureFile
    )

    $generationArgs = @(
        '--no-transfer-progress'
        '-Pxml-generation'
        "-Detl.xml.generation.jobConfig=$JobConfigPath"
        'process-classes'
    )

    $exitCode = Invoke-MavenWithTimeout -CaptureFile $CaptureFile -Arguments $generationArgs -TimeoutMinutes $ScenarioTimeoutMinutes -OperationLabel "$ScenarioName model generation"
    if ($exitCode -ne 0) {
        throw "$ScenarioName model generation failed unexpectedly. See $CaptureFile"
    }
}

# Runs one Spring Boot scenario, captures its Maven console output, and enforces
# whether that scenario is expected to succeed or fail.
function Invoke-MavenScenario {
    param(
        [string]$ScenarioName,
        [string]$JobConfigPath,
        [string]$CaptureFile,
        [bool]$ExpectSuccess,
        [bool]$AllowZeroExitOnExpectedFailure = $false,
        [string]$AdditionalJvmArguments = ''
    )

    $jvmArgs = "-Detl.config.job=$JobConfigPath"
    if (-not [string]::IsNullOrWhiteSpace($AdditionalJvmArguments)) {
        $jvmArgs = "$jvmArgs $AdditionalJvmArguments"
    }

    Push-Location $RepoRoot
    try {
        if (Test-Path $CaptureFile) {
            Remove-Item $CaptureFile -Force
        }

        Invoke-ScenarioGeneration -ScenarioName $ScenarioName -JobConfigPath $JobConfigPath -CaptureFile $CaptureFile

        # Capture the full Maven/Spring Boot console transcript so later checks can
        # validate specific proof points from logs, not just process exit codes.
        $runArgs = @(
            '--no-transfer-progress'
            '-DskipTests'
            '-Dspring-boot.run.mainClass=com.etl.ETLEngineApplication'
            '-Dspring-boot.run.main-class=com.etl.ETLEngineApplication'
            "-Dspring-boot.run.jvmArguments=$jvmArgs"
            'spring-boot:run'
        )
        $exitCode = Invoke-MavenWithTimeout -CaptureFile $CaptureFile -Arguments $runArgs -TimeoutMinutes $ScenarioTimeoutMinutes -OperationLabel "$ScenarioName spring-boot:run" -AppendOutput
    }
    finally {
        Pop-Location
    }

    if ($ExpectSuccess -and $exitCode -ne 0) {
        throw "$ScenarioName run failed unexpectedly. See $CaptureFile"
    }

    if (-not $ExpectSuccess -and -not $AllowZeroExitOnExpectedFailure -and $exitCode -eq 0) {
        throw "$ScenarioName run succeeded unexpectedly. See $CaptureFile"
    }

    return $exitCode
}

# Validates that a log or output file contains an expected proof point.
# This keeps the smoke check focused on observable behavior.
function Assert-FileContains {
    param(
        [string]$Path,
        [string]$ExpectedText,
        [string]$Message
    )

    if (-not (Test-Path $Path)) {
        throw "$Message File not found: $Path"
    }

    $content = Get-Content -Path $Path -Raw
    if ($content -notmatch [regex]::Escape($ExpectedText)) {
        throw "$Message Expected to find '$ExpectedText' in $Path"
    }
}

function Assert-FileContainsAll {
    param(
        [string]$Path,
        [string[]]$ExpectedTexts,
        [string]$Message
    )

    if (-not (Test-Path $Path)) {
        throw "$Message File not found: $Path"
    }

    $content = Get-Content -Path $Path -Raw
    foreach ($expectedText in $ExpectedTexts) {
        if ($content -notmatch [regex]::Escape($expectedText)) {
            throw "$Message Expected to find '$expectedText' in $Path"
        }
    }
}

$positiveCapture = Join-Path $RepoRoot 'target\verify-customer-load.log'
$negativeCapture = Join-Path $RepoRoot 'target\verify-csv-to-sqlserver.log'
$customerOutputRoot = Join-Path $RepoRoot 'src\main\resources\config-jobs\customer-load\output'
$customerOutput = Join-Path $RepoRoot 'src\main\resources\config-jobs\customer-load\output\customers.xml'
$smokeDbDir = Join-Path $RepoRoot 'target\verify-smoke'
$smokeDbFile = Join-Path $smokeDbDir 'etl-dev-smoke.db'
$smokeDbJdbcPath = ([System.IO.Path]::GetFullPath($smokeDbFile)).Replace('\\', '/')
$smokeDbJdbcUrl = "jdbc:sqlite:$smokeDbJdbcPath"
$smokeDbJvmArg = "-Dspring.datasource.url=$smokeDbJdbcUrl"
$previousSpringDatasourceUrl = $env:SPRING_DATASOURCE_URL

try {
    $env:SPRING_DATASOURCE_URL = $smokeDbJdbcUrl

    if (-not (Test-Path $smokeDbDir)) {
        New-Item -ItemType Directory -Path $smokeDbDir | Out-Null
    }

    # Keep smoke runs deterministic by cleaning only the isolated smoke metadata DB.
    @($smokeDbFile, "$smokeDbFile-wal", "$smokeDbFile-shm", "$smokeDbFile-journal") |
        ForEach-Object {
            if (Test-Path $_) {
                Remove-Item $_ -Force -ErrorAction SilentlyContinue
            }
        }

    Write-Host "[1/2] Verifying positive smoke run: customer-load"
    if (Test-Path (Join-Path $RepoRoot 'targetcustomers.xml')) {
        Remove-Item (Join-Path $RepoRoot 'targetcustomers.xml') -Force
    }
    if (Test-Path (Join-Path $RepoRoot 'target\customers.xml')) {
        Remove-Item (Join-Path $RepoRoot 'target\customers.xml') -Force
    }
    Get-ChildItem (Join-Path $RepoRoot 'target\classes\config-jobs') -Recurse -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -eq 'output' } |
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    if (Test-Path $customerOutputRoot) {
        Remove-Item $customerOutputRoot -Recurse -Force
    }

    # Positive smoke: prove that one explicit scenario still runs end-to-end,
    # emits the expected run/step events, and writes its target output.
    Invoke-MavenScenario -ScenarioName 'customer-load' -JobConfigPath 'src/main/resources/config-jobs/customer-load/job-config.yaml' -CaptureFile $positiveCapture -ExpectSuccess $true -AdditionalJvmArguments $smokeDbJvmArg | Out-Null
    Assert-FileContains -Path $positiveCapture -ExpectedText 'RUN_SUMMARY event=run_summary scenario=customer-load' -Message 'customer-load did not emit expected run summary.'
    Assert-FileContains -Path $positiveCapture -ExpectedText 'status=COMPLETED' -Message 'customer-load did not complete successfully.'
    Assert-FileContainsAll -Path $positiveCapture -ExpectedTexts @('STEP_EVENT event=step_finished', 'stepName=customers-step') -Message 'customer-load did not finish the explicit step.'
    Assert-FileContains -Path $customerOutput -ExpectedText '<Customers>' -Message 'customer-load did not produce expected XML output.'

    Write-Host "[2/2] Verifying negative smoke run: csv-to-sqlserver operational failure evidence"
    # Negative smoke: prove that the preserved SQL Server scenario still fails and
    # emits explicit failure evidence for operators.
    Invoke-MavenScenario -ScenarioName 'csv-to-sqlserver' -JobConfigPath 'src/main/resources/config-jobs/csv-to-sqlserver/job-config.yaml' -CaptureFile $negativeCapture -ExpectSuccess $false -AllowZeroExitOnExpectedFailure $true -AdditionalJvmArguments $smokeDbJvmArg | Out-Null
    Assert-FileContains -Path $negativeCapture -ExpectedText 'RUN_SUMMARY event=run_summary scenario=csv-to-sqlserver' -Message 'csv-to-sqlserver did not emit run summary evidence.'
    Assert-FileContains -Path $negativeCapture -ExpectedText 'status=FAILED' -Message 'csv-to-sqlserver did not finish with FAILED status as expected.'
    Assert-FileContains -Path $negativeCapture -ExpectedText 'JOB_FAILURE event=job_failure scenario=csv-to-sqlserver' -Message 'csv-to-sqlserver did not emit categorized job-failure evidence.'
    Assert-FileContains -Path $negativeCapture -ExpectedText 'CannotGetJdbcConnectionException' -Message 'csv-to-sqlserver did not report the expected SQL connectivity failure category.'

    Write-Host ''
    Write-Host 'Verification PASSED' -ForegroundColor Green
    Write-Host "- Positive run log: $positiveCapture"
    Write-Host "- Negative run log: $negativeCapture"
    Write-Host "- Positive output: $customerOutput"

    # Reset the process exit code to success because the negative scenario already failed
    # in the expected way and all smoke assertions passed.
    $global:LASTEXITCODE = 0
    exit 0
}
catch {
    $message = $_.Exception.Message
    if ($message -like 'TIMED_OUT:*') {
        [Console]::Error.WriteLine($message)
        $global:LASTEXITCODE = 124
        exit 124
    }

    throw
}
finally {
    if ($null -eq $previousSpringDatasourceUrl) {
        Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
    }
    else {
        $env:SPRING_DATASOURCE_URL = $previousSpringDatasourceUrl
    }
}
