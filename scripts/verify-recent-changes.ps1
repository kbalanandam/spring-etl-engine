<#
    Runs a small end-to-end smoke verification after code changes.

    What it verifies:
    1. A known-good explicit scenario (`customer-load`) still runs successfully.
    2. A known-bad preserved scenario (`csv-to-sqlserver`) fails for the
       right operational reason and emits failure evidence in logs.
    3. Trigger-now controller paths emit structured `CONTROLPLANE_TRIGGER`
       evidence logs for requested/accepted/duplicate decisions.

    Main artifacts:
    - target/verify-customer-load.log
    - target/verify-csv-to-sqlserver.log
    - target/verify-trigger-now.log
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
            ('"-Dspring-boot.run.jvmArguments=' + $jvmArgs + '"')
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

function Invoke-H2Bootstrap {
    param(
        [string]$JdbcUrl,
        [string]$ScriptPath,
        [string]$CaptureFile,
        [string]$OperationLabel
    )

    if (-not (Test-Path $ScriptPath)) {
        throw "$OperationLabel bootstrap script not found. See $ScriptPath"
    }

    $h2Jar = Get-ChildItem (Join-Path $env:USERPROFILE '.m2\repository\com\h2database\h2') -Recurse -Filter 'h2-*.jar' |
        Where-Object { $_.Name -notmatch '-sources\.jar$' -and $_.Name -notmatch '-javadoc\.jar$' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $h2Jar) {
        throw "$OperationLabel could not locate the H2 JDBC jar in the local Maven repository."
    }

    $javaExecutable = if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $null
    }
    else {
        Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    if ([string]::IsNullOrWhiteSpace($javaExecutable) -or -not (Test-Path $javaExecutable)) {
        $javaCommand = Get-Command java -ErrorAction SilentlyContinue
        if (-not $javaCommand) {
            throw "$OperationLabel could not locate a Java runtime for H2 bootstrap."
        }
        $javaExecutable = $javaCommand.Source
    }

    $bootstrapArgs = @(
        '-cp'
        $h2Jar.FullName
        'org.h2.tools.RunScript'
        '-url'
        $JdbcUrl
        '-user'
        'sa'
        '-script'
        $ScriptPath
    )

    & $javaExecutable @bootstrapArgs 2>&1 | Out-File -FilePath $CaptureFile -Encoding utf8 -Append
    if ($LASTEXITCODE -ne 0) {
        throw "$OperationLabel bootstrap failed unexpectedly. See $CaptureFile"
    }
}

$positiveCapture = Join-Path $RepoRoot 'target\verify-customer-load.log'
$negativeCapture = Join-Path $RepoRoot 'target\verify-csv-to-sqlserver.log'
$triggerCapture = Join-Path $RepoRoot 'target\verify-trigger-now.log'
$customerOutputRoot = Join-Path $RepoRoot 'src\main\resources\config-jobs\customer-load\output'
$customerOutput = Join-Path $RepoRoot 'src\main\resources\config-jobs\customer-load\output\customers.xml'
$smokeDbDir = Join-Path $RepoRoot 'target\verify-smoke'
$customerSmokeDbFile = Join-Path $smokeDbDir 'etl-dev-smoke-customer'
$negativeSmokeDbFile = Join-Path $smokeDbDir 'etl-dev-smoke-csv-to-sqlserver'
$batchMetadataScriptPath = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot 'scripts\sql\h2\spring-batch-metadata.sql'))
function New-SmokeDbJdbcUrl {
    param(
        [string]$DbFilePath
    )

    $resolvedDbPath = ([System.IO.Path]::GetFullPath($DbFilePath)).Replace('\\', '/')
    return "jdbc:h2:file:$resolvedDbPath;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
}

