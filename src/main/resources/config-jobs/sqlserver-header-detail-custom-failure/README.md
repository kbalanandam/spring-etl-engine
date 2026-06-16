# sqlserver-header-detail-custom-failure

Preserved A7/A7b SQL Server proof bundle for a real file-load failure flow with header status finalization.

## What this proves

- custom pre-step inserts one `dbo.etl_run_header` row with status `IN_PROGRESS`
- standard middle step reads `input/Customers-invalid.csv` and fails on malformed file content before the completion step runs
- failed job invokes `CustomStepFailureFinalizer` and updates the same header row to status `FAILED`

## Prerequisites

- SQL Server reachable with named startup connection `sqlserver-main` configured under `etl.config.relational.connections.sqlserver-main.*`
- `target-config.yaml` and `job-config.yaml` custom steps both reference `connectionRef: sqlserver-main`
- Existing table:
  - `dbo.etl_run_header`
- `dbo.CustomersAuditFailure` is created on demand by the `header-start` custom step if it does not exist

## Run (Windows)

```powershell
Set-Location "C:\spring-etl-engine"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\job-runner.ps1 -Action both -JobConfigPath src/main/resources/config-jobs/sqlserver-header-detail-custom-failure/job-config.yaml
```

## Expected evidence

- run log: `logs/<yyyy-MM-dd>/sqlserver-header-detail-custom-failure.log`
- `RUN_SUMMARY ... status=FAILED`
- `RUN_EVENT event=custom_step_failure_finalized ...`
- header row transitions `IN_PROGRESS -> FAILED`
- header `detail_count=0` because the malformed CSV row prevents successful writes



