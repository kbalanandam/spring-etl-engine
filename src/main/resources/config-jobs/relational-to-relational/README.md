# relational-to-relational

Business scenario for relational source to relational target flow.

## Purpose

Use this bundle as the preserved phase-1 relational example when you want:

- a relational source table
- a relational target table
- explicit SQL Server connection placeholders in committed YAML
- source-side `countQuery` and `fetchSize` plus target-side `batchSize`

## Bundle map

- `job-config.yaml` - selects the runnable source, target, and processor files
- `source-config.yaml` - declares the relational source table and JDBC settings
- `target-config.yaml` - declares the relational target table and JDBC settings
- `processor-config.yaml` - maps source columns directly to target columns

## `job-config.yaml`

```yaml
name: relational-to-relational
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
steps:
  - name: customers-relational-step
    source: Customers
    target: CustomersSql
```

### What each field means

- `name` identifies the scenario in logs and metadata.
- `sourceConfigPath`, `targetConfigPath`, and `processorConfigPath` point at the sibling config files.
- `steps` contains one explicit relational step.
- `source: Customers` must match the relational `sourceName`.
- `target: CustomersSql` must match the relational `targetName`.

## `source-config.yaml`

```yaml
sources:
  - format: relational
    sourceName: Customers
    packageName: com.etl.generated.job.relationaltorelational.source
    schema: dbo
    table: CustomersSource
    countQuery: SELECT COUNT(*) FROM dbo.CustomersSource
    fetchSize: 500
    connection:
      vendor: sqlserver
      jdbcUrl: jdbc:sqlserver://<SQLSERVER_HOST>:1433;databaseName=<SQLSERVER_DATABASE>;encrypt=true;trustServerCertificate=true
      schema: dbo
      username: <SQLSERVER_USERNAME>
      password: <SQLSERVER_PASSWORD>
      driverClassName: com.microsoft.sqlserver.jdbc.SQLServerDriver
    fields:
      - name: id
        type: int
      - name: name
        type: String
      - name: email
        type: String
```

### What each field means

- `format: relational` selects the JDBC reader path.
- `sourceName: Customers` is the logical identity used by the job step and processor mapping.
- `packageName` points to the scenario-scoped generated source package.
- `schema` and `table` identify the source table.
- `countQuery` keeps source counting explicit for planning and reporting.
- `fetchSize` is the source-side streaming hint.
- `connection` groups the SQL Server settings used by the reader.
- The `<...>` values are placeholders that must be replaced before execution.
- `fields` lists the selected source columns and generated source model properties.

## `target-config.yaml`

```yaml
targets:
  - format: relational
    targetName: CustomersSql
    packageName: com.etl.generated.job.relationaltorelational.target
    schema: dbo
    table: CustomersTarget
    writeMode: insert
    batchSize: 500
    connection:
      vendor: sqlserver
      jdbcUrl: jdbc:sqlserver://<SQLSERVER_HOST>:1433;databaseName=<SQLSERVER_DATABASE>;encrypt=true;trustServerCertificate=true
      schema: dbo
      username: <SQLSERVER_USERNAME>
      password: <SQLSERVER_PASSWORD>
      driverClassName: com.microsoft.sqlserver.jdbc.SQLServerDriver
    fields:
      - name: id
        type: int
      - name: name
        type: String
      - name: email
        type: String
```

### What each field means

- `format: relational` selects the JDBC writer path.
- `targetName: CustomersSql` is the logical target identity used by the job step and processor mapping.
- `packageName` points to the scenario-scoped generated target package.
- `schema` and `table` identify the destination table.
- `writeMode: insert` uses the only shipped relational write mode today.
- `batchSize: 500` is the target-side write-grouping hint.
- `connection` groups the SQL Server settings used by the writer.
- `fields` lists the written target properties and database columns in phase 1.

## `processor-config.yaml`

```yaml
type: default
mappings:
  - source: Customers
    target: CustomersSql
    fields:
      - from: id
        to: id
      - from: name
        to: name
      - from: email
        to: email
```

### What each field means

- `type: default` selects the shipped processor implementation.
- The single mapping converts `Customers` source rows into `CustomersSql` target rows.
- Each `from -> to` pair is a direct field copy, so the phase-1 assumption stays simple: field names should align with selected source columns and target table columns.

## Notes

- Replace placeholder SQL Server connection values before running this scenario.
- `countQuery` is included so the job can make a predictable chunk/tasklet decision for the source.
- `fetchSize` and `batchSize` are included as the phase-1 large-volume tuning knobs.

## Run example

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/relational-to-relational/job-config.yaml" spring-boot:run
```
