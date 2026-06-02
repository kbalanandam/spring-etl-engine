param(
    [string]$RepoRoot,
    [string]$SourceDbPath,
    [string]$TargetDbPath,
    [switch]$SkipBackup
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

if ([string]::IsNullOrWhiteSpace($SourceDbPath)) {
    $SourceDbPath = Join-Path $RepoRoot '.controlplane\controlplane.db'
}

if ([string]::IsNullOrWhiteSpace($TargetDbPath)) {
    $TargetDbPath = Join-Path $RepoRoot '.etl-dev\etl-dev.db'
}

$sourceDbPath = [System.IO.Path]::GetFullPath($SourceDbPath)
$targetDbPath = [System.IO.Path]::GetFullPath($TargetDbPath)
$targetDir = Split-Path -Path $targetDbPath -Parent

if (-not (Test-Path $sourceDbPath)) {
    throw "Source control-plane database not found: $sourceDbPath"
}

if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

if ((Test-Path $targetDbPath) -and -not $SkipBackup) {
    $backupPath = $targetDbPath + '.pre-controlplane-migration-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.bak'
    Copy-Item -Path $targetDbPath -Destination $backupPath -Force
    Write-Host "Created backup: $backupPath"
}

$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCommand) {
    throw 'Python is required for SQLite migration but was not found in PATH.'
}

$tempScriptPath = Join-Path $targetDir 'tmp-migrate-controlplane-sqlite.py'
$pythonScript = @'
import os
import shutil
import sqlite3
import sys

source_db = os.path.abspath(sys.argv[1])
target_db = os.path.abspath(sys.argv[2])

CONTROLPLANE_TABLES = [
    "controlplane_schedule",
    "controlplane_trigger_event",
    "controlplane_run_summary",
    "controlplane_run_record",
]


def table_exists(conn, table_name):
    row = conn.execute(
        "select 1 from sqlite_master where type='table' and name = ?",
        (table_name,),
    ).fetchone()
    return row is not None


def list_columns(conn, table_name):
    return [row[1] for row in conn.execute(f"pragma table_info({table_name})").fetchall()]


def ensure_target_table_from_source(src_conn, dst_conn, table_name):
    if table_exists(dst_conn, table_name):
        return False
    create_row = src_conn.execute(
        "select sql from sqlite_master where type='table' and name = ?",
        (table_name,),
    ).fetchone()
    if create_row is None or not create_row[0]:
        raise RuntimeError(f"Could not find CREATE TABLE sql for {table_name} in source database")
    dst_conn.execute(create_row[0])
    index_rows = src_conn.execute(
        "select sql from sqlite_master where type='index' and tbl_name = ? and sql is not null order by name",
        (table_name,),
    ).fetchall()
    for (index_sql,) in index_rows:
        dst_conn.execute(index_sql)
    return True


def copy_table_rows(src_conn, dst_conn, table_name):
    if not table_exists(src_conn, table_name):
        return {"status": "missing-source", "rows": 0}

    created = ensure_target_table_from_source(src_conn, dst_conn, table_name)
    source_columns = list_columns(src_conn, table_name)
    target_columns = list_columns(dst_conn, table_name)
    common_columns = [column for column in source_columns if column in target_columns]
    if not common_columns:
        return {"status": "no-common-columns", "rows": 0, "created": created}

    select_sql = f"select {', '.join(common_columns)} from {table_name}"
    rows = src_conn.execute(select_sql).fetchall()
    if not rows:
        return {"status": "ok", "rows": 0, "created": created, "columns": common_columns}

    placeholders = ", ".join(["?"] * len(common_columns))
    insert_sql = f"insert or ignore into {table_name} ({', '.join(common_columns)}) values ({placeholders})"
    dst_conn.executemany(insert_sql, rows)
    return {"status": "ok", "rows": len(rows), "created": created, "columns": common_columns}


if not os.path.exists(source_db):
    raise SystemExit(f"Source database does not exist: {source_db}")

os.makedirs(os.path.dirname(target_db), exist_ok=True)
if not os.path.exists(target_db):
    sqlite3.connect(target_db).close()

source_conn = sqlite3.connect(source_db)
target_conn = sqlite3.connect(target_db)
try:
    target_conn.execute("pragma foreign_keys = off")
    migration_report = []
    for table_name in CONTROLPLANE_TABLES:
        result = copy_table_rows(source_conn, target_conn, table_name)
        migration_report.append((table_name, result))
    target_conn.commit()
finally:
    target_conn.close()
    source_conn.close()

for table_name, result in migration_report:
    print(f"{table_name}: {result}")
'@

Set-Content -Path $tempScriptPath -Value $pythonScript -Encoding utf8
try {
    & $pythonCommand.Source $tempScriptPath $sourceDbPath $targetDbPath
    if ($LASTEXITCODE -ne 0) {
        throw "SQLite migration failed with exit code $LASTEXITCODE"
    }
}
finally {
    if (Test-Path $tempScriptPath) {
        Remove-Item -Path $tempScriptPath -Force
    }
}

Write-Host "Migration complete. Shared DB: $targetDbPath"

