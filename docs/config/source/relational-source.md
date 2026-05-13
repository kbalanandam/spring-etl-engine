# Relational Source Config

## Purpose

`RelationalSourceConfig` defines a database source reached through `format: relational`.

In the current phase-1 implementation, this source path is intended to support:

- table-based reads
- query-based reads
- default field-to-field processor mapping
- cross-connector flows such as relational-to-XML, relational-to-CSV, and later relational-to-relational

## Java contract

Backed by:
- `src/main/java/com/etl/config/source/RelationalSourceConfig.java`
- `src/main/java/com/etl/config/relational/RelationalConnectionConfig.java`
- `src/main/java/com/etl/config/relational/RelationalDataSourceFactory.java`
- `src/main/java/com/etl/reader/impl/RelationalDynamicReader.java`
- `src/main/java/com/etl/relational/dialect/`

## Supported fields today

### Top-level source fields

| Field | Required | Type | Description |
|---|---|---|---|
| `format` | yes | string | Must be `relational` |
| `sourceName` | yes | string | Logical source name used in processor mapping lookup |
| `packageName` | no in explicit job mode; otherwise yes | string | Deprecated bridge field for generated source model naming. When omitted for an explicit `job-config.yaml` run, the runtime and build-time generation path derive `com.etl.generated.job.<normalized-job-name>.source` |
| `table` | conditional | string | Source table name when reading directly from a table |
| `schema` | no | string | Optional schema override |
| `query` | conditional | string | Explicit select query instead of table read |
| `countQuery` | no | string | Explicit count query used by `getRecordCount()` |
| `fetchSize` | no | integer | JDBC fetch size hint |
| `maxRows` | no | integer | Maximum rows returned by the reader |
| `fields` | yes | list | Fields expected on the generated source model and selected columns |

### Connection fields

| Field | Required | Type | Description |
|---|---|---|---|
| `connection.vendor` | yes | string | Current values: `sqlserver`, test value `h2` |
| `connection.jdbcUrl` | recommended | string | Explicit JDBC URL |
| `connection.host` | conditional | string | Used when `jdbcUrl` is not provided |
| `connection.port` | conditional | integer | Used when `jdbcUrl` is not provided |
| `connection.database` | conditional | string | Used when `jdbcUrl` is not provided |
| `connection.schema` | no | string | Default schema if top-level `schema` is omitted |
| `connection.username` | yes | string | Database username |
| `connection.password` | yes | string | Database password |
| `connection.driverClassName` | no | string | Explicit JDBC driver class |

### Field entries

| Field | Required | Type | Description |
|---|---|---|---|
| `fields[].name` | yes | string | Current assumed source column name and generated model property name |
| `fields[].type` | yes | string | Logical type used by the generated source model contract |

## Recommended examples

### Table source

This shape mirrors the preserved relational source bundle under `src/main/resources/config-jobs/relational-to-relational/source-config.yaml`.

```yaml
sources:
  - format: relational
    sourceName: Customers
    table: Customers
    schema: dbo
    fetchSize: 500
    connection:
      vendor: sqlserver
      jdbcUrl: jdbc:sqlserver://<SQLSERVER_HOST>:1433;databaseName=<SQLSERVER_DATABASE>;encrypt=true;trustServerCertificate=true
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

#### Table source walkthrough

- `sources:` is the required root for source config bundles.
- `format: relational` selects the JDBC reader path.
- `sourceName` is the logical identity matched by processor mappings and job steps.
- `packageName` is a deprecated bridge field for the generated source model package; in explicit job mode prefer omitting it to use the job-scoped default package.
- `table` chooses direct table-based reads.
- `schema` optionally overrides the schema for that table.
- `fetchSize` provides a JDBC streaming hint for larger reads.
- `connection` groups the database connection settings.
- `connection.vendor` selects the relational dialect family.
- `connection.jdbcUrl` is the preferred fully explicit connection string.
- `connection.username`, `connection.password`, and `connection.driverClassName` provide the remaining JDBC details.
- `fields` lists the columns selected into the generated source model.
- `fields[].name` is both the current source column name and the generated property name in phase 1.
- `fields[].type` is the logical type used by the generated model contract.

### Query source

```yaml
sources:
  - format: relational
    sourceName: Customers
    query: SELECT id, name, email FROM dbo.Customers WHERE active = 1
    countQuery: SELECT COUNT(*) FROM dbo.Customers WHERE active = 1
    fetchSize: 500
    connection:
      vendor: sqlserver
      jdbcUrl: jdbc:sqlserver://<SQLSERVER_HOST>:1433;databaseName=<SQLSERVER_DATABASE>;encrypt=true;trustServerCertificate=true
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

#### Query source walkthrough

- `query` replaces `table` when the source must be expressed as a custom SQL select.
- `countQuery` is optional but recommended when the runtime should know the source count for step planning and reporting.
- The remaining fields keep the same meaning as the table example above.
- In phase 1, keep selected SQL column names aligned with `fields[].name` because the shipped relational source contract still assumes field name == selected column name.

## Runtime behavior today

- Exactly one of `table` or `query` must be provided.
- If `countQuery` is supplied, `getRecordCount()` uses it.
- If the source uses `query` and no `countQuery` is provided, `getRecordCount()` returns `-1`.
- `BatchConfig` treats unknown count (`-1`) as chunk mode for safer large-source processing.
- If `fetchSize` is configured, the relational reader passes it to the JDBC cursor reader as the streaming hint.
- Field names are currently treated as both:
  - source column names
  - generated source model property names
- For explicit job-config runs, `packageName` may be omitted and defaults to scenario/job-scoped generated classes such as `com.etl.generated.job.<normalized-job-name>.source`.
- Treat explicit `packageName` as a deprecated compatibility bridge on the active path, not as the preferred authoring style for new relational bundles.

## Current limitations

### Supported today
- `format: relational`
- table or query reads
- H2-backed automated validation
- SQL Server-oriented JDBC configuration model
- field name == column name assumption
- preserved scenario examples for connector-style and relational-to-relational runs
- automated 20k-row relational flow validation through H2

### Not yet supported
- per-field SQL column aliases in config
- reusable named connection registries
- incremental extraction columns/values
- vendor-specific pagination/query rewriting
- stored procedures
- advanced restart semantics for relational reads

## Validation / usage notes

- `sourceName` must match `processor.mappings[].source`.
- Use explicit `countQuery` when query sources need predictable chunk/tasklet selection.
- Keep selected column names aligned with configured field names during phase 1.
- If both top-level `schema` and `connection.schema` are provided, top-level `schema` wins.
- For larger loads, tune `fetchSize` and keep the step chunk size aligned with the intended relational target write grouping.
- Selected relational source configs are validated at startup, and placeholder tokens such as `<SQLSERVER_HOST>` or `<SQLSERVER_DATABASE>` are rejected before JDBC runtime.

## Preserved examples

- `src/main/resources/config-jobs/relational-to-relational/source-config.yaml`

## Related docs

- [`CSV source reference`](csv-source.md)
- [`Relational target reference`](../target/relational-target.md)
- [`Relational DB support`](../../architecture/relational-db-support.md)