$customerSmokeDbJdbcUrl = New-SmokeDbJdbcUrl -DbFilePath $customerSmokeDbFile
$negativeSmokeDbJdbcUrl = New-SmokeDbJdbcUrl -DbFilePath $negativeSmokeDbFile
$customerSmokeJvmArgs = @(
    "-Dspring.datasource.url=$customerSmokeDbJdbcUrl"
    '-Dspring.datasource.driver-class-name=org.h2.Driver'
    '-Dspring.datasource.username=sa'
    '-Dspring.datasource.password='
) -join ' '
$negativeSmokeJvmArgs = @(
    "-Dspring.datasource.url=$negativeSmokeDbJdbcUrl"
    '-Dspring.datasource.driver-class-name=org.h2.Driver'
    '-Dspring.datasource.username=sa'
    '-Dspring.datasource.password='
    '-Detl.config.relational.connections.sqlserver-main.username=<sqlserver-username>'
    '-Detl.config.relational.connections.sqlserver-main.password=<sqlserver-password>'
) -join ' '
$previousSpringDatasourceUrl = $env:SPRING_DATASOURCE_URL
$previousSpringDatasourceDriverClassName = $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME
$previousSpringDatasourceUsername = $env:SPRING_DATASOURCE_USERNAME
$previousSpringDatasourcePassword = $env:SPRING_DATASOURCE_PASSWORD
$previousSpringBatchInitializeSchema = $env:SPRING_BATCH_JDBC_INITIALIZE_SCHEMA
$previousSpringProfilesActive = $env:SPRING_PROFILES_ACTIVE

