# sqlserver-header-detail-custom-positive

Preserved A7/A7b SQL Server proof bundle for a real file-load flow with header status tracking.

## What this proves

- custom pre-step inserts one `dbo.etl_run_header` row with status `IN_PROGRESS`
- standard middle step reads `input/Customers.csv` and loads the rows into `dbo.CustomersAudit`
- custom completion step updates the same header row to status `SUCCESS`
- header `detail_count` is derived from the standard file-load step write count

## Prerequisites

- SQL Server reachable with valid connection values in `job-config.yaml` and `target-config.yaml`
- Existing table:
  - `dbo.etl_run_header`
- `dbo.CustomersAudit` is created on demand by the `header-start` custom step if it does not exist

## Run (Windows)

```powershell
Set-Location "C:\spring-etl-engine"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\job-runner.ps1 -Action both -JobConfigPath src/main/resources/config-jobs/sqlserver-header-detail-custom-positive/job-config.yaml
```

## Expected evidence

- run log: `logs/<yyyy-MM-dd>/sqlserver-header-detail-custom-positive.log`
- `RUN_SUMMARY ... status=COMPLETED`
- header row transitions `IN_PROGRESS -> SUCCESS`
- header `detail_count=3`
- `dbo.CustomersAudit` receives 3 rows from the CSV file


