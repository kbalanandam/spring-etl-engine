# CSV to SQL Server Scenario

This folder preserves one concrete scenario config set without changing the baseline YAML files under `src/main/resources/`.

## Scenario

- source: CSV
- processor: default field mapping
- target: SQL Server relational table

## Files

- `job-config.yaml`
- `source-config.yaml`
- `target-config.yaml`
- `processor-config.yaml`

## Runtime command

Preferred run style: point the app at this scenario's `job-config.yaml` so one file explicitly selects the source, target, and processor config set.

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-scenarios/csv-to-sqlserver/job-config.yaml" spring-boot:run
```

Legacy direct-path override mode still works if needed:

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.source=src/main/resources/config-scenarios/csv-to-sqlserver/source-config.yaml -Detl.config.target=src/main/resources/config-scenarios/csv-to-sqlserver/target-config.yaml -Detl.config.processor=src/main/resources/config-scenarios/csv-to-sqlserver/processor-config.yaml" spring-boot:run
```

## Intended target table

This scenario assumes a SQL Server table like:

- `dbo.Customers`

with columns:

- `id`
- `name`
- `email`

## Connection values

The committed `target-config.yaml` now uses placeholders for host, database, username, and password.
Replace them with environment-appropriate values before running this scenario against a live SQL Server.

## Usage pattern

Select this scenario by explicitly choosing its `job-config.yaml` at runtime instead of editing the baseline resource YAML files or scanning all scenarios.



