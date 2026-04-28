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
| `packageName` | yes | string | Package used for generated source model naming |
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

This shape mirrors the preserved relational source bundle under `src/main/resources/config-scenarios/relational-to-relational/source-config.yaml`.

```yaml
sources:
  - format: relational
    sourceName: Customers
    packageName: com.etl.model.source
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

### Query source

```yaml
sources:
  - format: relational
    sourceName: Customers
    packageName: com.etl.model.source
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

## Runtime behavior today

- Exactly one of `table` or `query` must be provided.
- If `countQuery` is supplied, `getRecordCount()` uses it.
- If the source uses `query` and no `countQuery` is provided, `getRecordCount()` returns `-1`.
- `BatchConfig` treats unknown count (`-1`) as chunk mode for safer large-source processing.
- If `fetchSize` is configured, the relational reader passes it to the JDBC cursor reader as the streaming hint.
- Field names are currently treated as both:
  - source column names
  - generated source model property names

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

- `src/main/resources/config-scenarios/relational-to-relational/source-config.yaml`

## Related docs

- [`csv-source.md`](csv-source.md)
- [`../target/relational-target.md`](../target/relational-target.md)
- [`../../architecture/relational-db-support.md`](../../architecture/relational-db-support.md)


