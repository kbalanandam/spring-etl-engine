# Comprehensive setup and validation guide

## Purpose

Use this single runbook to set up `spring-etl-engine` on a new machine, run a selected ETL scenario, bring up the optional control-plane runtime with MySQL or SQL Server, and execute full regression verification.

This guide is intentionally command-first so a new engineer can follow it line by line.

## Scope and boundaries

- Technical identity remains `spring-etl-engine`.
- Product-facing name **OneFlow** appears only in user-facing wording.
- ETL worker runtime is still one selected `job-config.yaml` per run.
- Control-plane runtime is optional and additive; direct ETL runs must remain valid without it.

## Supported database lanes in this setup guide

This document provides step-by-step setup for these active local lanes:

- MySQL (control-plane default local lane)
- SQL Server (control-plane supported enterprise lane)

For the broader persistence contract (including PostgreSQL and Oracle planning lanes), see:

- [`../config/control-plane/control-plane-persistence-profiles.md`](../config/control-plane/control-plane-persistence-profiles.md)

## 1) Prerequisites

Run these checks from PowerShell on Windows.

```powershell
Set-Location "C:\spring-etl-engine"
java -version
mvn -version
```

Expected baseline:

- Java 17 is available.
- Maven is available on `PATH`.
- Repository root is `C:\spring-etl-engine`.

## 2) Baseline project validation

```powershell
Set-Location "C:\spring-etl-engine"
mvn --batch-mode --no-transfer-progress -DskipTests validate
```

If this fails, resolve Java/Maven/tooling first before database setup.

## 3) First ETL worker run (no control-plane required)

This proves the selected-job contract and local runtime baseline.

```powershell
Set-Location "C:\spring-etl-engine"
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/customer-load/job-config.yaml" spring-boot:run
```

Verify:

- Startup logs show selected job resolution.
- Scenario log file is created under `logs\<yyyy-MM-dd>\customer-load.log`.
- Output artifact is produced under the selected scenario output path.

## 4) Optional control-plane setup: MySQL lane

Set environment variables, bootstrap schema, and start profile.

```powershell
Set-Location "C:\spring-etl-engine"
$env:CONTROLPLANE_DB_VENDOR="mysql"
$env:CONTROLPLANE_DB_URL="jdbc:mysql://localhost:3306/etl_controlplane?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:CONTROLPLANE_DB_USERNAME="root"
$env:CONTROLPLANE_DB_PASSWORD="<password>"
$env:CONTROLPLANE_DB_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\setup-controlplane.ps1 -Vendor mysql -ServerName localhost -Port 3306 -DatabaseName etl_controlplane -Username root -Password "<password>"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\restart-controlplane.ps1 -Action Start -Profile controlplane -Port 8081 -CleanMode Preserve -StartupTimeoutSec 120
```

Quick checks:

```powershell
Invoke-RestMethod "http://localhost:8081/api/v1/system/info"
Get-Content "C:\spring-etl-engine\logs\startup\startup.log" -Tail 80
```

## 5) Optional control-plane setup: SQL Server lane

Set environment variables, bootstrap schema, and start profile.

```powershell
Set-Location "C:\spring-etl-engine"
$env:CONTROLPLANE_DB_VENDOR="mssql"
$env:CONTROLPLANE_DB_URL="jdbc:sqlserver://localhost:1433;databaseName=etl_controlplane;encrypt=true;trustServerCertificate=true"
$env:CONTROLPLANE_DB_USERNAME="sa"
$env:CONTROLPLANE_DB_PASSWORD="<password>"
$env:CONTROLPLANE_DB_DRIVER_CLASS_NAME="com.microsoft.sqlserver.jdbc.SQLServerDriver"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\setup-controlplane.ps1 -Vendor mssql -ServerName localhost -Port 1433 -DatabaseName etl_controlplane -Username sa -Password "<password>"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\restart-controlplane.ps1 -Action Start -Profile controlplane -Port 8081 -CleanMode Preserve -StartupTimeoutSec 120
```

Quick checks:

```powershell
Invoke-RestMethod "http://localhost:8081/api/v1/system/info"
Get-Content "C:\spring-etl-engine\logs\startup\startup.log" -Tail 80
```

## 6) Validate expected smoke behavior

The project smoke contract expects:

- `customer-load` succeeds.
- `csv-to-sqlserver` fails fast when placeholder SQL Server values are still present.

Run smoke verifier:

```powershell
Set-Location "C:\spring-etl-engine"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\verify-recent-changes.ps1
```

Check logs:

- `target\verification-smoke.log`
- `target\verify-customer-load.log`
- `target\verify-csv-to-sqlserver.log`

## 7) Full regression verification (recommended before PR merge)

```powershell
Set-Location "C:\spring-etl-engine"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\generate-verification-report.ps1
```

Primary evidence:

- `target\verification-report.md`
- `target\verification-mvn-test.log`
- `target\verification-smoke.log`

Interpretation:

- `STATUS: READY` means Maven tests + smoke checks passed for the collected run.
- `PASS` on `csv-to-sqlserver` negative smoke means expected fail-fast behavior occurred.

## 8) Troubleshooting quick map

### A) Missing selected job

Symptom: startup fails without explicit job.

Action:

- Provide `-Detl.config.job=<path-to-job-config.yaml>`.
- Use demo fallback only when intentionally needed.

### B) Control-plane DB startup failure

Symptom: control-plane profile fails on datasource connection.

Action:

- Re-check `CONTROLPLANE_DB_*` values.
- Confirm DB server reachable on selected host/port.
- Confirm credentials and driver class.

### C) Trigger mode-switch guardrail

Symptom: startup reports trigger persistence mode-switch detection.

Action (intentional local reset only):

```powershell
Set-Location "C:\spring-etl-engine"
mvn -f "C:\spring-etl-engine\pom.xml" --no-transfer-progress "-Dspring-boot.run.main-class=com.etl.controlplane.ControlPlaneApiApplication" "-Dspring-boot.run.profiles=controlplane" "-Dspring-boot.run.jvmArguments=-Dcontrolplane.triggers.persistence.allow-mode-switch=true" spring-boot:run
```

### D) Dependency-check behavior differences (local vs CI)

Symptom: local scan passes but CI fails due to CVE feed drift.

Action:

- Inspect CI `dependency-check-run.log` artifact.
- Keep suppressions package-scoped and temporary.
- Prefer real upgrades when compatible versions become available.

## 9) Operational and security hygiene

- Do not commit real credentials, secrets, or private DB endpoints.
- Use `private-jobs/` for environment-specific private bundles (git-ignored).
- Keep checked-in scenario bundles under `src/main/resources/config-jobs/` runnable and sanitized.

## 10) Related references

- [`../../README.md`](../../README.md)
- [`../config/README.md`](../config/README.md)
- [`../config/job-config.md`](../config/job-config.md)
- [`../config/control-plane/control-plane-persistence-profiles.md`](../config/control-plane/control-plane-persistence-profiles.md)
- [`control-plane-non-sqlite-bring-up.md`](control-plane-non-sqlite-bring-up.md)
