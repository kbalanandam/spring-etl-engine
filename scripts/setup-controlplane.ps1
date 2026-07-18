[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('mysql', 'mssql', 'sqlserver')]
    [string]$Vendor,
    [string]$ServerName = 'localhost',
    [int]$Port = 0,
    [string]$DatabaseName = 'etl_controlplane',
    [string]$Username = '',
    [string]$Password = '',
    [switch]$UseIntegratedSecurity,
    [switch]$ApplyGrants,
    [string]$AppUser = 'etl_app',
    [string]$AppHost = '%',
    [string]$AppPassword = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

function Resolve-VendorName {
    param([string]$ConfiguredVendor)

    $normalized = ''
    if ($null -ne $ConfiguredVendor) {
        $normalized = $ConfiguredVendor.Trim().ToLowerInvariant()
    }
    switch ($normalized) {
        'mysql' { return 'mysql' }
        'mssql' { return 'mssql' }
        'sqlserver' { return 'mssql' }
        default { throw "Unsupported vendor '$ConfiguredVendor'. Use mysql or mssql." }
    }
}

function Resolve-DefaultPort {
    param(
        [string]$NormalizedVendor,
        [int]$ConfiguredPort
    )

    if ($ConfiguredPort -gt 0) {
        return $ConfiguredPort
    }

    switch ($NormalizedVendor) {
        'mysql' { return 3306 }
        'mssql' { return 1433 }
        default { throw "No default port defined for vendor '$NormalizedVendor'." }
    }
}

function Resolve-TemplatePath {
    param(
        [string]$NormalizedVendor,
        [string]$FileName
    )

    $templatePath = Join-Path $repoRoot ("scripts\sql\$NormalizedVendor\$FileName")
    if (-not (Test-Path $templatePath)) {
        throw "SQL file not found: $templatePath"
    }
    return $templatePath
}

function Assert-DatabaseName {
    param([string]$ResolvedDatabaseName)

    if ($ResolvedDatabaseName -notmatch '^[A-Za-z0-9_]+$') {
        throw "DatabaseName '$ResolvedDatabaseName' is invalid. Use only letters, digits, and underscores."
    }
}

function Assert-ClientAvailable {
    param([string]$NormalizedVendor)

    $clientName = if ($NormalizedVendor -eq 'mysql') { 'mysql' } else { 'sqlcmd' }
    if (-not (Get-Command $clientName -ErrorAction SilentlyContinue)) {
        throw "$clientName client was not found in PATH. Install the required client tools and retry."
    }
}

function New-ParameterizedSqlFile {
    param(
        [string]$TemplatePath,
        [string]$FilePrefix,
        [string]$ResolvedDatabaseName
    )

    $template = Get-Content -Raw -Encoding UTF8 $TemplatePath
    $resolved = $template.Replace('{{CONTROLPLANE_DATABASE_NAME}}', $ResolvedDatabaseName)
    $tmpSql = Join-Path $env:TEMP ($FilePrefix + '-' + [guid]::NewGuid().ToString('N') + '.sql')
    Set-Content -Path $tmpSql -Value $resolved -Encoding utf8
    return $tmpSql
}

function Invoke-MySqlFile {
    param(
        [string]$TargetServer,
        [int]$ResolvedPort,
        [string]$ResolvedUsername,
        [string]$ResolvedPassword,
        [string]$SqlFilePath,
        [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($ResolvedUsername)) {
        throw 'Username is required for MySQL bootstrap.'
    }

    $credentialArg = if ([string]::IsNullOrWhiteSpace($ResolvedPassword)) {
        "-u$ResolvedUsername"
    } else {
        "-u$ResolvedUsername -p$ResolvedPassword"
    }

    $cmd = 'mysql --protocol=TCP -h ' + $TargetServer + ' -P ' + $ResolvedPort + ' ' + $credentialArg + ' < "' + $SqlFilePath + '"'
    if ($PSCmdlet.ShouldProcess($Label, $cmd)) {
        cmd.exe /d /c $cmd
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed with exit code $LASTEXITCODE."
        }
    }
}