try {
    if ($env:ETL_VERIFY_RECENT_CHANGES_TEST_FORCE_TIMEOUT -eq '1') {
        $captureDir = Split-Path -Path $positiveCapture -Parent
        if (-not [string]::IsNullOrWhiteSpace($captureDir) -and -not (Test-Path $captureDir)) {
            New-Item -ItemType Directory -Path $captureDir -Force | Out-Null
        }
        Add-Content -Path $positiveCapture -Value 'TIMED_OUT: smoke verification forced timeout for test coverage.'
        throw "TIMED_OUT: smoke verification forced timeout for test coverage. See $positiveCapture"
    }

    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $env:SPRING_DATASOURCE_URL = $customerSmokeDbJdbcUrl
    $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.h2.Driver'
    $env:SPRING_DATASOURCE_USERNAME = 'sa'
    $env:SPRING_DATASOURCE_PASSWORD = ''
    $env:SPRING_BATCH_JDBC_INITIALIZE_SCHEMA = 'always'

    if (-not (Test-Path $smokeDbDir)) {
        New-Item -ItemType Directory -Path $smokeDbDir | Out-Null
    }

    # Keep smoke runs deterministic by cleaning only the isolated smoke metadata DB.
    @(
        "$customerSmokeDbFile.mv.db",
        "$customerSmokeDbFile.trace.db",
        "$negativeSmokeDbFile.mv.db",
        "$negativeSmokeDbFile.trace.db"
    ) |
        ForEach-Object {
            if (Test-Path $_) {
                Remove-Item $_ -Force -ErrorAction SilentlyContinue
            }
        }

    @($positiveCapture, $negativeCapture) | ForEach-Object {
        if (Test-Path $_) {
            Remove-Item $_ -Force -ErrorAction SilentlyContinue
        }
    }

    Invoke-H2Bootstrap -JdbcUrl $customerSmokeDbJdbcUrl -ScriptPath $batchMetadataScriptPath -CaptureFile $positiveCapture -OperationLabel 'customer-load'
    Invoke-H2Bootstrap -JdbcUrl $negativeSmokeDbJdbcUrl -ScriptPath $batchMetadataScriptPath -CaptureFile $negativeCapture -OperationLabel 'csv-to-sqlserver'

    Write-Host "[1/3] Verifying positive smoke run: customer-load"
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
    Invoke-MavenScenario -ScenarioName 'customer-load' -JobConfigPath 'src/main/resources/config-jobs/customer-load/job-config.yaml' -CaptureFile $positiveCapture -ExpectSuccess $true -AdditionalJvmArguments $customerSmokeJvmArgs | Out-Null
    Assert-FileContains -Path $positiveCapture -ExpectedText 'RUN_SUMMARY event=run_summary scenario=customer-load' -Message 'customer-load did not emit expected run summary.'
    Assert-FileContains -Path $positiveCapture -ExpectedText 'status=COMPLETED' -Message 'customer-load did not complete successfully.'
    Assert-FileContainsAll -Path $positiveCapture -ExpectedTexts @('STEP_EVENT event=step_finished', 'stepName=customers-step') -Message 'customer-load did not finish the explicit step.'
    Assert-FileContains -Path $customerOutput -ExpectedText '<Customers>' -Message 'customer-load did not produce expected XML output.'

    Write-Host "[2/3] Verifying negative smoke run: csv-to-sqlserver operational failure evidence"
    # Negative smoke: prove that the preserved SQL Server scenario still fails and
    # emits explicit failure evidence for operators.
    Invoke-MavenScenario -ScenarioName 'csv-to-sqlserver' -JobConfigPath 'src/main/resources/config-jobs/csv-to-sqlserver/job-config.yaml' -CaptureFile $negativeCapture -ExpectSuccess $false -AllowZeroExitOnExpectedFailure $true -AdditionalJvmArguments $negativeSmokeJvmArgs | Out-Null
    Assert-FileContains -Path $negativeCapture -ExpectedText "scenario 'csv-to-sqlserver'" -Message 'csv-to-sqlserver did not identify the expected scenario in fail-fast evidence.'
    Assert-FileContains -Path $negativeCapture -ExpectedText 'placeholder value' -Message 'csv-to-sqlserver did not fail fast on placeholder SQL Server values.'
    Assert-FileContains -Path $negativeCapture -ExpectedText 'BUILD FAILURE' -Message 'csv-to-sqlserver did not terminate with the expected startup failure.'

    Write-Host "[3/3] Verifying trigger-now log evidence from controller tests"
    $triggerArgs = @(
        '--no-transfer-progress'
        '-Dtest=JobBundleControllerTriggerNowUnitTest,ScheduleControllerTest'
        'test'
    )
    $triggerExitCode = Invoke-MavenWithTimeout -CaptureFile $triggerCapture -Arguments $triggerArgs -TimeoutMinutes $ScenarioTimeoutMinutes -OperationLabel 'trigger-now evidence tests'
    if ($triggerExitCode -ne 0) {
        throw "Trigger-now evidence tests failed unexpectedly. See $triggerCapture"
    }
    Assert-FileContains -Path $triggerCapture -ExpectedText 'CONTROLPLANE_TRIGGER event=trigger_now_requested scope=JOB' -Message 'Missing job trigger-now requested evidence.'
    Assert-FileContains -Path $triggerCapture -ExpectedText 'CONTROLPLANE_TRIGGER event=trigger_now_accepted scope=JOB' -Message 'Missing job trigger-now accepted evidence.'
    Assert-FileContains -Path $triggerCapture -ExpectedText 'CONTROLPLANE_TRIGGER event=trigger_now_duplicate_suppressed scope=JOB' -Message 'Missing job trigger-now duplicate-suppressed evidence.'
    Assert-FileContains -Path $triggerCapture -ExpectedText 'CONTROLPLANE_TRIGGER event=trigger_now_requested scope=SCHEDULE' -Message 'Missing schedule trigger-now requested evidence.'
    Assert-FileContains -Path $triggerCapture -ExpectedText 'CONTROLPLANE_TRIGGER event=trigger_now_accepted scope=SCHEDULE' -Message 'Missing schedule trigger-now accepted evidence.'
    Assert-FileContains -Path $triggerCapture -ExpectedText 'CONTROLPLANE_TRIGGER event=trigger_now_duplicate_suppressed scope=SCHEDULE' -Message 'Missing schedule trigger-now duplicate-suppressed evidence.'

    Write-Host ''
    Write-Host 'Verification PASSED' -ForegroundColor Green
    Write-Host "- Positive run log: $positiveCapture"
    Write-Host "- Negative run log: $negativeCapture"
    Write-Host "- Trigger evidence log: $triggerCapture"
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

    if ($null -eq $previousSpringDatasourceDriverClassName) {
        Remove-Item Env:SPRING_DATASOURCE_DRIVER_CLASS_NAME -ErrorAction SilentlyContinue
    }
    else {
        $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = $previousSpringDatasourceDriverClassName
    }

    if ($null -eq $previousSpringDatasourceUsername) {
        Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
    }
    else {
        $env:SPRING_DATASOURCE_USERNAME = $previousSpringDatasourceUsername
    }

    if ($null -eq $previousSpringDatasourcePassword) {
        Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
    }
    else {
        $env:SPRING_DATASOURCE_PASSWORD = $previousSpringDatasourcePassword
    }

    if ($null -eq $previousSpringBatchInitializeSchema) {
        Remove-Item Env:SPRING_BATCH_JDBC_INITIALIZE_SCHEMA -ErrorAction SilentlyContinue
    }
    else {
        $env:SPRING_BATCH_JDBC_INITIALIZE_SCHEMA = $previousSpringBatchInitializeSchema
    }

    if ($null -eq $previousSpringProfilesActive) {
        Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    }
    else {
        $env:SPRING_PROFILES_ACTIVE = $previousSpringProfilesActive
    }
}
