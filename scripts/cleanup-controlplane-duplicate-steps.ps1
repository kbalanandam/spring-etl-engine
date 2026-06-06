param(
    [string]$RepoRoot,
    [string]$DbPath,
    [ValidateSet('Audit', 'Cleanup')]
    [string]$Mode = 'Audit',
    [switch]$FailOnDuplicates,
    [switch]$SkipBackup,
    [string]$ReportPath
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

if ([string]::IsNullOrWhiteSpace($DbPath)) {
    $DbPath = Join-Path $RepoRoot '.etl-dev\etl-dev.db'
}

if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $RepoRoot 'target\controlplane-duplicate-step-report.json'
}

$dbPath = [System.IO.Path]::GetFullPath($DbPath)
$reportPath = [System.IO.Path]::GetFullPath($ReportPath)
$reportDir = Split-Path -Path $reportPath -Parent

if (-not (Test-Path $dbPath)) {
    throw "Control-plane SQLite database not found: $dbPath"
}

if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}

if ($Mode -eq 'Cleanup' -and -not $SkipBackup) {
    $backupPath = $dbPath + '.pre-step-dedupe-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.bak'
    Copy-Item -Path $dbPath -Destination $backupPath -Force
    Write-Host "Created backup: $backupPath"
}

$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCommand) {
    throw 'Python is required for SQLite maintenance but was not found in PATH.'
}

$tempScriptPath = Join-Path $reportDir 'tmp-cleanup-controlplane-duplicate-steps.py'
$pythonScript = @'
import json
import os
import sqlite3
import sys
from datetime import datetime, timezone


def table_exists(conn, table_name):
    row = conn.execute(
        "select 1 from sqlite_master where type='table' and name = ?",
        (table_name,),
    ).fetchone()
    return row is not None


def rank_rows(conn):
    sql = """
    with ranked as (
        select
            step_record_id,
            run_record_id,
            step_name,
            step_status,
            read_count,
            write_count,
            rejected_count,
            coalesce(updated_at, created_at) as freshness,
            lower(trim(step_name)) as normalized_step_name,
            (
                case when read_count is not null then 1 else 0 end +
                case when write_count is not null then 1 else 0 end +
                case when rejected_count is not null then 1 else 0 end
            ) as metric_score,
            case upper(coalesce(step_status, ''))
                when 'COMPLETED' then 3
                when 'FAILED' then 2
                when 'STARTED' then 1
                else 0
            end as status_score,
            row_number() over (
                partition by run_record_id, lower(trim(step_name))
                order by
                    (
                        case when read_count is not null then 1 else 0 end +
                        case when write_count is not null then 1 else 0 end +
                        case when rejected_count is not null then 1 else 0 end
                    ) desc,
                    case upper(coalesce(step_status, ''))
                        when 'COMPLETED' then 3
                        when 'FAILED' then 2
                        when 'STARTED' then 1
                        else 0
                    end desc,
                    coalesce(updated_at, created_at) desc,
                    step_record_id desc
            ) as rank_in_group,
            count(*) over (
                partition by run_record_id, lower(trim(step_name))
            ) as group_count
        from controlplane_step_record
        where trim(coalesce(step_name, '')) <> ''
    )
    select
        step_record_id,
        run_record_id,
        step_name,
        step_status,
        read_count,
        write_count,
        rejected_count,
        normalized_step_name,
        rank_in_group,
        group_count
    from ranked
    where group_count > 1
    order by run_record_id, normalized_step_name, rank_in_group
    """
    return conn.execute(sql).fetchall()


def build_groups(rows):
    groups = {}
    for row in rows:
        key = (row[1], row[7])
        bucket = groups.setdefault(key, {
            "runRecordId": row[1],
            "stepName": row[2],
            "normalizedStepName": row[7],
            "keeperStepRecordId": "",
            "dropStepRecordIds": [],
            "rows": [],
        })
        item = {
            "stepRecordId": row[0],
            "stepStatus": row[3],
            "readCount": row[4],
            "writeCount": row[5],
            "rejectedCount": row[6],
            "rank": row[8],
            "groupCount": row[9],
        }
        bucket["rows"].append(item)
        if row[8] == 1:
            bucket["keeperStepRecordId"] = row[0]
        else:
            bucket["dropStepRecordIds"].append(row[0])
    return list(groups.values())


