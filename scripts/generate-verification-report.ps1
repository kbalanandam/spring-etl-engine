<#
    Generates a local verification report after code changes.

    What it does:
    1. Runs `mvn test` and captures the console output to `target/verification-mvn-test.log`
    2. Parses `target/surefire-reports/TEST-*.xml` to build a suite-by-suite summary
    3. Optionally runs the smoke-verification script to validate one known-good scenario
       and one expected fail-fast scenario
    4. Writes a readable markdown report to `target/verification-report.md`
       and also saves a timestamped historical copy in `target/`

    Main output files:
    - target/verification-report.md
    - target/verification-report-<timestamp>.md
    - target/verification-mvn-test.log
    - target/verification-smoke.log

    Optional behavior:
    - Use `-SkipSmoke` when you only want unit/integration test reporting.

    Notes:
    - Defaults assume this repository is located at `C:\spring-etl-engine`.
    - This script is intended for local verification on Windows PowerShell.
#>
param(
    [string]$RepoRoot = "C:\spring-etl-engine",
    [string]$ReportPath = "C:\spring-etl-engine\target\verification-report.md",
    [int]$KeepLatestCount = 5,
    [switch]$SkipSmoke,
    [ValidateSet('Markdown', 'Html', 'HtmlAndPdf')]
    [string]$ReportPublishMode = 'Markdown',
    [string]$HtmlReportPath,
    [string]$PdfReportPath
)

$ErrorActionPreference = 'Stop'

