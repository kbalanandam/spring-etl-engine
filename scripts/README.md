# Scripts

Automation helpers under `scripts/` for local verification, cleanup, project-board sync, and explicit job execution.

## Quick Pick

- Generate local verification report (`mvn test` + smoke + markdown report): `generate-verification-report.ps1`
- Run smoke-only verification checks: `verify-recent-changes.ps1`
- Migrate legacy control-plane SQLite tables into the shared dev database: `migrate-controlplane-sqlite-to-shared.ps1`
- Audit and clean duplicate control-plane step rows in SQLite: `cleanup-controlplane-duplicate-steps.ps1`
- Remove one job bundle and matching generated artifacts safely: `remove-job-bundle.ps1`
- Sync product backlog execution board to GitHub Project V2: `sync_project_board.py`
- Prepare/run one explicit job config on Windows: `job-runner.ps1`
- Prepare/run one explicit job config on Linux/macOS: `job-runner.sh`

## `generate-verification-report.ps1`

Purpose:
- Runs full Maven tests
- Parses Surefire XML results
- Optionally runs smoke verification
- Writes `target/verification-report.md` and timestamped history copies

Common usage:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-verification-report.ps1
```

Skip smoke:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-verification-report.ps1 -SkipSmoke
```

## `verify-recent-changes.ps1`

Purpose:
- Positive smoke: `customer-load` must complete
- Negative smoke: `csv-to-sqlserver` must fail fast on placeholder SQL Server values

Usage:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-recent-changes.ps1
```

## `migrate-controlplane-sqlite-to-shared.ps1`

Purpose:
- Copies legacy `controlplane_*` tables from `.controlplane/controlplane.db`
- Merges them into the shared `.etl-dev/etl-dev.db`
- Creates a timestamped backup of the target DB before migration unless `-SkipBackup` is used

Usage:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\migrate-controlplane-sqlite-to-shared.ps1
```

Custom source/target paths:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\migrate-controlplane-sqlite-to-shared.ps1 -SourceDbPath .\.controlplane\controlplane.db -TargetDbPath .\.etl-dev\etl-dev.db
```

## `cleanup-controlplane-duplicate-steps.ps1`

Purpose:
- Audits duplicate `controlplane_step_record` rows grouped by `(run_record_id, step_name)`
- Cleanup mode keeps one canonical row per group, rewires `artifact_record` / `checkpoint_anchor` references, then deletes duplicate step rows
- Writes a JSON report under `target/` for evidence and repeatable tracking

Audit only:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\cleanup-controlplane-duplicate-steps.ps1 -Mode Audit
```

Cleanup with backup:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\cleanup-controlplane-duplicate-steps.ps1 -Mode Cleanup
```

CI/nightly guardrail (non-zero exit when duplicates are found):

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\cleanup-controlplane-duplicate-steps.ps1 -Mode Audit -FailOnDuplicates
```

## `remove-job-bundle.ps1`

Purpose:
- Deletes selected job bundle root
- Deletes matching generated sources/classes
- Applies safety checks for shared bundles and shared package usage

Preview first:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\remove-job-bundle.ps1 -JobConfigPath .\private-jobs\local-verification\your-job\config\job-config.yaml -WhatIf
```

## `sync_project_board.py`

Purpose:
- Parses `docs/product/product-backlog.md` execution-board table
- Creates/updates mapped items/fields in GitHub Project V2

Dry-run parse only:

```powershell
Set-Location (Resolve-Path ..)
python .\scripts\sync_project_board.py --dry-run
```

## `job-runner.ps1` (Windows)

Purpose:
- `prepare`: generate and compile job-scoped model classes
- `run`: run selected `etl.config.job`
- `both`: prepare then run

Usage:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\job-runner.ps1 -Action prepare -JobConfigPath tmp-test-config/customer-load-reject-demo/job-config.yaml
powershell.exe -ExecutionPolicy Bypass -File .\scripts\job-runner.ps1 -Action run -JobConfigPath tmp-test-config/customer-load-reject-demo/job-config.yaml
```

## `job-runner.sh` (Linux/macOS)

Purpose:
- Same flow as Windows helper: `prepare|run|both`

Usage:

```bash
chmod +x ./scripts/job-runner.sh
./scripts/job-runner.sh prepare tmp-test-config/customer-load-reject-demo/job-config.yaml
./scripts/job-runner.sh run tmp-test-config/customer-load-reject-demo/job-config.yaml
```

## Tests

- `scripts/tests/test_sync_project_board.py`: tests for `sync_project_board.py`

