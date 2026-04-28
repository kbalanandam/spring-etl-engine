# Relational Target Config

## Purpose

`RelationalTargetConfig` defines a database target reached through `format: relational`.

In the current phase-1 implementation, this is the first relational target path and is intended to support:

- existing source types such as CSV
- default field-to-field processor mapping
- insert-oriented writes into a relational table

## Java contract

Backed by:
- `src/main/java/com/etl/config/target/RelationalTargetConfig.java`
- `src/main/java/com/etl/config/relational/RelationalConnectionConfig.java`
- `src/main/java/com/etl/writer/impl/RelationalDynamicWriter.java`
- `src/main/java/com/etl/relational/dialect/`

## Supported fields today

### Top-level target fields

| Field | Required | Type | Description |
|---|---|---|---|
| `format` | yes | string | Must be `relational` |
| `targetName` | yes | string | Logical target name used in processor mapping lookup |
| `packageName` | yes | string | Package used for generated target model naming |
| `schema` | no | string | Optional schema override, e.g. `dbo` |
| `table` | yes | string | Target table name |
| `writeMode` | no | string | Currently only `insert` is supported |
| `batchSize` | no | integer | Hint for intended relational batch sizing; defaults to `100` |
| `fields` | yes | list | Field/property names expected on the target object and table columns |

### Connection fields

| Field | Required | Type | Description |
|---|---|---|---|
| `connection.vendor` | yes | string | Current values: `sqlserver`, test value `h2` |
| `connection.jdbcUrl` | recommended | string | Explicit JDBC URL |
| `connection.host` | conditional | string | Used when `jdbcUrl` is not provided |
| `connection.port` | conditional | integer | Used when `jdbcUrl` is not provided |
| `connection.database` | conditional | string | Used when `jdbcUrl` is not provided |
| `connection.schema` | no | string | Default schema if target-level `schema` is omitted |
| `connection.username` | yes | string | Database username |
| `connection.password` | yes | string | Database password |
| `connection.driverClassName` | no | string | Explicit JDBC driver class |

### Field entries

| Field | Required | Type | Description |
|---|---|---|---|
| `fields[].name` | yes | string | Property name and current assumed DB column name |
| `fields[].type` | yes | string | Logical type used by the generated target model contract |

## Recommended example

This shape mirrors the preserved SQL Server target bundles under `src/main/resources/config-scenarios/csv-to-sqlserver/target-config.yaml` and `src/main/resources/config-scenarios/relational-to-relational/target-config.yaml`.

```yaml
targets:
  - format: relational
    targetName: CustomersSql
    packageName: com.etl.model.target
    schema: dbo
    table: Customers
    writeMode: insert
    batchSize: 100
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

## Runtime behavior today

- The writer resolves a JDBC target through the relational connection settings.
- The current dialect layer supports SQL Server as the first live vendor target.
- If `jdbcUrl` is absent, the writer can construct a SQL Server URL from host/port/database.
- The writer currently generates an `INSERT INTO ... VALUES ...` statement only.
- The writer executes inside the surrounding Spring Batch chunk/tasklet lifecycle; for larger loads, align the step chunk size with the configured `batchSize` value.
- Field names are currently treated as both:
  - target object property names
  - target table column names

## Current limitations

Phase-1 relational target support is intentionally narrow.

### Supported today
- `format: relational`
- SQL Server as the first real target vendor
- insert-only writes
- one target table per config entry
- current field name == current database column name assumption
- H2-backed automated writer and relational-to-relational flow validation
- preserved scenario bundles for `csv-to-sqlserver` and `relational-to-relational`

### Not yet supported
- `update`
- `upsert`
- `truncate-insert`
- per-field database column aliases
- stored procedures
- richer transaction/restart semantics
- reusable named connection registries

## Validation / usage notes

- `targetName` must match `processor.mappings[].target`.
- Use explicit `jdbcUrl` for the first live test when possible.
- Keep DB column names aligned with configured field names during phase 1.
- Keep credentials out of committed real environment configs where possible; use placeholders in committed scenario YAMLs.
- If both top-level `schema` and `connection.schema` are provided, target-level `schema` wins.
- Treat `batchSize` as the relational write-grouping value to mirror in higher-volume chunk-oriented jobs.
- Selected relational target configs are validated at startup, and placeholder tokens such as `<SQLSERVER_HOST>`, `<SQLSERVER_DATABASE>`, `<SQLSERVER_USERNAME>`, and `<SQLSERVER_PASSWORD>` are rejected before the job reaches JDBC runtime.

## Preserved examples

- `src/main/resources/config-scenarios/csv-to-sqlserver/target-config.yaml`
- `src/main/resources/config-scenarios/relational-to-relational/target-config.yaml`

## Related docs

- [`../source/csv-source.md`](../source/csv-source.md)
- [`../processor/default-processor.md`](../processor/default-processor.md)
- [`../../architecture/relational-db-support.md`](../../architecture/relational-db-support.md)

