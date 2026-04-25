<#
    Runs a small end-to-end smoke verification after code changes.

    What it verifies:
    1. A known-good explicit scenario (`customer-load`) still runs successfully.
    2. A known-bad preserved scenario (`csv-to-sqlserver`) fails early for the
       right reason: placeholder SQL Server connection values.

    Main artifacts:
    - target/verify-customer-load.log
    - target/verify-csv-to-sqlserver.log
    - target/customers.xml

    Important behavior:
    - The second scenario is expected to fail.
    - If both the positive and negative checks behave as expected, the script exits
      with code 0 so higher-level automation can treat the overall smoke verification
      as successful.
#>
param(
    [string]$RepoRoot = "C:\spring-etl-engine"
)

$ErrorActionPreference = 'Stop'

# Runs one Spring Boot scenario, captures its Maven console output, and enforces
# whether that scenario is expected to succeed or fail.
function Invoke-MavenScenario {
    param(
        [string]$ScenarioName,
        [string]$JobConfigPath,
        [string]$CaptureFile,
        [bool]$ExpectSuccess
    )

    $jvmArgs = "-Detl.config.job=$JobConfigPath"

    Push-Location $RepoRoot
    try {
        if (Test-Path $CaptureFile) {
            Remove-Item $CaptureFile -Force
        }

        # Capture the full Maven/Spring Boot console transcript so later checks can
        # validate specific proof points from logs, not just process exit codes.
        & mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=$jvmArgs" spring-boot:run 2>&1 | Out-File -FilePath $CaptureFile -Encoding utf8
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    if ($ExpectSuccess -and $exitCode -ne 0) {
        throw "$ScenarioName run failed unexpectedly. See $CaptureFile"
    }

    if (-not $ExpectSuccess -and $exitCode -eq 0) {
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

$positiveCapture = Join-Path $RepoRoot 'target\verify-customer-load.log'
$negativeCapture = Join-Path $RepoRoot 'target\verify-csv-to-sqlserver.log'
$customerOutput = Join-Path $RepoRoot 'target\customers.xml'

Write-Host "[1/2] Verifying positive smoke run: customer-load"
if (Test-Path $customerOutput) {
    Remove-Item $customerOutput -Force
}

# Positive smoke: prove that one explicit scenario still runs end-to-end,
# emits the expected run/step events, and writes its target output.
Invoke-MavenScenario -ScenarioName 'customer-load' -JobConfigPath 'src/main/resources/config-scenarios/customer-load/job-config.yaml' -CaptureFile $positiveCapture -ExpectSuccess $true | Out-Null
Assert-FileContains -Path $positiveCapture -ExpectedText 'RUN_SUMMARY event=run_summary scenario=customer-load' -Message 'customer-load did not emit expected run summary.'
Assert-FileContains -Path $positiveCapture -ExpectedText 'status=COMPLETED' -Message 'customer-load did not complete successfully.'
Assert-FileContains -Path $positiveCapture -ExpectedText 'STEP_EVENT event=step_finished stepName=customers-step' -Message 'customer-load did not finish the explicit step.'
Assert-FileContains -Path $customerOutput -ExpectedText '<Customers>' -Message 'customer-load did not produce expected XML output.'

Write-Host "[2/2] Verifying fail-fast smoke run: csv-to-sqlserver placeholder validation"
# Negative smoke: prove that the preserved SQL Server scenario now fails early for
# placeholder connection values instead of progressing to a late JDBC failure.
Invoke-MavenScenario -ScenarioName 'csv-to-sqlserver' -JobConfigPath 'src/main/resources/config-scenarios/csv-to-sqlserver/job-config.yaml' -CaptureFile $negativeCapture -ExpectSuccess $false | Out-Null
Assert-FileContains -Path $negativeCapture -ExpectedText "Invalid relational target configuration for scenario 'csv-to-sqlserver'" -Message 'csv-to-sqlserver did not fail with scenario-aware config validation.'
Assert-FileContains -Path $negativeCapture -ExpectedText 'placeholder value' -Message 'csv-to-sqlserver did not report placeholder connection details.'
Assert-FileContains -Path $negativeCapture -ExpectedText 'BUILD FAILURE' -Message 'csv-to-sqlserver did not fail the Maven run as expected.'

Write-Host ''
Write-Host 'Verification PASSED' -ForegroundColor Green
Write-Host "- Positive run log: $positiveCapture"
Write-Host "- Negative run log: $negativeCapture"
Write-Host "- Positive output: $customerOutput"

# Reset the process exit code to success because the negative scenario already failed
# in the expected way and all smoke assertions passed.
$global:LASTEXITCODE = 0
exit 0




