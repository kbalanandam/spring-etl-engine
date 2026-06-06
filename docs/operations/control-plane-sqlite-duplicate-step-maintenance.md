# Control-plane SQLite duplicate step maintenance

## Purpose

This runbook defines how to clean existing duplicate step rows in the shared local control-plane SQLite database and how to detect future recurrences.

## Scope

- Database: `.etl-dev/etl-dev.db`
- Table: `controlplane_step_record`
- Related references updated during cleanup:
  - `controlplane_artifact_record.step_record_id`
  - `controlplane_checkpoint_anchor.step_record_id`

## Why this exists

Some historical runs can contain more than one persisted row for the same logical step name under the same `run_record_id`.

The operator UI now deduplicates for display, but this script is the source-of-truth hygiene action that cleans retained history itself.

## Commands

Audit only:

```powershell
Set-Location C:\spring-etl-engine
powershell.exe -ExecutionPolicy Bypass -File .\scripts\cleanup-controlplane-duplicate-steps.ps1 -Mode Audit
```

Cleanup (creates backup by default):

```powershell
Set-Location C:\spring-etl-engine
powershell.exe -ExecutionPolicy Bypass -File .\scripts\cleanup-controlplane-duplicate-steps.ps1 -Mode Cleanup
```

Guardrail mode for scheduled checks:

```powershell
Set-Location C:\spring-etl-engine
powershell.exe -ExecutionPolicy Bypass -File .\scripts\cleanup-controlplane-duplicate-steps.ps1 -Mode Audit -FailOnDuplicates
```

## Evidence

The script writes a JSON report to:

- `target/controlplane-duplicate-step-report.json`

Use this report to track:

- `duplicateGroupCount`
- `duplicateRowCount`
- cleanup rewires/deletions
- post-cleanup duplicate counts

## Safety notes

- Cleanup mode creates a timestamped backup unless `-SkipBackup` is supplied.
- Prefer running cleanup when the app is stopped to avoid SQLite writer lock contention.