function Invoke-SqlCmdFile {
    param(
        [string]$TargetServer,
        [int]$ResolvedPort,
        [string]$ResolvedUsername,
        [string]$ResolvedPassword,
        [switch]$IntegratedSecurity,
        [string]$SqlFilePath,
        [string]$Label
    )

    $serverToken = if ($ResolvedPort -gt 0) { "$TargetServer,$ResolvedPort" } else { $TargetServer }
    $credentialArgs = if ($IntegratedSecurity) {
        '-E'
    } else {
        if ([string]::IsNullOrWhiteSpace($ResolvedUsername) -or [string]::IsNullOrWhiteSpace($ResolvedPassword)) {
            throw 'Username and Password are required for SQL Server bootstrap unless -UseIntegratedSecurity is used.'
        }
        "-U `"$ResolvedUsername`" -P `"$ResolvedPassword`""
    }

    $cmd = 'sqlcmd -S "' + $serverToken + '" ' + $credentialArgs + ' -b -i "' + $SqlFilePath + '"'
    if ($PSCmdlet.ShouldProcess($Label, $cmd)) {
        cmd.exe /d /c $cmd
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed with exit code $LASTEXITCODE."
        }
    }
}

function Invoke-MySqlGrants {
    param(
        [string]$TargetServer,
        [int]$ResolvedPort,
        [string]$ResolvedUsername,
        [string]$ResolvedPassword,
        [string]$ResolvedDatabaseName,
        [string]$ResolvedAppUser,
        [string]$ResolvedAppHost,
        [string]$ResolvedAppPassword
    )

    if (-not $ApplyGrants) {
        return
    }

    if ([string]::IsNullOrWhiteSpace($ResolvedAppUser)) {
        throw 'AppUser is required when -ApplyGrants is used.'
    }
    if ([string]::IsNullOrWhiteSpace($ResolvedAppPassword)) {
        throw 'AppPassword is required when -ApplyGrants is used.'
    }

    $escapedUser = $ResolvedAppUser.Replace("'", "''")
    $escapedHost = $ResolvedAppHost.Replace("'", "''")
    $escapedPassword = $ResolvedAppPassword.Replace("'", "''")

    $tmpGrantSql = Join-Path $env:TEMP ("controlplane-grants-" + [guid]::NewGuid().ToString('N') + '.sql')
    try {
        $grantStatements = @(
            "CREATE USER IF NOT EXISTS '$escapedUser'@'$escapedHost' IDENTIFIED BY '$escapedPassword';",
            "USE ``$ResolvedDatabaseName``;",
            "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP ON ``$ResolvedDatabaseName``.* TO '$escapedUser'@'$escapedHost';",
            'FLUSH PRIVILEGES;'
        )
        Set-Content -Path $tmpGrantSql -Value ($grantStatements -join [Environment]::NewLine) -Encoding utf8

        Write-Host "Applying control-plane MySQL grants for '$ResolvedAppUser'@'$ResolvedAppHost' on database '$ResolvedDatabaseName'"
        Invoke-MySqlFile -TargetServer $TargetServer -ResolvedPort $ResolvedPort -ResolvedUsername $ResolvedUsername -ResolvedPassword $ResolvedPassword -SqlFilePath $tmpGrantSql -Label 'control-plane grants bootstrap'
    }
    finally {
        Remove-Item $tmpGrantSql -Force -ErrorAction SilentlyContinue
    }
}

function Write-ValidationHint {
    param(
        [string]$NormalizedVendor,
        [string]$TargetServer,
        [int]$ResolvedPort,
        [string]$ResolvedUsername,
        [string]$ResolvedPassword,
        [switch]$IntegratedSecurity,
        [string]$ResolvedDatabaseName
    )

    Write-Host ''
    if ($NormalizedVendor -eq 'mysql') {
        Write-Host 'Control-plane MySQL bootstrap completed.' -ForegroundColor Green
        Write-Host ('Validate tables with: USE `{0}`; SHOW TABLES LIKE ''controlplane_%''; SHOW TABLES LIKE ''BATCH_%'';' -f $ResolvedDatabaseName)
        return
    }

    $serverToken = if ($ResolvedPort -gt 0) { "$TargetServer,$ResolvedPort" } else { $TargetServer }
    $credentialArgs = if ($IntegratedSecurity) {
        '-E'
    } else {
        "-U `"$ResolvedUsername`" -P `"$ResolvedPassword`""
    }
    $validationSql = "SELECT name FROM sys.tables WHERE name LIKE 'controlplane_%' OR name LIKE 'BATCH_%' ORDER BY name;"
    Write-Host 'Control-plane SQL Server bootstrap completed.' -ForegroundColor Green
    Write-Host ('Validate tables with: sqlcmd -S "{0}" {1} -d "{2}" -Q "{3}"' -f $serverToken, $credentialArgs, $ResolvedDatabaseName, $validationSql)
}

