# Scripts

Automation helpers under `scripts/` for local verification, cleanup, project-board sync, and explicit job execution.

## Quick Pick

- Generate local verification report (`mvn test` + smoke + markdown report): `generate-verification-report.ps1`
- Run smoke-only verification checks: `verify-recent-changes.ps1`
- Bootstrap control-plane schema/tables/metadata for supported RDBMS with one common contract: `setup-controlplane.ps1`
- Remove one job bundle and matching generated artifacts safely: `remove-job-bundle.ps1`
- Restart/start/stop/status control-plane quickly on port 8081: `restart-controlplane.ps1`
- Generate job-scoped model classes for all job configs under folder roots: `generate-models-batch.ps1`
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

Timeout-safe usage (bounded Maven + smoke):

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-verification-report.ps1 -MavenTimeoutMinutes 45 -SmokeTimeoutMinutes 20
```

## `verify-recent-changes.ps1`

Purpose:
- Positive smoke: `customer-load` must complete
- Negative smoke: `csv-to-sqlserver` must emit runtime failure evidence (`RUN_SUMMARY status=FAILED`, `JOB_FAILURE`)
- Trigger-now evidence: controller tests must emit `CONTROLPLANE_TRIGGER` logs for requested/accepted/duplicate decisions (job + schedule scopes)
- Uses an isolated smoke metadata DB at `target/verify-smoke/etl-dev-smoke.mv.db` (does not reuse control-plane runtime databases)

Key artifacts:
- `target/verify-customer-load.log`
- `target/verify-csv-to-sqlserver.log`
- `target/verify-trigger-now.log`

Usage:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-recent-changes.ps1
```

Bound each scenario run with a timeout:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-recent-changes.ps1 -ScenarioTimeoutMinutes 20
```

## `setup-controlplane.ps1`

Purpose:
- Single supported setup entrypoint for supported control-plane relational engines
- Creates active `controlplane_*` schema tables and indexes
- Creates Spring Batch `BATCH_*` metadata tables in the same database
- Seeds `controlplane_trigger_source` master rows
- Keeps the user contract stable across vendors: server, port, database, username, password
- MySQL-only grants remain available as an optional branch
- Safety behavior: rerun-safe and non-destructive for existing control-plane run history (no table/database drop and no truncate/delete of run data)

Use this script for both MySQL and SQL Server. No vendor-specific setup wrappers are required.

Important safety note:

- `setup-controlplane.ps1` is non-destructive for existing run history data.
- Re-running setup does not drop tables/databases and does not truncate/delete existing control-plane rows.
- Setup only creates missing objects, ensures indexes, and refreshes seed rows in `controlplane_trigger_source`.

Common MySQL usage:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\setup-controlplane.ps1 -Vendor mysql -ServerName localhost -Port 3306 -DatabaseName etl_controlplane -Username root -Password "<root-password>"
```

Common SQL Server usage:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\setup-controlplane.ps1 -Vendor mssql -ServerName localhost -Port 1433 -DatabaseName etl_controlplane -Username sa -Password "<sa-password>"
```

SQL Server integrated security:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\setup-controlplane.ps1 -Vendor mssql -ServerName localhost -Port 1433 -DatabaseName etl_controlplane -UseIntegratedSecurity
```

MySQL grants for app user:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\setup-controlplane.ps1 -Vendor mysql -ServerName localhost -Port 3306 -DatabaseName etl_controlplane -Username root -Password "<root-password>" -ApplyGrants -AppUser etl_app -AppHost % -AppPassword "<app-password>"
```

Preview without executing (WhatIf):

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\setup-controlplane.ps1 -Vendor mysql -WhatIf
```

Impact notes:

- Existing control-plane and Spring Batch rows are preserved when setup is re-run.
- Setup can create missing database/tables/indexes and refresh seed rows in `controlplane_trigger_source`.
- With `-ApplyGrants` (MySQL), setup can create/update app-user grants.

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

## `restart-controlplane.ps1`

Purpose:
- Stops any process listening on control-plane port (default `8081`)
- Starts control-plane with explicit main class and `controlplane` profile
- Supports quick `Restart`, `Start`, `Stop`, and `Status`
- Defaults to `CleanMode Preserve` so generated model classes remain intact unless an explicit clean rebuild is requested

Common usage:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\restart-controlplane.ps1 -Action Restart
```

Explicit clean-mode selection:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\restart-controlplane.ps1 -Action Restart -CleanMode Preserve
powershell.exe -ExecutionPolicy Bypass -File .\scripts\restart-controlplane.ps1 -Action Start -CleanMode Clean
```

Status only:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\restart-controlplane.ps1 -Action Status
```

## `generate-models-batch.ps1`

Purpose:
- Scans one or more folder roots for `job-config.yaml`
- Optionally performs one clean upfront (`clean resources:resources`)
- Regenerates job-scoped XML model classes for each discovered config

Generate preserved bundle models:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-models-batch.ps1
```

Generate preserved + private bundle models:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-models-batch.ps1 -IncludePrivateJobs
```

Preview scan and commands only:

```powershell
Set-Location (Resolve-Path ..)
powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-models-batch.ps1 -DryRun
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
- `scripts/tests/test_verify_recent_changes_timeout.py`: validates forced-timeout handling exits with code `124` for `verify-recent-changes.ps1`
