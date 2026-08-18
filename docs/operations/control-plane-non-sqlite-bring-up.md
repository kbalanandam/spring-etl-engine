# Control-plane non-SQLite bring-up (quick runbook)

## Purpose

Provide a short operational sequence for bringing up the optional control-plane runtime against non-SQLite relational engines while keeping direct selected-job ETL execution unchanged.

## Preconditions

- Database server is reachable.
- Driver and connection settings are provided through environment variables.
- You are running from repository root.

## MySQL lane (example)

```powershell
$env:CONTROLPLANE_DB_VENDOR="mysql"
$env:CONTROLPLANE_DB_URL="jdbc:mysql://localhost:3306/etl_controlplane?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:CONTROLPLANE_DB_USERNAME="root"
$env:CONTROLPLANE_DB_PASSWORD="<password>"
$env:CONTROLPLANE_DB_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\setup-controlplane.ps1 -Vendor mysql -ServerName localhost -Port 3306 -DatabaseName etl_controlplane -Username root -Password "<password>"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\restart-controlplane.ps1 -Action Start -Profile controlplane -Port 8081 -CleanMode Preserve -StartupTimeoutSec 120
```

## SQL Server lane (example)

```powershell
$env:CONTROLPLANE_DB_VENDOR="mssql"
$env:CONTROLPLANE_DB_URL="jdbc:sqlserver://localhost:1433;databaseName=etl_controlplane;encrypt=true;trustServerCertificate=true"
$env:CONTROLPLANE_DB_USERNAME="sa"
$env:CONTROLPLANE_DB_PASSWORD="<password>"
$env:CONTROLPLANE_DB_DRIVER_CLASS_NAME="com.microsoft.sqlserver.jdbc.SQLServerDriver"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\setup-controlplane.ps1 -Vendor mssql -ServerName localhost -Port 1433 -DatabaseName etl_controlplane -Username sa -Password "<password>"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\restart-controlplane.ps1 -Action Start -Profile controlplane -Port 8081 -CleanMode Preserve -StartupTimeoutSec 120
```

## Quick checks

- Check startup logs for profile + datasource readiness in `logs/startup/startup.log`.
- Check API health and active database metadata through `GET /api/v1/system/info`.
- Confirm direct selected-job ETL execution remains independent of control-plane by running a selected `etl.config.job` flow.

## Related references

- `docs/config/control-plane/control-plane-persistence-profiles.md`
- `scripts/setup-controlplane.ps1`
- `scripts/restart-controlplane.ps1`

