[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$HostName = 'localhost',
    [int]$Port = 3306,
    [string]$RootUser = 'root',
    [string]$RootPassword = '',
    [string]$DatabaseName = 'etl_controlplane',
    [switch]$ApplyGrants,
    [string]$AppUser = 'etl_app',
    [string]$AppHost = '%',
    [string]$AppPassword = ''
)

$ErrorActionPreference = 'Stop'

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$bootstrapSqlTemplate = Join-Path $repoRoot 'scripts\sql\mysql\controlplane-bootstrap.sql'
$batchMetadataSqlTemplate = Join-Path $repoRoot 'scripts\sql\mysql\spring-batch-metadata.sql'

if ($DatabaseName -notmatch '^[A-Za-z0-9_]+$') {
    throw "DatabaseName '$DatabaseName' is invalid. Use only letters, digits, and underscores."
}

if (-not (Test-Path $bootstrapSqlTemplate)) {
    throw "Bootstrap SQL file not found: $bootstrapSqlTemplate"
}

if (-not (Test-Path $batchMetadataSqlTemplate)) {
    throw "Spring Batch SQL file not found: $batchMetadataSqlTemplate"
}

$mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysqlCommand) {
    throw 'mysql client was not found in PATH. Install MySQL client tools and retry.'
}

$credentialArg = if ([string]::IsNullOrWhiteSpace($RootPassword)) {
    "-u$RootUser"
} else {
    "-u$RootUser -p$RootPassword"
}

$baseArgs = "--protocol=TCP -h $HostName -P $Port $credentialArg"

function New-ParameterizedSqlFile {
    param(
        [string]$TemplatePath,
        [string]$FilePrefix
    )

    $template = Get-Content -Raw -Encoding UTF8 $TemplatePath
    $resolved = $template.Replace('{{CONTROLPLANE_DATABASE_NAME}}', $DatabaseName)
    $tmpSql = Join-Path $env:TEMP ($FilePrefix + '-' + [guid]::NewGuid().ToString('N') + '.sql')
    Set-Content -Path $tmpSql -Value $resolved -Encoding utf8
    return $tmpSql
}

function Invoke-MySqlFile {
    param(
        [string]$SqlFilePath,
        [string]$Label
    )

    $cmd = 'mysql ' + $baseArgs + ' < "' + $SqlFilePath + '"'

    if ($PSCmdlet.ShouldProcess($Label, $cmd)) {
        cmd.exe /d /c $cmd
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed with exit code $LASTEXITCODE."
        }
    }
}

$bootstrapSql = New-ParameterizedSqlFile -TemplatePath $bootstrapSqlTemplate -FilePrefix 'controlplane-bootstrap'
$batchMetadataSql = New-ParameterizedSqlFile -TemplatePath $batchMetadataSqlTemplate -FilePrefix 'spring-batch-bootstrap'

try {
    Write-Host "Applying control-plane MySQL bootstrap to database '$DatabaseName': $bootstrapSqlTemplate"
    Invoke-MySqlFile -SqlFilePath $bootstrapSql -Label 'control-plane schema bootstrap'

    Write-Host "Applying Spring Batch MySQL metadata bootstrap to database '$DatabaseName': $batchMetadataSqlTemplate"
    Invoke-MySqlFile -SqlFilePath $batchMetadataSql -Label 'spring-batch metadata bootstrap'

    if ($ApplyGrants) {
        if ([string]::IsNullOrWhiteSpace($AppUser)) {
            throw 'AppUser is required when -ApplyGrants is used.'
        }
        if ([string]::IsNullOrWhiteSpace($AppPassword)) {
            throw 'AppPassword is required when -ApplyGrants is used.'
        }

        $escapedUser = $AppUser.Replace("'", "''")
        $escapedHost = $AppHost.Replace("'", "''")
        $escapedPassword = $AppPassword.Replace("'", "''")

        $tmpGrantSql = Join-Path $env:TEMP ("controlplane-grants-" + [guid]::NewGuid().ToString('N') + '.sql')
        try {
            $grantStatements = @(
                "CREATE USER IF NOT EXISTS '$escapedUser'@'$escapedHost' IDENTIFIED BY '$escapedPassword';",
                "USE ``$DatabaseName``;",
                "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP ON ``$DatabaseName``.* TO '$escapedUser'@'$escapedHost';",
                'FLUSH PRIVILEGES;'
            )
            Set-Content -Path $tmpGrantSql -Value ($grantStatements -join [Environment]::NewLine) -Encoding utf8

            Write-Host "Applying control-plane MySQL grants for '$AppUser'@'$AppHost' on database '$DatabaseName'"
            Invoke-MySqlFile -SqlFilePath $tmpGrantSql -Label 'control-plane grants bootstrap'
        }
        finally {
            Remove-Item $tmpGrantSql -Force -ErrorAction SilentlyContinue
        }
    }
}
finally {
    Remove-Item $bootstrapSql, $batchMetadataSql -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Control-plane MySQL bootstrap completed.' -ForegroundColor Green
Write-Host "Validate tables with: USE \\`$DatabaseName\\`; SHOW TABLES LIKE 'controlplane_%'; SHOW TABLES LIKE 'BATCH_%';"






