[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$ServerName = 'localhost',
    [int]$Port = 1433,
    [string]$AdminUser = 'sa',
    [string]$AdminPassword = '',
    [string]$DatabaseName = 'etl_controlplane',
    [switch]$UseIntegratedSecurity
)

$ErrorActionPreference = 'Stop'

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$bootstrapSqlTemplate = Join-Path $repoRoot 'scripts\sql\mssql\controlplane-bootstrap.sql'
$batchMetadataSqlTemplate = Join-Path $repoRoot 'scripts\sql\mssql\spring-batch-metadata.sql'

if ($DatabaseName -notmatch '^[A-Za-z0-9_]+$') {
    throw "DatabaseName '$DatabaseName' is invalid. Use only letters, digits, and underscores."
}

if (-not (Test-Path $bootstrapSqlTemplate)) {
    throw "Bootstrap SQL file not found: $bootstrapSqlTemplate"
}

if (-not (Test-Path $batchMetadataSqlTemplate)) {
    throw "Spring Batch SQL file not found: $batchMetadataSqlTemplate"
}

$sqlcmdCommand = Get-Command sqlcmd -ErrorAction SilentlyContinue
if (-not $sqlcmdCommand) {
    throw 'sqlcmd client was not found in PATH. Install SQL Server command-line tools and retry.'
}

$serverToken = if ($Port -gt 0) { "$ServerName,$Port" } else { $ServerName }
$credentialArgs = if ($UseIntegratedSecurity) {
    '-E'
} else {
    if ([string]::IsNullOrWhiteSpace($AdminUser) -or [string]::IsNullOrWhiteSpace($AdminPassword)) {
        throw 'AdminUser and AdminPassword are required unless -UseIntegratedSecurity is used.'
    }
    "-U `"$AdminUser`" -P `"$AdminPassword`""
}

$baseArgs = "-S `"$serverToken`" $credentialArgs"

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

function Invoke-SqlCmdFile {
    param(
        [string]$SqlFilePath,
        [string]$Label
    )

    $cmd = 'sqlcmd ' + $baseArgs + ' -b -i "' + $SqlFilePath + '"'

    if ($PSCmdlet.ShouldProcess($Label, $cmd)) {
        cmd.exe /d /c $cmd
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed with exit code $LASTEXITCODE."
        }
    }
}

$bootstrapSql = New-ParameterizedSqlFile -TemplatePath $bootstrapSqlTemplate -FilePrefix 'controlplane-bootstrap-mssql'
$batchMetadataSql = New-ParameterizedSqlFile -TemplatePath $batchMetadataSqlTemplate -FilePrefix 'spring-batch-bootstrap-mssql'

try {
    Write-Host "Applying control-plane SQL Server bootstrap to database '$DatabaseName': $bootstrapSqlTemplate"
    Invoke-SqlCmdFile -SqlFilePath $bootstrapSql -Label 'control-plane schema bootstrap (mssql)'

    Write-Host "Applying Spring Batch SQL Server metadata bootstrap to database '$DatabaseName': $batchMetadataSqlTemplate"
    Invoke-SqlCmdFile -SqlFilePath $batchMetadataSql -Label 'spring-batch metadata bootstrap (mssql)'
}
finally {
    Remove-Item $bootstrapSql, $batchMetadataSql -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Control-plane SQL Server bootstrap completed.' -ForegroundColor Green
Write-Host "Validate tables with: sqlcmd -S \"$serverToken\" $credentialArgs -d \"$DatabaseName\" -Q \"SELECT name FROM sys.tables WHERE name LIKE 'controlplane_%' OR name LIKE 'BATCH_%' ORDER BY name;\""