def cleanup_groups(conn, groups):
    deleted = 0
    artifact_updates = 0
    anchor_updates = 0

    artifact_exists = table_exists(conn, "controlplane_artifact_record")
    anchor_exists = table_exists(conn, "controlplane_checkpoint_anchor")

    for group in groups:
        keeper = group["keeperStepRecordId"]
        for duplicate in group["dropStepRecordIds"]:
            if artifact_exists:
                result = conn.execute(
                    """
                    update controlplane_artifact_record
                    set step_record_id = ?
                    where step_record_id = ?
                    """,
                    (keeper, duplicate),
                )
                artifact_updates += result.rowcount

            if anchor_exists:
                result = conn.execute(
                    """
                    update controlplane_checkpoint_anchor
                    set step_record_id = ?
                    where step_record_id = ?
                    """,
                    (keeper, duplicate),
                )
                anchor_updates += result.rowcount

            result = conn.execute(
                "delete from controlplane_step_record where step_record_id = ?",
                (duplicate,),
            )
            deleted += result.rowcount

    return {
        "deletedStepRows": deleted,
        "updatedArtifactRows": artifact_updates,
        "updatedCheckpointAnchorRows": anchor_updates,
    }


def main(argv):
    db_path = os.path.abspath(argv[1])
    mode = argv[2]
    report_path = os.path.abspath(argv[3])
    fail_on_duplicates = argv[4].lower() == "true"

    os.makedirs(os.path.dirname(report_path), exist_ok=True)

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    try:
        if not table_exists(conn, "controlplane_step_record"):
            report = {
                "databasePath": db_path,
                "mode": mode,
                "timestampUtc": datetime.now(timezone.utc).isoformat(),
                "status": "step-table-missing",
                "duplicateGroupCount": 0,
                "duplicateRowCount": 0,
                "groups": [],
            }
            with open(report_path, "w", encoding="utf-8") as handle:
                json.dump(report, handle, indent=2)
            print(json.dumps(report))
            return 0

        duplicate_rows = rank_rows(conn)
        groups = build_groups(duplicate_rows)

        summary = {
            "databasePath": db_path,
            "mode": mode,
            "timestampUtc": datetime.now(timezone.utc).isoformat(),
            "duplicateGroupCount": len(groups),
            "duplicateRowCount": sum(len(group["dropStepRecordIds"]) for group in groups),
            "groups": groups,
            "cleanup": {
                "deletedStepRows": 0,
                "updatedArtifactRows": 0,
                "updatedCheckpointAnchorRows": 0,
            },
        }

        if mode == "Cleanup" and groups:
            try:
                conn.execute("begin")
                summary["cleanup"] = cleanup_groups(conn, groups)
                conn.commit()
            except Exception:
                conn.rollback()
                raise

            post_rows = rank_rows(conn)
            post_groups = build_groups(post_rows)
            summary["postCleanupDuplicateGroupCount"] = len(post_groups)
            summary["postCleanupDuplicateRowCount"] = sum(len(group["dropStepRecordIds"]) for group in post_groups)

        with open(report_path, "w", encoding="utf-8") as handle:
            json.dump(summary, handle, indent=2)

        print(json.dumps({
            "mode": summary["mode"],
            "duplicateGroupCount": summary["duplicateGroupCount"],
            "duplicateRowCount": summary["duplicateRowCount"],
            "cleanup": summary["cleanup"],
            "postCleanupDuplicateGroupCount": summary.get("postCleanupDuplicateGroupCount", summary["duplicateGroupCount"]),
            "postCleanupDuplicateRowCount": summary.get("postCleanupDuplicateRowCount", summary["duplicateRowCount"]),
            "reportPath": report_path,
        }))

        if fail_on_duplicates and summary["duplicateGroupCount"] > 0:
            return 2
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
'@

Set-Content -Path $tempScriptPath -Value $pythonScript -Encoding utf8
try {
    & $pythonCommand.Source $tempScriptPath $dbPath $Mode $reportPath $FailOnDuplicates.IsPresent
    if ($LASTEXITCODE -ne 0) {
        throw "Duplicate-step maintenance returned exit code $LASTEXITCODE"
    }
}
finally {
    if (Test-Path $tempScriptPath) {
        Remove-Item -Path $tempScriptPath -Force
    }
}

Write-Host "Duplicate-step maintenance complete. Report: $reportPath"