# Runs the full Maven test suite and captures the complete console log.
# Returns timing and exit-code metadata so the report can summarize both
# raw Maven status and end-to-end execution duration.
function Invoke-MavenTestRun {
    param(
        [string]$WorkingDirectory,
        [string]$CaptureFile
    )

    if (Test-Path $CaptureFile) {
        Remove-Item $CaptureFile -Force
    }

    Push-Location $WorkingDirectory
    try {
        $start = Get-Date
        # We run Maven through cmd.exe so stdout/stderr redirection is stable on
        # Windows PowerShell and we still get a clean log file plus `$LASTEXITCODE`.
        $escapedCaptureFile = $CaptureFile.Replace('"', '""')
        $command = "mvn --no-transfer-progress test > `"$escapedCaptureFile`" 2>&1"
        & cmd.exe /d /c $command | Out-Null
        $exitCode = $LASTEXITCODE
        $end = Get-Date
    }
    finally {
        Pop-Location
    }

    [pscustomobject]@{
        ExitCode = $exitCode
        StartTime = $start
        EndTime = $end
        Duration = ($end - $start)
        LogPath = $CaptureFile
    }
}

# Normalizes free-form XML text into one short, markdown-friendly line.
function Convert-ToCompactSingleLine {
    param(
        [AllowNull()][string]$Text,
        [int]$MaxLength = 240
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return ''
    }

    $normalized = ($Text -replace '\s+', ' ').Trim()
    if ($normalized.Length -le $MaxLength) {
        return $normalized
    }

    return $normalized.Substring(0, $MaxLength - 3) + '...'
}

# Prevent markdown tables/lists from breaking when names/messages contain special characters.
function Format-MarkdownInlineText {
    param(
        [AllowNull()][string]$Text
    )

    if ([string]::IsNullOrEmpty($Text)) {
        return ''
    }

    return (($Text -replace '\|', '\\|') -replace '`', '\`')
}

# Converts internal PASS/FAIL/etc. states into a more scannable badge for markdown output.
function Get-StatusBadge {
    param(
        [AllowNull()][string]$Status
    )

    $normalizedStatus = if ($null -eq $Status) { '' } else { $Status.ToUpperInvariant() }

    switch ($normalizedStatus) {
        'READY' { return '[READY]' }
        'NOT READY' { return '[NOT READY]' }
        'PASS' { return '[PASS]' }
        'FAIL' { return '[FAIL]' }
        'ERROR' { return '[ERROR]' }
        'SKIPPED' { return '[SKIPPED]' }
        default { return $Status }
    }
}

# Summarizes the working-tree status codes so the report remains readable even when
# the detailed changed-file list is long.
function Get-GitStatusCounts {
    param(
        [object[]]$StatusLines
    )

    $counts = [ordered]@{
        Modified = 0
        Added = 0
        Deleted = 0
        Renamed = 0
        Untracked = 0
        Other = 0
    }

    foreach ($statusLine in @($StatusLines)) {
        $trimmed = ($statusLine | Out-String).Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            continue
        }

        if ($trimmed.StartsWith('??')) {
            $counts.Untracked++
            continue
        }

        $x = if ($trimmed.Length -ge 1) { $trimmed.Substring(0, 1) } else { ' ' }
        $y = if ($trimmed.Length -ge 2) { $trimmed.Substring(1, 1) } else { ' ' }
        $combined = ($x + $y)

        if ($combined.Contains('M')) {
            $counts.Modified++
        }
        elseif ($combined.Contains('A')) {
            $counts.Added++
        }
        elseif ($combined.Contains('D')) {
            $counts.Deleted++
        }
        elseif ($combined.Contains('R')) {
            $counts.Renamed++
        }
        else {
            $counts.Other++
        }
    }

    return [pscustomobject]$counts
}

# Classifies a test suite into high-level area and optional subarea buckets so
# the report can provide faster scan-first summaries before full suite details.
function Get-SuiteAreaMetadata {
    param(
        [AllowNull()][string]$SuiteName
    )

    $normalized = if ($null -eq $SuiteName) { '' } else { $SuiteName.ToLowerInvariant() }
    $areaKey = 'other'
    $areaLabel = 'Other'
    $subareaKey = 'general'
    $subareaLabel = 'General'

    if ($normalized.StartsWith('com.etl.config.')) {
        $areaKey = 'config'; $areaLabel = 'Config'
        if ($normalized.Contains('.source.validation.')) { $subareaKey = 'source-validation'; $subareaLabel = 'Source validation' }
        elseif ($normalized.Contains('.source.')) { $subareaKey = 'source'; $subareaLabel = 'Source config' }
        elseif ($normalized.Contains('.target.')) { $subareaKey = 'target'; $subareaLabel = 'Target config' }
        elseif ($normalized.Contains('.relational.')) { $subareaKey = 'relational'; $subareaLabel = 'Relational config' }
        else { $subareaKey = 'core'; $subareaLabel = 'Core config' }
    }
    elseif ($normalized.StartsWith('com.etl.reader.')) {
        $areaKey = 'reader'; $areaLabel = 'Reader'
        if ($normalized.Contains('csv')) { $subareaKey = 'csv'; $subareaLabel = 'CSV' }
        elseif ($normalized.Contains('xml')) { $subareaKey = 'xml'; $subareaLabel = 'XML' }
        elseif ($normalized.Contains('json')) { $subareaKey = 'json'; $subareaLabel = 'JSON' }
        elseif ($normalized.Contains('relational')) { $subareaKey = 'relational'; $subareaLabel = 'Relational' }
        else { $subareaKey = 'core'; $subareaLabel = 'Core reader' }
    }
    elseif ($normalized.StartsWith('com.etl.writer.')) {
        $areaKey = 'writer'; $areaLabel = 'Writer'
        if ($normalized.Contains('csv')) { $subareaKey = 'csv'; $subareaLabel = 'CSV' }
        elseif ($normalized.Contains('xml')) { $subareaKey = 'xml'; $subareaLabel = 'XML' }
        elseif ($normalized.Contains('json')) { $subareaKey = 'json'; $subareaLabel = 'JSON' }
        elseif ($normalized.Contains('relational')) { $subareaKey = 'relational'; $subareaLabel = 'Relational' }
        else { $subareaKey = 'core'; $subareaLabel = 'Core writer' }
    }
    elseif ($normalized.StartsWith('com.etl.processor.')) {
        $areaKey = 'processor'; $areaLabel = 'Processor'
        if ($normalized.Contains('.validation.')) { $subareaKey = 'validation'; $subareaLabel = 'Validation rules' }
        elseif ($normalized.Contains('.transform.')) { $subareaKey = 'transform'; $subareaLabel = 'Transforms' }
        elseif ($normalized.Contains('.pipeline.')) { $subareaKey = 'pipeline'; $subareaLabel = 'Pipeline' }
        else { $subareaKey = 'core'; $subareaLabel = 'Core processor' }
    }
    elseif ($normalized.StartsWith('com.etl.runtime.')) {
        $areaKey = 'runtime'; $areaLabel = 'Runtime'
        if ($normalized.Contains('duplicateresolver')) { $subareaKey = 'duplicate-resolver'; $subareaLabel = 'Duplicate resolver' }
        elseif ($normalized.Contains('.job.')) { $subareaKey = 'job-runtime'; $subareaLabel = 'Job runtime' }
        else { $subareaKey = 'core'; $subareaLabel = 'Core runtime' }
    }
    elseif ($normalized.StartsWith('com.etl.flow.')) {
        $areaKey = 'flow'; $areaLabel = 'Flow'; $subareaKey = 'scenario-flows'; $subareaLabel = 'Scenario flows'
    }
    elseif ($normalized.StartsWith('com.etl.relational.')) {
        $areaKey = 'relational'; $areaLabel = 'Relational'; $subareaKey = 'relational-flows'; $subareaLabel = 'Relational flows'
    }
    elseif ($normalized.StartsWith('com.etl.generation.')) {
        $areaKey = 'generation'; $areaLabel = 'Generation'; $subareaKey = 'xml-generation'; $subareaLabel = 'XML generation'
    }
    elseif ($normalized.StartsWith('com.etl.mapping.')) {
        $areaKey = 'mapping'; $areaLabel = 'Mapping'; $subareaKey = 'dynamic-mapping'; $subareaLabel = 'Dynamic mapping'
    }
    elseif ($normalized.StartsWith('com.etl.common.')) {
        $areaKey = 'common'; $areaLabel = 'Common'; $subareaKey = 'common-utils'; $subareaLabel = 'Utilities'
    }
    elseif ($normalized.StartsWith('com.etl.source.')) {
        $areaKey = 'source'; $areaLabel = 'Source'; $subareaKey = 'source-strategy'; $subareaLabel = 'Source strategy'
    }
    elseif ($normalized.StartsWith('com.etl.job.')) {
        $areaKey = 'job'; $areaLabel = 'Job'; $subareaKey = 'job-listeners'; $subareaLabel = 'Job listeners'
    }
    elseif ($normalized.StartsWith('com.etl.runner.')) {
        $areaKey = 'runner'; $areaLabel = 'Runner'; $subareaKey = 'etl-runner'; $subareaLabel = 'Runner'
    }
    elseif ($normalized.StartsWith('com.etl.aspect.')) {
        $areaKey = 'aspect'; $areaLabel = 'Aspect'; $subareaKey = 'logging-aspect'; $subareaLabel = 'Logging aspect'
    }
    elseif ($normalized.StartsWith('com.etl.model.')) {
        $areaKey = 'model'; $areaLabel = 'Model'; $subareaKey = 'generated-models'; $subareaLabel = 'Generated models'
    }

    [pscustomobject]@{
        AreaKey = $areaKey
        AreaLabel = $areaLabel
        SubareaKey = $subareaKey
        SubareaLabel = $subareaLabel
    }
}

# Reads Maven Surefire XML results and converts them into an in-memory model for
# totals, suite summaries, testcase-by-testcase detail, and non-passing highlights.
function Get-SurefireSummary {
    param(
        [string]$SurefireDirectory
    )

    if (-not (Test-Path $SurefireDirectory)) {
        return [pscustomobject]@{
            SuiteCount = 0
            Tests = 0
            Passed = 0
            Failures = 0
            Errors = 0
            Skipped = 0
            TimeSeconds = 0.0
            NonPassingCases = @()
            Suites = @()
        }
    }

    $xmlFiles = Get-ChildItem -Path $SurefireDirectory -Filter 'TEST-*.xml' -File | Sort-Object Name
    if (-not $xmlFiles) {
        return [pscustomobject]@{
            SuiteCount = 0
            Tests = 0
            Passed = 0
            Failures = 0
            Errors = 0
            Skipped = 0
            TimeSeconds = 0.0
            NonPassingCases = @()
            Suites = @()
        }
    }

    $suiteRows = New-Object System.Collections.ArrayList
    $nonPassingCases = New-Object System.Collections.ArrayList
    $totalTests = 0
    $totalFailures = 0
    $totalErrors = 0
    $totalSkipped = 0
    $totalTime = 0.0

    foreach ($file in $xmlFiles) {
        [xml]$doc = Get-Content -Path $file.FullName -Raw
        $suite = $doc.testsuite
        if ($null -eq $suite) {
            continue
        }

        $suiteName = [string]$suite.name
        if ([string]::IsNullOrWhiteSpace($suiteName)) {
            $suiteName = $file.BaseName
        }

        $suiteTests = [int]$suite.tests
        $suiteFailures = [int]$suite.failures
        $suiteErrors = [int]$suite.errors
        $suiteSkipped = [int]$suite.skipped
        $suiteTime = [double]$suite.time

        $totalTests += $suiteTests
        $totalFailures += $suiteFailures
        $totalErrors += $suiteErrors
        $totalSkipped += $suiteSkipped
        $totalTime += $suiteTime

        $caseRows = New-Object System.Collections.ArrayList

        # `@(...)` normalizes XML nodes so this works whether there is one testcase
        # element or many.
        foreach ($testcase in @($suite.testcase)) {
            if ($null -eq $testcase) {
                continue
            }

            $status = 'PASS'
            $kind = 'pass'
            $message = ''

            $failureNode = @($testcase.failure) | Select-Object -First 1
            $errorNode = @($testcase.error) | Select-Object -First 1
            $skippedNode = @($testcase.skipped) | Select-Object -First 1

            if ($null -ne $failureNode) {
                $status = 'FAIL'
                $kind = 'failure'
                $message = Convert-ToCompactSingleLine -Text ([string]$failureNode.message)
                if ([string]::IsNullOrWhiteSpace($message)) {
                    $message = Convert-ToCompactSingleLine -Text ([string]$failureNode.'#text')
                }
            }
            elseif ($null -ne $errorNode) {
                $status = 'ERROR'
                $kind = 'error'
                $message = Convert-ToCompactSingleLine -Text ([string]$errorNode.message)
                if ([string]::IsNullOrWhiteSpace($message)) {
                    $message = Convert-ToCompactSingleLine -Text ([string]$errorNode.'#text')
                }
            }
            elseif ($null -ne $skippedNode) {
                $status = 'SKIPPED'
                $kind = 'skipped'
                $message = Convert-ToCompactSingleLine -Text ([string]$skippedNode.message)
                if ([string]::IsNullOrWhiteSpace($message)) {
                    $message = Convert-ToCompactSingleLine -Text ([string]$skippedNode.'#text')
                }
            }

            $caseRow = [pscustomobject]@{
                Suite = $suiteName
                Test = [string]$testcase.name
                ClassName = [string]$testcase.classname
                Status = $status
                Kind = $kind
                TimeSeconds = [math]::Round(([double]$testcase.time), 3)
                Message = $message
            }
            $caseRows.Add($caseRow) | Out-Null

            if ($status -ne 'PASS') {
                $nonPassingCases.Add($caseRow) | Out-Null
            }
        }

        $areaMetadata = Get-SuiteAreaMetadata -SuiteName $suiteName

        $suiteRows.Add([pscustomobject]@{
            Name = $suiteName
            AreaKey = $areaMetadata.AreaKey
            AreaLabel = $areaMetadata.AreaLabel
            SubareaKey = $areaMetadata.SubareaKey
            SubareaLabel = $areaMetadata.SubareaLabel
            Tests = $suiteTests
            Passed = ($suiteTests - $suiteFailures - $suiteErrors - $suiteSkipped)
            Failures = $suiteFailures
            Errors = $suiteErrors
            Skipped = $suiteSkipped
            TimeSeconds = [math]::Round($suiteTime, 3)
            Cases = @($caseRows)
        }) | Out-Null
    }

    [pscustomobject]@{
        SuiteCount = $suiteRows.Count
        Tests = $totalTests
        Passed = ($totalTests - $totalFailures - $totalErrors - $totalSkipped)
        Failures = $totalFailures
        Errors = $totalErrors
        Skipped = $totalSkipped
        TimeSeconds = [math]::Round($totalTime, 3)
        NonPassingCases = $nonPassingCases
        Suites = $suiteRows
    }
}

# Collects a lightweight Git summary so the report shows which branch was verified
# and what local file changes were present at the time of verification.
function Get-GitSummary {
    param(
        [string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        $branchName = (& git branch --show-current 2>$null)
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($branchName)) {
            $branchName = 'unknown'
        }

        $statusLines = @(& git --no-pager status --short 2>$null)
        if ($LASTEXITCODE -ne 0) {
            $statusLines = @('Git status unavailable.')
        }
    }
    finally {
        Pop-Location
    }

    [pscustomobject]@{
        Branch = ($branchName | Out-String).Trim()
        StatusLines = $statusLines
    }
}

# Runs the existing smoke-verification workflow and evaluates whether the expected
# proof points are present in the generated logs and output artifacts.
function Invoke-SmokeVerification {
    param(
        [string]$WorkingDirectory,
        [string]$CaptureFile
    )

    if (Test-Path $CaptureFile) {
        Remove-Item $CaptureFile -Force
    }

    $scriptPath = Join-Path $WorkingDirectory 'scripts\verify-recent-changes.ps1'
    Push-Location $WorkingDirectory
    try {
        # Run the smoke script in a fresh PowerShell process so local execution policy
        # does not block the workflow for new developers.
        & powershell.exe -ExecutionPolicy Bypass -File $scriptPath 2>&1 | Out-File -FilePath $CaptureFile -Encoding utf8
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }

    $positiveLog = Join-Path $WorkingDirectory 'target\verify-customer-load.log'
    $negativeLog = Join-Path $WorkingDirectory 'target\verify-csv-to-sqlserver.log'
    $positiveOutput = Join-Path $WorkingDirectory 'src\main\resources\config-jobs\customer-load\output\customers.xml'

    $positivePassed = (Test-Path $positiveLog) -and (Test-Path $positiveOutput)
    $negativePassed = (Test-Path $negativeLog)

    if ($positivePassed) {
        $positiveContent = Get-Content -Path $positiveLog -Raw
        # The positive smoke proves that a known-good explicit scenario still runs,
        # finishes its configured step, and emits the expected run summary.
        $positivePassed = $positiveContent.Contains('RUN_SUMMARY event=run_summary scenario=customer-load') -and
            $positiveContent.Contains('status=COMPLETED') -and
            $positiveContent.Contains('STEP_EVENT event=step_finished') -and
            $positiveContent.Contains('stepName=customers-step')
    }

    if ($negativePassed) {
        $negativeContent = Get-Content -Path $negativeLog -Raw
        # The negative smoke proves the system now fails early for placeholder SQL
        # Server values instead of reaching a late JDBC writer failure.
        $negativePassed = $negativeContent.Contains("Invalid relational target configuration for scenario 'csv-to-sqlserver'") -and
            $negativeContent.Contains('placeholder value') -and
            $negativeContent.Contains('BUILD FAILURE')
    }

    [pscustomobject]@{
        ExitCode = $exitCode
        Passed = ($exitCode -eq 0 -and $positivePassed -and $negativePassed)
        PositivePassed = $positivePassed
        NegativePassed = $negativePassed
        CaptureFile = $CaptureFile
        PositiveLog = $positiveLog
        NegativeLog = $negativeLog
        PositiveOutput = $positiveOutput
    }
}

# Builds one presentation-neutral evidence object that can later drive multiple
# report renderers while the current script still emits Markdown only.
function New-VerificationEvidence {
    param(
        [string]$RepoRoot,
        [pscustomobject]$MavenRun,
        [pscustomobject]$SurefireSummary,
        [pscustomobject]$SmokeSummary,
        [pscustomobject]$GitSummary,
        [bool]$SmokeWasSkipped
    )

    $buildStatus = if ($MavenRun.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }
    $smokeStatus = if ($SmokeWasSkipped) { 'SKIPPED' } elseif ($SmokeSummary -and $SmokeSummary.Passed) { 'PASS' } else { 'FAIL' }
    $positiveSmokeStatus = if ($SmokeWasSkipped) { 'SKIPPED' } elseif ($SmokeSummary -and $SmokeSummary.PositivePassed) { 'PASS' } else { 'FAIL' }
    $negativeSmokeStatus = if ($SmokeWasSkipped) { 'SKIPPED' } elseif ($SmokeSummary -and $SmokeSummary.NegativePassed) { 'PASS' } else { 'FAIL' }
    $overallReady = ($MavenRun.ExitCode -eq 0) -and ($SmokeWasSkipped -or ($SmokeSummary -and $SmokeSummary.Passed))
    $overallStatus = if ($overallReady) { 'READY' } else { 'NOT READY' }
    $gitStatusCounts = Get-GitStatusCounts -StatusLines $GitSummary.StatusLines
    $nonPassingCount = $SurefireSummary.Failures + $SurefireSummary.Errors + $SurefireSummary.Skipped
    $passRate = if ($SurefireSummary.Tests -gt 0) {
        [math]::Round(($SurefireSummary.Passed * 100.0) / $SurefireSummary.Tests, 1)
    }
    else {
        0
    }

    $slowestSuites = @($SurefireSummary.Suites | Sort-Object TimeSeconds -Descending | Select-Object -First 5)
    $allCases = @($SurefireSummary.Suites | ForEach-Object { @($_.Cases) })
    $slowestCases = @($allCases | Sort-Object TimeSeconds -Descending | Select-Object -First 5)

    $areaDisplayOrder = @('config', 'processor', 'reader', 'writer', 'runtime', 'flow', 'relational', 'generation', 'mapping', 'common', 'source', 'job', 'runner', 'aspect', 'model', 'other')
    $areaSummaries = New-Object System.Collections.ArrayList
    $groupedSuitesByArea = @{}

    foreach ($areaGroup in @($SurefireSummary.Suites | Group-Object -Property AreaKey)) {
        $groupedSuitesByArea[$areaGroup.Name] = @($areaGroup.Group)
    }

    $orderedAreaKeys = New-Object System.Collections.ArrayList
    foreach ($knownAreaKey in $areaDisplayOrder) {
        if ($groupedSuitesByArea.ContainsKey($knownAreaKey)) {
            $orderedAreaKeys.Add($knownAreaKey) | Out-Null
        }
    }
    foreach ($extraAreaKey in ($groupedSuitesByArea.Keys | Where-Object { $areaDisplayOrder -notcontains $_ } | Sort-Object)) {
        $orderedAreaKeys.Add($extraAreaKey) | Out-Null
    }

    foreach ($areaKey in @($orderedAreaKeys)) {
        $areaSuites = @($groupedSuitesByArea[$areaKey])
        if ($areaSuites.Count -eq 0) {
            continue
        }

        $areaLabel = ($areaSuites | Select-Object -First 1).AreaLabel
        $areaTests = ($areaSuites | Measure-Object -Property Tests -Sum).Sum
        $areaPassed = ($areaSuites | Measure-Object -Property Passed -Sum).Sum
        $areaFailures = ($areaSuites | Measure-Object -Property Failures -Sum).Sum
        $areaErrors = ($areaSuites | Measure-Object -Property Errors -Sum).Sum
        $areaSkipped = ($areaSuites | Measure-Object -Property Skipped -Sum).Sum
        $areaTime = [math]::Round((($areaSuites | Measure-Object -Property TimeSeconds -Sum).Sum), 3)
        $areaNonPassing = $areaFailures + $areaErrors + $areaSkipped
        $areaPassRate = if ($areaTests -gt 0) { [math]::Round(($areaPassed * 100.0) / $areaTests, 1) } else { 0 }
        $areaSlowestSuite = $areaSuites | Sort-Object TimeSeconds -Descending | Select-Object -First 1

        $subareaSummaries = New-Object System.Collections.ArrayList
        foreach ($subGroup in @($areaSuites | Group-Object -Property SubareaKey | Sort-Object Name)) {
            $subSuites = @($subGroup.Group)
            if ($subSuites.Count -eq 0) {
                continue
            }

            $subLabel = ($subSuites | Select-Object -First 1).SubareaLabel
            $subTests = ($subSuites | Measure-Object -Property Tests -Sum).Sum
            $subPassed = ($subSuites | Measure-Object -Property Passed -Sum).Sum
            $subFailures = ($subSuites | Measure-Object -Property Failures -Sum).Sum
            $subErrors = ($subSuites | Measure-Object -Property Errors -Sum).Sum
            $subSkipped = ($subSuites | Measure-Object -Property Skipped -Sum).Sum
            $subTime = [math]::Round((($subSuites | Measure-Object -Property TimeSeconds -Sum).Sum), 3)
            $subNonPassing = $subFailures + $subErrors + $subSkipped
            $subPassRate = if ($subTests -gt 0) { [math]::Round(($subPassed * 100.0) / $subTests, 1) } else { 0 }
            $subSlowestSuite = $subSuites | Sort-Object TimeSeconds -Descending | Select-Object -First 1

            $subareaSummaries.Add([pscustomobject]@{
                SubareaKey = $subGroup.Name
                SubareaLabel = $subLabel
                SuiteCount = $subSuites.Count
                Tests = $subTests
                Passed = $subPassed
                Failures = $subFailures
                Errors = $subErrors
                Skipped = $subSkipped
                NonPassing = $subNonPassing
                PassRate = $subPassRate
                TimeSeconds = $subTime
                SlowestSuite = $subSlowestSuite
            }) | Out-Null
        }

        $areaSummaries.Add([pscustomobject]@{
            AreaKey = $areaKey
            AreaLabel = $areaLabel
            SuiteCount = $areaSuites.Count
            SubareaCount = $subareaSummaries.Count
            Tests = $areaTests
            Passed = $areaPassed
            Failures = $areaFailures
            Errors = $areaErrors
            Skipped = $areaSkipped
            NonPassing = $areaNonPassing
            PassRate = $areaPassRate
            TimeSeconds = $areaTime
            SlowestSuite = $areaSlowestSuite
            Subareas = @($subareaSummaries)
        }) | Out-Null
    }

    $releaseRecommendation = 'READY'
    if ($MavenRun.ExitCode -ne 0 -or $nonPassingCount -gt 0) {
        $releaseRecommendation = 'NOT READY'
    }
    elseif ($SmokeWasSkipped) {
        $releaseRecommendation = 'NEEDS RUNTIME EVIDENCE'
    }
    elseif (-not ($SmokeSummary -and $SmokeSummary.Passed)) {
        $releaseRecommendation = 'NOT READY'
    }

    $releaseReadinessReasons = New-Object System.Collections.Generic.List[string]
    if ($MavenRun.ExitCode -ne 0) {
        $releaseReadinessReasons.Add('Automated regression execution did not complete successfully.') | Out-Null
    }
    if ($nonPassingCount -gt 0) {
        $releaseReadinessReasons.Add('One or more failing, erroring, or skipped tests were reported in the regression evidence.') | Out-Null
    }
    if ($SmokeWasSkipped) {
        $releaseReadinessReasons.Add('Runtime / smoke verification was skipped, so this report is not yet full release evidence.') | Out-Null
    }
    elseif (-not ($SmokeSummary -and $SmokeSummary.Passed)) {
        $releaseReadinessReasons.Add('Runtime / smoke verification did not prove the expected scenario behavior.') | Out-Null
    }
    if ($GitSummary.StatusLines.Count -gt 0) {
        $releaseReadinessReasons.Add('Verification was executed against a working tree with local changes, so provenance is tied to the current workspace state rather than a clean immutable revision.') | Out-Null
    }

    [pscustomobject]@{
        Metadata = [pscustomobject]@{
            GeneratedAt = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')
            Repository = $RepoRoot
            SmokeWasSkipped = $SmokeWasSkipped
        }
        Overview = [pscustomobject]@{
            OverallStatus = $overallStatus
            BuildStatus = $buildStatus
            SmokeStatus = $smokeStatus
            PassRate = $passRate
            NonPassingCount = $nonPassingCount
            SlowestSuite = ($slowestSuites | Select-Object -First 1)
            SlowestCase = ($slowestCases | Select-Object -First 1)
        }
        ChangeFocused = [pscustomobject]@{
            Branch = $GitSummary.Branch
            ChangedFileCount = $GitSummary.StatusLines.Count
            StatusCounts = $gitStatusCounts
            StatusLines = @($GitSummary.StatusLines)
        }
        Regression = [pscustomobject]@{
            BuildStatus = $buildStatus
            MavenExitCode = $MavenRun.ExitCode
            SuiteCount = $SurefireSummary.SuiteCount
            Tests = $SurefireSummary.Tests
            Passed = $SurefireSummary.Passed
            Failures = $SurefireSummary.Failures
            Errors = $SurefireSummary.Errors
            Skipped = $SurefireSummary.Skipped
            SurefireTimeSeconds = $SurefireSummary.TimeSeconds
            MavenDurationSeconds = [math]::Round($MavenRun.Duration.TotalSeconds, 2)
            MavenLogPath = $MavenRun.LogPath
            Suites = @($SurefireSummary.Suites)
            AreaSummaries = @($areaSummaries)
            SlowestSuites = @($slowestSuites)
            SlowestCases = @($slowestCases)
            NonPassingCases = @($SurefireSummary.NonPassingCases)
        }
        Runtime = [pscustomobject]@{
            Status = $smokeStatus
            ExitCode = if ($SmokeWasSkipped -or $null -eq $SmokeSummary) { $null } else { $SmokeSummary.ExitCode }
            PositiveStatus = $positiveSmokeStatus
            NegativeStatus = $negativeSmokeStatus
            CaptureFile = if ($SmokeSummary) { $SmokeSummary.CaptureFile } else { $null }
            PositiveLog = if ($SmokeSummary) { $SmokeSummary.PositiveLog } else { $null }
            NegativeLog = if ($SmokeSummary) { $SmokeSummary.NegativeLog } else { $null }
            PositiveOutput = if ($SmokeSummary) { $SmokeSummary.PositiveOutput } else { $null }
        }
        ReleaseReadiness = [pscustomobject]@{
            Recommendation = $releaseRecommendation
            EvidenceBasis = [pscustomobject]@{
                Maven = $buildStatus
                Smoke = $smokeStatus
                PassRate = $passRate
                NonPassing = $nonPassingCount
            }
            Caveats = @($releaseReadinessReasons)
        }
    }
}

# Builds the final markdown report from the shared verification evidence model.
function New-VerificationReport {
    param(
        [string]$Destination,
        [string]$TimestampedDestination,
        [pscustomobject]$Evidence
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $overallStatusBadge = Get-StatusBadge -Status $Evidence.Overview.OverallStatus
    $buildStatusBadge = Get-StatusBadge -Status $Evidence.Overview.BuildStatus
    $smokeStatusBadge = Get-StatusBadge -Status $Evidence.Overview.SmokeStatus
    $slowestSuite = $Evidence.Overview.SlowestSuite
    $slowestCase = $Evidence.Overview.SlowestCase

    $lines.Add("STATUS: $($Evidence.Overview.OverallStatus)") | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('# Verification Report') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add("Generated: $($Evidence.Metadata.GeneratedAt)") | Out-Null
    $lines.Add("Repository: $($Evidence.Metadata.Repository)") | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('## At a glance') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add("- Overall: **$overallStatusBadge**") | Out-Null
    $lines.Add("- Maven tests: **$buildStatusBadge**") | Out-Null
    $lines.Add("- Smoke verification: **$smokeStatusBadge**") | Out-Null
    $lines.Add("- Pass rate: **$($Evidence.Overview.PassRate)%** ($($Evidence.Regression.Passed)/$($Evidence.Regression.Tests))") | Out-Null
    $lines.Add("- Non-passing tests: **$($Evidence.Overview.NonPassingCount)**") | Out-Null
    if ($slowestSuite) {
        $lines.Add("- Slowest suite: **$(Format-MarkdownInlineText -Text $slowestSuite.Name)** ($($slowestSuite.TimeSeconds)s)") | Out-Null
    }
    if ($slowestCase) {
        $lines.Add("- Slowest testcase: **$(Format-MarkdownInlineText -Text $slowestCase.Test)** ($($slowestCase.TimeSeconds)s)") | Out-Null
    }
    $lines.Add('') | Out-Null
    $lines.Add('## Navigation') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('- [1. Change-focused verification](#1-change-focused-verification)') | Out-Null
    $lines.Add('- [2. Regression suite verification](#2-regression-suite-verification)') | Out-Null
    $lines.Add('- [Regression by area](#regression-by-area)') | Out-Null
    $lines.Add('- [3. Runtime and smoke verification](#3-runtime-and-smoke-verification)') | Out-Null
    $lines.Add('- [4. Release readiness](#4-release-readiness)') | Out-Null
    $lines.Add('- [How to read this report](#how-to-read-this-report)') | Out-Null
    $lines.Add('- [Recommended interpretation](#recommended-interpretation)') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('## How to read this report') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('- **READY / PASS**: Maven tests passed, and if smoke verification ran, it also passed.') | Out-Null
    $lines.Add('- **NOT READY / FAIL**: Maven tests failed, or smoke verification found a runtime/config regression.') | Out-Null
    $lines.Add('- A **PASS** negative smoke result for `csv-to-sqlserver` means the scenario failed in the expected fail-fast way because placeholder SQL Server values were detected.') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('## 1. Change-focused verification') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('- This category answers **what changed** and **which working-tree state** was verified.') | Out-Null
    $lines.Add('- In phase 1, the evidence source is the local Git working tree plus the generated verification artifacts tied to that state.') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('### Git and change context') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add("- Branch: **$($Evidence.ChangeFocused.Branch)**") | Out-Null
    $lines.Add("- Changed file count: **$($Evidence.ChangeFocused.ChangedFileCount)**") | Out-Null
    $lines.Add("- Modified: **$($Evidence.ChangeFocused.StatusCounts.Modified)**") | Out-Null
    $lines.Add("- Added: **$($Evidence.ChangeFocused.StatusCounts.Added)**") | Out-Null
    $lines.Add("- Deleted: **$($Evidence.ChangeFocused.StatusCounts.Deleted)**") | Out-Null
    $lines.Add("- Renamed: **$($Evidence.ChangeFocused.StatusCounts.Renamed)**") | Out-Null
    $lines.Add("- Untracked: **$($Evidence.ChangeFocused.StatusCounts.Untracked)**") | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('### Changed files at report time') | Out-Null
    $lines.Add('') | Out-Null
    if ($Evidence.ChangeFocused.ChangedFileCount -eq 0) {
        $lines.Add('- Working tree clean.') | Out-Null
    }
    else {
        foreach ($statusLine in $Evidence.ChangeFocused.StatusLines) {
            $trimmedStatusLine = ($statusLine | Out-String).Trim()
            if (-not [string]::IsNullOrWhiteSpace($trimmedStatusLine)) {
                $lines.Add("- $trimmedStatusLine") | Out-Null
            }
        }
    }
    $lines.Add('') | Out-Null
    $lines.Add('## 2. Regression suite verification') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('- This category answers **did broader automated regression evidence remain healthy after the recent change set?**') | Out-Null
    $lines.Add('- It combines Maven execution status, Surefire suite/testcase results, and non-passing-case visibility.') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('### Automated regression summary') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add("- Maven test run: **$($Evidence.Regression.BuildStatus)** (exit code $($Evidence.Regression.MavenExitCode))") | Out-Null
    $lines.Add("- Test suites: **$($Evidence.Regression.SuiteCount)**") | Out-Null
    $lines.Add("- Tests run: **$($Evidence.Regression.Tests)**") | Out-Null
    $lines.Add("- Passed: **$($Evidence.Regression.Passed)**") | Out-Null
    $lines.Add("- Failures: **$($Evidence.Regression.Failures)**") | Out-Null
    $lines.Add("- Errors: **$($Evidence.Regression.Errors)**") | Out-Null
    $lines.Add("- Skipped: **$($Evidence.Regression.Skipped)**") | Out-Null
    $lines.Add("- Surefire reported time: **$($Evidence.Regression.SurefireTimeSeconds)s**") | Out-Null
    $lines.Add("- End-to-end Maven duration: **$($Evidence.Regression.MavenDurationSeconds)s**") | Out-Null
    $lines.Add("- Maven log: $($Evidence.Regression.MavenLogPath)") | Out-Null
    $lines.Add('') | Out-Null

    $lines.Add('### Regression by area') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('- This view groups suites by product area first, so reviewers can quickly spot hotspots and then drill down.') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('| Area | Subareas | Suites | Tests | Non-passing | Pass rate | Time (s) | Slowest suite |') | Out-Null
    $lines.Add('| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |') | Out-Null
    foreach ($areaSummary in @($Evidence.Regression.AreaSummaries)) {
        $safeSlowestSuiteName = if ($areaSummary.SlowestSuite) { Format-MarkdownInlineText -Text $areaSummary.SlowestSuite.Name } else { 'n/a' }
        $lines.Add("| $($areaSummary.AreaLabel) | $($areaSummary.SubareaCount) | $($areaSummary.SuiteCount) | $($areaSummary.Tests) | $($areaSummary.NonPassing) | $($areaSummary.PassRate)% | $($areaSummary.TimeSeconds) | $safeSlowestSuiteName |") | Out-Null
    }
    $lines.Add('') | Out-Null

    foreach ($areaSummary in @($Evidence.Regression.AreaSummaries)) {
        $lines.Add("#### $($areaSummary.AreaLabel)") | Out-Null
        $lines.Add('') | Out-Null
        if ($areaSummary.Subareas.Count -eq 0) {
            $lines.Add('- No subarea data is available for this area.') | Out-Null
            $lines.Add('') | Out-Null
            continue
        }

        $lines.Add('| Subarea | Suites | Tests | Non-passing | Pass rate | Time (s) | Slowest suite |') | Out-Null
        $lines.Add('| --- | ---: | ---: | ---: | ---: | ---: | --- |') | Out-Null
        foreach ($subareaSummary in @($areaSummary.Subareas | Sort-Object TimeSeconds -Descending)) {
            $safeSubareaSlowestSuiteName = if ($subareaSummary.SlowestSuite) { Format-MarkdownInlineText -Text $subareaSummary.SlowestSuite.Name } else { 'n/a' }
            $safeSubareaLabel = Format-MarkdownInlineText -Text $subareaSummary.SubareaLabel
            $lines.Add("| $safeSubareaLabel | $($subareaSummary.SuiteCount) | $($subareaSummary.Tests) | $($subareaSummary.NonPassing) | $($subareaSummary.PassRate)% | $($subareaSummary.TimeSeconds) | $safeSubareaSlowestSuiteName |") | Out-Null
        }
        $lines.Add('') | Out-Null
    }

    $lines.Add('### Suite summary') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('| Suite | Tests | Passed | Failures | Errors | Skipped | Time (s) |') | Out-Null
    $lines.Add('| --- | ---: | ---: | ---: | ---: | ---: | ---: |') | Out-Null
    foreach ($suite in $Evidence.Regression.Suites) {
        $safeSuiteName = Format-MarkdownInlineText -Text $suite.Name
        $lines.Add("| $safeSuiteName | $($suite.Tests) | $($suite.Passed) | $($suite.Failures) | $($suite.Errors) | $($suite.Skipped) | $($suite.TimeSeconds) |") | Out-Null
    }
    $lines.Add('') | Out-Null

    $lines.Add('### Slowest suites') | Out-Null
    $lines.Add('') | Out-Null
    if ($Evidence.Regression.SlowestSuites.Count -eq 0) {
        $lines.Add('- No suite timing data was available.') | Out-Null
    }
    else {
        foreach ($suite in $Evidence.Regression.SlowestSuites) {
            $safeSuiteName = Format-MarkdownInlineText -Text $suite.Name
            $lines.Add('- **' + $safeSuiteName + '** - ' + $suite.TimeSeconds + 's (' + $suite.Tests + ' tests)') | Out-Null
        }
    }
    $lines.Add('') | Out-Null

    $lines.Add('### Slowest test cases') | Out-Null
    $lines.Add('') | Out-Null
    if ($Evidence.Regression.SlowestCases.Count -eq 0) {
        $lines.Add('- No testcase timing data was available.') | Out-Null
    }
    else {
        foreach ($case in $Evidence.Regression.SlowestCases) {
            $safeSuiteName = Format-MarkdownInlineText -Text $case.Suite
            $safeTestName = Format-MarkdownInlineText -Text $case.Test
            $statusBadge = Get-StatusBadge -Status $case.Status
            $lines.Add('- **' + $statusBadge + '** ' + $safeSuiteName + ' :: `' + $safeTestName + '` (' + $case.TimeSeconds + 's)') | Out-Null
        }
    }
    $lines.Add('') | Out-Null

    $lines.Add('### Test case details') | Out-Null
    $lines.Add('') | Out-Null
    if ($Evidence.Regression.SuiteCount -eq 0) {
        $lines.Add('- No testcase details were available because no Surefire XML reports were found.') | Out-Null
    }
    else {
        foreach ($suite in $Evidence.Regression.Suites) {
            $safeSuiteName = Format-MarkdownInlineText -Text $suite.Name
            $lines.Add("#### $safeSuiteName") | Out-Null
            $lines.Add('') | Out-Null

            if ($suite.Cases.Count -eq 0) {
                $lines.Add('- No testcase entries were reported for this suite.') | Out-Null
                $lines.Add('') | Out-Null
                continue
            }

            foreach ($case in $suite.Cases) {
                $safeTestName = Format-MarkdownInlineText -Text $case.Test
                $statusBadge = Get-StatusBadge -Status $case.Status
                $lines.Add('- **' + $statusBadge + '** `' + $safeTestName + '` (' + $case.TimeSeconds + 's)') | Out-Null
                if (-not [string]::IsNullOrWhiteSpace($case.Message)) {
                    $safeMessage = Format-MarkdownInlineText -Text $case.Message
                    $lines.Add("  - Detail: $safeMessage") | Out-Null
                }
            }

            $lines.Add('') | Out-Null
        }
    }

    $lines.Add('### Non-passing test details') | Out-Null
    $lines.Add('') | Out-Null
    if ($Evidence.Regression.NonPassingCases.Count -eq 0) {
        $lines.Add('- No failing, erroring, or skipped test cases were reported.') | Out-Null
    }
    else {
        foreach ($nonPassingCase in $Evidence.Regression.NonPassingCases) {
            $safeSuiteName = Format-MarkdownInlineText -Text $nonPassingCase.Suite
            $safeTestName = Format-MarkdownInlineText -Text $nonPassingCase.Test
            $statusBadge = Get-StatusBadge -Status $nonPassingCase.Status
            $lines.Add("- [$($nonPassingCase.Kind)] **$statusBadge** $safeSuiteName :: $safeTestName") | Out-Null
            if (-not [string]::IsNullOrWhiteSpace($nonPassingCase.Message)) {
                $safeMessage = Format-MarkdownInlineText -Text $nonPassingCase.Message
                $lines.Add("  - Message: $safeMessage") | Out-Null
            }
        }
    }
    $lines.Add('') | Out-Null

    $lines.Add('## 3. Runtime and smoke verification') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('- This category answers **did representative runtime scenarios still behave correctly, including expected fail-fast behavior?**') | Out-Null
    $lines.Add('- In phase 1, this category is populated from the existing smoke workflow and its generated artifacts.') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('### Runtime and smoke evidence') | Out-Null
    $lines.Add('') | Out-Null
    if ($Evidence.Metadata.SmokeWasSkipped) {
        $lines.Add('- Smoke verification: **SKIPPED**') | Out-Null
        $lines.Add('- Scope note: runtime scenario evidence was intentionally omitted for this report run.') | Out-Null
    }
    else {
        $lines.Add("- Smoke verification: **$($Evidence.Runtime.Status)** (exit code $($Evidence.Runtime.ExitCode))") | Out-Null
        $lines.Add("- Positive smoke (customer-load): **$($Evidence.Runtime.PositiveStatus)**") | Out-Null
        $lines.Add("- Negative smoke (csv-to-sqlserver fail-fast): **$($Evidence.Runtime.NegativeStatus)**") | Out-Null
        $lines.Add('- Runtime evidence captured from:') | Out-Null
        $lines.Add("  - Smoke capture: $($Evidence.Runtime.CaptureFile)") | Out-Null
        $lines.Add("  - Positive log: $($Evidence.Runtime.PositiveLog)") | Out-Null
        $lines.Add("  - Negative log: $($Evidence.Runtime.NegativeLog)") | Out-Null
        $lines.Add("  - Positive output: $($Evidence.Runtime.PositiveOutput)") | Out-Null
    }
    $lines.Add('') | Out-Null

    $lines.Add('## 4. Release readiness') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('- This category answers **is the current evidence scope strong enough to treat the verified workspace state as release-ready?**') | Out-Null
    $lines.Add('- Phase 1 uses currently available local evidence: Git context, automated regression, and smoke/runtime proof when present.') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('### Release readiness decision') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('- Recommendation: **' + $Evidence.ReleaseReadiness.Recommendation + '**') | Out-Null
    $lines.Add("- Evidence basis: Maven=$($Evidence.ReleaseReadiness.EvidenceBasis.Maven), Smoke=$($Evidence.ReleaseReadiness.EvidenceBasis.Smoke), PassRate=$($Evidence.ReleaseReadiness.EvidenceBasis.PassRate)%, NonPassing=$($Evidence.ReleaseReadiness.EvidenceBasis.NonPassing)") | Out-Null
    if ($Evidence.ReleaseReadiness.Caveats.Count -eq 0) {
        $lines.Add('- No release-readiness caveats were detected in the currently collected evidence.') | Out-Null
    }
    else {
        $lines.Add('- Release-readiness caveats:') | Out-Null
        foreach ($reason in $Evidence.ReleaseReadiness.Caveats) {
            $lines.Add('  - ' + $reason) | Out-Null
        }
    }
    $lines.Add('') | Out-Null

    $lines.Add('## Recommended interpretation') | Out-Null
    $lines.Add('') | Out-Null
    $lines.Add('- If Maven test run is PASS and smoke verification is PASS, the recent code changes are in a good local verification state.') | Out-Null
    $lines.Add('- If Maven tests pass but smoke verification fails, review runtime/logging or scenario-specific behavior.') | Out-Null
    $lines.Add('- If smoke verification is skipped, use the report for change-focused and regression evidence only, not as full release evidence.') | Out-Null
    $lines.Add('- If Maven tests fail, fix those before trusting smoke results.') | Out-Null

    $reportDir = Split-Path -Path $Destination -Parent
    if (-not (Test-Path $reportDir)) {
        New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
    }

    Set-Content -Path $Destination -Value $lines -Encoding utf8
    if ($TimestampedDestination) {
        Set-Content -Path $TimestampedDestination -Value $lines -Encoding utf8
    }
}

# Builds a sortable timestamped report filename in the same folder as the stable
# latest report. Example: verification-report-20260425-053000.md
function Get-TimestampedReportPath {
    param(
        [string]$BaseReportPath
    )

    $reportDirectory = Split-Path -Path $BaseReportPath -Parent
    $reportExtension = [System.IO.Path]::GetExtension($BaseReportPath)
    $reportBaseName = [System.IO.Path]::GetFileNameWithoutExtension($BaseReportPath)
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'

    Join-Path $reportDirectory ($reportBaseName + '-' + $timestamp + $reportExtension)
}

# Resolves sibling report artifact paths for html/pdf outputs unless explicitly provided.
function Resolve-PublishArtifactPaths {
    param(
        [string]$MarkdownPath,
        [AllowNull()][string]$RequestedHtmlPath,
        [AllowNull()][string]$RequestedPdfPath
    )

    $reportDirectory = Split-Path -Path $MarkdownPath -Parent
    $reportBaseName = [System.IO.Path]::GetFileNameWithoutExtension($MarkdownPath)

    $resolvedHtmlPath = if ([string]::IsNullOrWhiteSpace($RequestedHtmlPath)) {
        Join-Path $reportDirectory ($reportBaseName + '.html')
    }
    else {
        $RequestedHtmlPath
    }

    $resolvedPdfPath = if ([string]::IsNullOrWhiteSpace($RequestedPdfPath)) {
        Join-Path $reportDirectory ($reportBaseName + '.pdf')
    }
    else {
        $RequestedPdfPath
    }

    [pscustomobject]@{
        HtmlPath = $resolvedHtmlPath
        PdfPath = $resolvedPdfPath
    }
}

function Get-SectionAnchorId {
    param(
        [string]$HeadingText
    )

    $normalized = $HeadingText.ToLowerInvariant()
    $slug = ($normalized -replace '[^a-z0-9\s-]', '' -replace '\s+', '-' -replace '-{2,}', '-').Trim('-')
    if ([string]::IsNullOrWhiteSpace($slug)) {
        return 'section'
    }
    return $slug
}

function Convert-InlineMarkdownToHtml {
    param(
        [AllowNull()][string]$Text
    )

    $encoded = [System.Net.WebUtility]::HtmlEncode([string]$Text)
    $encoded = [System.Text.RegularExpressions.Regex]::Replace($encoded, '\[(.+?)\]\((https?://[^\)]+)\)', '<a href="$2">$1</a>')
    $encoded = [System.Text.RegularExpressions.Regex]::Replace($encoded, '`([^`]+)`', '<code>$1</code>')
    $encoded = [System.Text.RegularExpressions.Regex]::Replace($encoded, '\*\*(.+?)\*\*', '<strong>$1</strong>')
    return $encoded
}

function Convert-MarkdownToHtmlFragmentBasic {
    param(
        [string]$MarkdownText
    )

    $lines = @($MarkdownText -split "`r?`n")
    $htmlLines = New-Object System.Collections.Generic.List[string]
    $inList = $false
    $inCodeBlock = $false
    $codeLines = New-Object System.Collections.Generic.List[string]

    $index = 0
    while ($index -lt $lines.Count) {
        $line = $lines[$index]
        $trimmed = $line.Trim()

        if ($trimmed.StartsWith('```')) {
            if ($inCodeBlock) {
                $htmlLines.Add('<pre><code>' + ([System.Net.WebUtility]::HtmlEncode(($codeLines -join "`n"))) + '</code></pre>') | Out-Null
                $codeLines.Clear()
                $inCodeBlock = $false
            }
            else {
                if ($inList) {
                    $htmlLines.Add('</ul>') | Out-Null
                    $inList = $false
                }
                $inCodeBlock = $true
            }
            $index++
            continue
        }

        if ($inCodeBlock) {
            $codeLines.Add($line) | Out-Null
            $index++
            continue
        }

        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            if ($inList) {
                $htmlLines.Add('</ul>') | Out-Null
                $inList = $false
            }
            $index++
            continue
        }

        if ($trimmed.StartsWith('|') -and ($index + 1 -lt $lines.Count)) {
            $nextTrimmed = $lines[$index + 1].Trim()
            if ($nextTrimmed -match '^\|\s*[:\-\|\s]+\|?$') {
                if ($inList) {
                    $htmlLines.Add('</ul>') | Out-Null
                    $inList = $false
                }

                $tableRows = New-Object System.Collections.Generic.List[string]
                $tableRows.Add($trimmed) | Out-Null
                $index += 2
                while ($index -lt $lines.Count) {
                    $candidate = $lines[$index].Trim()
                    if ($candidate.StartsWith('|')) {
                        $tableRows.Add($candidate) | Out-Null
                        $index++
                        continue
                    }
                    break
                }

                $htmlLines.Add('<table>') | Out-Null
                $headerRow = $tableRows[0]
                $headerTrimmed = $headerRow.Trim('|')
                $headerCells = @($headerTrimmed.Split('|') | ForEach-Object { $_.Trim() })
                $htmlLines.Add('<thead><tr>') | Out-Null
                foreach ($cell in $headerCells) {
                    $htmlLines.Add('<th>' + (Convert-InlineMarkdownToHtml -Text $cell) + '</th>') | Out-Null
                }
                $htmlLines.Add('</tr></thead>') | Out-Null
                $htmlLines.Add('<tbody>') | Out-Null
                for ($rowIndex = 1; $rowIndex -lt $tableRows.Count; $rowIndex++) {
                    $bodyTrimmed = $tableRows[$rowIndex].Trim('|')
                    $bodyCells = @($bodyTrimmed.Split('|') | ForEach-Object { $_.Trim() })
                    $htmlLines.Add('<tr>') | Out-Null
                    foreach ($cell in $bodyCells) {
                        $htmlLines.Add('<td>' + (Convert-InlineMarkdownToHtml -Text $cell) + '</td>') | Out-Null
                    }
                    $htmlLines.Add('</tr>') | Out-Null
                }
                $htmlLines.Add('</tbody></table>') | Out-Null
                continue
            }
        }

        if ($trimmed -match '^(#{1,6})\s+(.+)$') {
            if ($inList) {
                $htmlLines.Add('</ul>') | Out-Null
                $inList = $false
            }

            $headingLevel = $matches[1].Length
            $headingText = $matches[2].Trim()
            $headingId = Get-SectionAnchorId -HeadingText $headingText
            $htmlLines.Add('<h' + $headingLevel + ' id="' + $headingId + '">' + (Convert-InlineMarkdownToHtml -Text $headingText) + '</h' + $headingLevel + '>') | Out-Null
            $index++
            continue
        }

        if ($trimmed -match '^-\s+(.+)$') {
            if (-not $inList) {
                $htmlLines.Add('<ul>') | Out-Null
                $inList = $true
            }
            $htmlLines.Add('<li>' + (Convert-InlineMarkdownToHtml -Text $matches[1].Trim()) + '</li>') | Out-Null
            $index++
            continue
        }

        if ($inList) {
            $htmlLines.Add('</ul>') | Out-Null
            $inList = $false
        }

        $htmlLines.Add('<p>' + (Convert-InlineMarkdownToHtml -Text $trimmed) + '</p>') | Out-Null
        $index++
    }

    if ($inList) {
        $htmlLines.Add('</ul>') | Out-Null
    }
    if ($inCodeBlock) {
        $htmlLines.Add('<pre><code>' + ([System.Net.WebUtility]::HtmlEncode(($codeLines -join "`n"))) + '</code></pre>') | Out-Null
    }

    return ($htmlLines -join "`n")
}

function New-VerificationHtmlDocument {
    param(
        [string]$BodyHtml
    )

    @"
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <title>Verification Report</title>
  <style>
    body { font-family: Segoe UI, Arial, sans-serif; margin: 24px; line-height: 1.45; color: #1f2328; }
    .section-tabs { position: sticky; top: 0; background: #ffffff; border-bottom: 1px solid #d8dee4; padding: 8px 0; margin-bottom: 16px; }
    .section-tabs a { display: inline-block; margin-right: 8px; padding: 6px 10px; border: 1px solid #d0d7de; border-radius: 999px; text-decoration: none; color: #0969da; font-size: 12px; }
    .section-tabs a:hover { background: #f6f8fa; }
    table { border-collapse: collapse; width: 100%; margin-bottom: 16px; }
    th, td { border: 1px solid #d0d7de; padding: 6px 8px; vertical-align: top; font-size: 13px; }
    th { background: #f6f8fa; }
    code, pre { background: #f6f8fa; }
    code { padding: 1px 4px; border-radius: 4px; }
    pre { padding: 10px; overflow-x: auto; border: 1px solid #d0d7de; border-radius: 6px; }
    h1, h2, h3, h4 { color: #0f172a; }
    @media print {
      .section-tabs { display: none; }
      h2 { break-before: page; }
      h2:first-of-type { break-before: auto; }
      body { margin: 12mm; }
    }
  </style>
</head>
<body>
  <div class="section-tabs">
    <a href="#1-change-focused-verification">Change</a>
    <a href="#2-regression-suite-verification">Regression</a>
    <a href="#3-runtime-and-smoke-verification">Runtime</a>
    <a href="#4-release-readiness">Readiness</a>
  </div>
$BodyHtml
</body>
</html>
"@
}

# Converts markdown report to an html report using available tooling.
# Priority: pandoc -> pwsh ConvertFrom-Markdown -> built-in markdown parser fallback.
function Publish-HtmlReport {
    param(
        [string]$MarkdownPath,
        [string]$HtmlPath
    )

    $htmlDir = Split-Path -Path $HtmlPath -Parent
    if (-not (Test-Path $htmlDir)) {
        New-Item -ItemType Directory -Path $htmlDir -Force | Out-Null
    }

    $pandocCommand = Get-Command pandoc -ErrorAction SilentlyContinue
    if ($pandocCommand) {
        & $pandocCommand.Source $MarkdownPath -f gfm -t html5 -s -o $HtmlPath
        if ($LASTEXITCODE -eq 0 -and (Test-Path $HtmlPath)) {
            return [pscustomobject]@{ Success = $true; Method = 'pandoc'; Path = $HtmlPath }
        }
    }

    $pwshCommand = Get-Command pwsh -ErrorAction SilentlyContinue
    if ($pwshCommand) {
        $pwshScript = @'
$inputPath = $args[0]
$outputPath = $args[1]
$markdown = Get-Content -Path $inputPath -Raw
$rendered = ConvertFrom-Markdown -InputObject $markdown
$document = [string](Get-Command -Name New-VerificationHtmlDocument -ErrorAction SilentlyContinue | Out-Null)
$bodyHtml = $rendered.Html
$document = @"
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <title>Verification Report</title>
  <style>
    body { font-family: Segoe UI, Arial, sans-serif; margin: 24px; line-height: 1.45; color: #1f2328; }
    .section-tabs { position: sticky; top: 0; background: #ffffff; border-bottom: 1px solid #d8dee4; padding: 8px 0; margin-bottom: 16px; }
    .section-tabs a { display: inline-block; margin-right: 8px; padding: 6px 10px; border: 1px solid #d0d7de; border-radius: 999px; text-decoration: none; color: #0969da; font-size: 12px; }
    .section-tabs a:hover { background: #f6f8fa; }
    table { border-collapse: collapse; width: 100%; margin-bottom: 16px; }
    th, td { border: 1px solid #d0d7de; padding: 6px 8px; vertical-align: top; font-size: 13px; }
    th { background: #f6f8fa; }
    code, pre { background: #f6f8fa; }
    code { padding: 1px 4px; border-radius: 4px; }
    pre { padding: 10px; overflow-x: auto; border: 1px solid #d0d7de; border-radius: 6px; }
    h1, h2, h3, h4 { color: #0f172a; }
    @media print {
      .section-tabs { display: none; }
      h2 { break-before: page; }
      h2:first-of-type { break-before: auto; }
      body { margin: 12mm; }
    }
  </style>
</head>
<body>
  <div class="section-tabs">
    <a href="#1-change-focused-verification">Change</a>
    <a href="#2-regression-suite-verification">Regression</a>
    <a href="#3-runtime-and-smoke-verification">Runtime</a>
    <a href="#4-release-readiness">Readiness</a>
  </div>
$bodyHtml
</body>
</html>
"@
Set-Content -Path $outputPath -Value $document -Encoding utf8
'@

        & $pwshCommand.Source -NoProfile -Command $pwshScript -- $MarkdownPath $HtmlPath
        if ($LASTEXITCODE -eq 0 -and (Test-Path $HtmlPath)) {
            return [pscustomobject]@{ Success = $true; Method = 'pwsh-convertfrommarkdown'; Path = $HtmlPath }
        }
    }

    $rawMarkdown = Get-Content -Path $MarkdownPath -Raw
    $bodyHtml = Convert-MarkdownToHtmlFragmentBasic -MarkdownText $rawMarkdown
    $fallbackHtml = New-VerificationHtmlDocument -BodyHtml $bodyHtml
    Set-Content -Path $HtmlPath -Value $fallbackHtml -Encoding utf8

    [pscustomobject]@{ Success = $true; Method = 'built-in-basic-parser'; Path = $HtmlPath }
}

# Converts html report to pdf when a local converter/browser is available.
function Publish-PdfReport {
    param(
        [string]$HtmlPath,
        [string]$PdfPath
    )

    $pdfDir = Split-Path -Path $PdfPath -Parent
    if (-not (Test-Path $pdfDir)) {
        New-Item -ItemType Directory -Path $pdfDir -Force | Out-Null
    }

    $wkhtmltopdfCommand = Get-Command wkhtmltopdf -ErrorAction SilentlyContinue
    if ($wkhtmltopdfCommand) {
        & $wkhtmltopdfCommand.Source $HtmlPath $PdfPath
        if ($LASTEXITCODE -eq 0 -and (Test-Path $PdfPath)) {
            return [pscustomobject]@{ Success = $true; Method = 'wkhtmltopdf'; Path = $PdfPath }
        }
    }

    $browserCandidates = @(
        (Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe'),
        (Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'Google\Chrome\Application\chrome.exe'),
        (Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path $_) }

    $browserPath = $browserCandidates | Select-Object -First 1
    if ($browserPath) {
        $htmlUri = 'file:///' + ($HtmlPath.Replace('\\', '/'))
        $arguments = @('--headless', '--disable-gpu', '--no-pdf-header-footer', "--print-to-pdf=$PdfPath", $htmlUri)
        $process = Start-Process -FilePath $browserPath -ArgumentList $arguments -Wait -PassThru -WindowStyle Hidden
        if ($process.ExitCode -eq 0 -and (Test-Path $PdfPath)) {
            return [pscustomobject]@{ Success = $true; Method = 'headless-browser'; Path = $PdfPath }
        }
    }

    [pscustomobject]@{ Success = $false; Method = 'none'; Path = $PdfPath }
}

# Keeps only the most recent N timestamped report files so local history is useful
# without letting `target/` grow forever.
function Remove-OldTimestampedReports {
    param(
        [string]$BaseReportPath,
        [int]$KeepCount,
        [string]$ExcludePath
    )

    if ($KeepCount -lt 1) {
        return
    }

    $reportDirectory = Split-Path -Path $BaseReportPath -Parent
    $reportExtension = [System.IO.Path]::GetExtension($BaseReportPath)
    $reportBaseName = [System.IO.Path]::GetFileNameWithoutExtension($BaseReportPath)
    $pattern = $reportBaseName + '-*' + $reportExtension

    $timestampedReports = Get-ChildItem -Path $reportDirectory -Filter $pattern -File |
        Where-Object { $_.FullName -ne $ExcludePath } |
        Sort-Object LastWriteTime -Descending

    if ($timestampedReports.Count -le $KeepCount) {
        return
    }

    $reportsToRemove = $timestampedReports | Select-Object -Skip $KeepCount
    foreach ($report in $reportsToRemove) {
        Remove-Item -Path $report.FullName -Force
    }
}

# Default report artifact locations under `target/`.
$targetDir = Join-Path $RepoRoot 'target'
$testLog = Join-Path $targetDir 'verification-mvn-test.log'
$smokeCapture = Join-Path $targetDir 'verification-smoke.log'
$surefireDir = Join-Path $targetDir 'surefire-reports'
$timestampedReportPath = Get-TimestampedReportPath -BaseReportPath $ReportPath
$gitSummary = Get-GitSummary -WorkingDirectory $RepoRoot

# Step 1: run the full automated test suite.
$testRun = Invoke-MavenTestRun -WorkingDirectory $RepoRoot -CaptureFile $testLog
# Step 2: summarize Maven Surefire XML results.
$surefireSummary = Get-SurefireSummary -SurefireDirectory $surefireDir

$smokeSummary = $null
if (-not $SkipSmoke) {
    try {
        # Step 3 (optional): run scenario-level smoke verification.
        $smokeSummary = Invoke-SmokeVerification -WorkingDirectory $RepoRoot -CaptureFile $smokeCapture
    }
    catch {
        # If smoke verification itself crashes, still produce a report that clearly
        # marks smoke validation as failed and preserves whatever diagnostics we have.
        $smokeSummary = [pscustomobject]@{
            ExitCode = 1
            Passed = $false
            PositivePassed = $false
            NegativePassed = $false
            CaptureFile = $smokeCapture
            PositiveLog = (Join-Path $RepoRoot 'target\verify-customer-load.log')
            NegativeLog = (Join-Path $RepoRoot 'target\verify-csv-to-sqlserver.log')
            PositiveOutput = (Join-Path $RepoRoot 'src\main\resources\config-jobs\customer-load\output\customers.xml')
        }
        $_ | Out-File -FilePath $smokeCapture -Append -Encoding utf8
    }
}

$smokeWasSkipped = $SkipSmoke.IsPresent
# Step 4: assemble the shared verification evidence model.
$verificationEvidence = New-VerificationEvidence -RepoRoot $RepoRoot -MavenRun $testRun -SurefireSummary $surefireSummary -SmokeSummary $smokeSummary -GitSummary $gitSummary -SmokeWasSkipped $smokeWasSkipped
# Step 5: render the final human-readable report from the shared evidence model.
New-VerificationReport -Destination $ReportPath -TimestampedDestination $timestampedReportPath -Evidence $verificationEvidence

$publishPaths = Resolve-PublishArtifactPaths -MarkdownPath $ReportPath -RequestedHtmlPath $HtmlReportPath -RequestedPdfPath $PdfReportPath
$htmlPublishResult = $null
$pdfPublishResult = $null

if ($ReportPublishMode -eq 'Html' -or $ReportPublishMode -eq 'HtmlAndPdf') {
    $htmlPublishResult = Publish-HtmlReport -MarkdownPath $ReportPath -HtmlPath $publishPaths.HtmlPath
}

if ($ReportPublishMode -eq 'HtmlAndPdf') {
    if ($null -eq $htmlPublishResult -or -not $htmlPublishResult.Success) {
        $pdfPublishResult = [pscustomobject]@{ Success = $false; Method = 'html-missing'; Path = $publishPaths.PdfPath }
    }
    else {
        $pdfPublishResult = Publish-PdfReport -HtmlPath $htmlPublishResult.Path -PdfPath $publishPaths.PdfPath
    }
}

# Step 6: prune older timestamped reports and keep only a small recent history.
Remove-OldTimestampedReports -BaseReportPath $ReportPath -KeepCount $KeepLatestCount -ExcludePath $timestampedReportPath

Write-Host ''
Write-Host 'Verification report generated:' -ForegroundColor Green
Write-Host "- Latest: $ReportPath"
Write-Host "- Timestamped: $timestampedReportPath"
if ($htmlPublishResult) {
    Write-Host "- HTML: $($htmlPublishResult.Path) (method=$($htmlPublishResult.Method))"
}
if ($ReportPublishMode -eq 'HtmlAndPdf') {
    if ($pdfPublishResult -and $pdfPublishResult.Success) {
        Write-Host "- PDF: $($pdfPublishResult.Path) (method=$($pdfPublishResult.Method))"
    }
    else {
        Write-Host "- PDF: not generated (no compatible converter found)" -ForegroundColor Yellow
    }
}
Write-Host "- Retention: keeping latest $KeepLatestCount timestamped reports"