$normalizedVendor = Resolve-VendorName -ConfiguredVendor $Vendor
$resolvedPort = Resolve-DefaultPort -NormalizedVendor $normalizedVendor -ConfiguredPort $Port
Assert-DatabaseName -ResolvedDatabaseName $DatabaseName
Assert-ClientAvailable -NormalizedVendor $normalizedVendor

Write-Host "Safety: setup-controlplane is additive and rerun-safe; it does not drop/truncate/delete existing control-plane run history." -ForegroundColor Cyan

$bootstrapSqlTemplate = Resolve-TemplatePath -NormalizedVendor $normalizedVendor -FileName 'controlplane-bootstrap.sql'
$batchMetadataSqlTemplate = Resolve-TemplatePath -NormalizedVendor $normalizedVendor -FileName 'spring-batch-metadata.sql'
$bootstrapSql = New-ParameterizedSqlFile -TemplatePath $bootstrapSqlTemplate -FilePrefix ("controlplane-bootstrap-" + $normalizedVendor) -ResolvedDatabaseName $DatabaseName
$batchMetadataSql = New-ParameterizedSqlFile -TemplatePath $batchMetadataSqlTemplate -FilePrefix ("spring-batch-bootstrap-" + $normalizedVendor) -ResolvedDatabaseName $DatabaseName

try {
    if ($normalizedVendor -eq 'mysql') {
        Write-Host "Applying control-plane MySQL bootstrap to database '$DatabaseName': $bootstrapSqlTemplate"
        Invoke-MySqlFile -TargetServer $ServerName -ResolvedPort $resolvedPort -ResolvedUsername $Username -ResolvedPassword $Password -SqlFilePath $bootstrapSql -Label 'control-plane schema bootstrap (mysql)'

        Write-Host "Applying Spring Batch MySQL metadata bootstrap to database '$DatabaseName': $batchMetadataSqlTemplate"
        Invoke-MySqlFile -TargetServer $ServerName -ResolvedPort $resolvedPort -ResolvedUsername $Username -ResolvedPassword $Password -SqlFilePath $batchMetadataSql -Label 'spring-batch metadata bootstrap (mysql)'

        Invoke-MySqlGrants -TargetServer $ServerName -ResolvedPort $resolvedPort -ResolvedUsername $Username -ResolvedPassword $Password -ResolvedDatabaseName $DatabaseName -ResolvedAppUser $AppUser -ResolvedAppHost $AppHost -ResolvedAppPassword $AppPassword
    } else {
        Write-Host "Applying control-plane SQL Server bootstrap to database '$DatabaseName': $bootstrapSqlTemplate"
        Invoke-SqlCmdFile -TargetServer $ServerName -ResolvedPort $resolvedPort -ResolvedUsername $Username -ResolvedPassword $Password -IntegratedSecurity:$UseIntegratedSecurity -SqlFilePath $bootstrapSql -Label 'control-plane schema bootstrap (mssql)'

        Write-Host "Applying Spring Batch SQL Server metadata bootstrap to database '$DatabaseName': $batchMetadataSqlTemplate"
        Invoke-SqlCmdFile -TargetServer $ServerName -ResolvedPort $resolvedPort -ResolvedUsername $Username -ResolvedPassword $Password -IntegratedSecurity:$UseIntegratedSecurity -SqlFilePath $batchMetadataSql -Label 'spring-batch metadata bootstrap (mssql)'
    }
}
finally {
    Remove-Item $bootstrapSql, $batchMetadataSql -Force -ErrorAction SilentlyContinue
}

Write-ValidationHint -NormalizedVendor $normalizedVendor -TargetServer $ServerName -ResolvedPort $resolvedPort -ResolvedUsername $Username -ResolvedPassword $Password -IntegratedSecurity:$UseIntegratedSecurity -ResolvedDatabaseName $DatabaseName






