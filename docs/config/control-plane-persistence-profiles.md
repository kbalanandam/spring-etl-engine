# Control-plane persistence profiles

## Summary

This page defines the `R2` deploy-time configuration contract for optional control-plane persistence across major relational engines.

The contract applies to retained control-plane history only. It does not change selected-job ETL execution semantics.

## Boundary alignment

- ETL worker execution remains anchored to one selected job via `etl.config.job`.
- Control-plane persistence remains optional and additive.
- Direct ETL runs remain valid when control-plane persistence is disabled or unavailable.

See [`../architecture/control-plane/control-plane-persistence-boundary-contract.md`](../architecture/control-plane/control-plane-persistence-boundary-contract.md) for frozen boundary invariants.

## Supported target lanes

| Target DB | Intended lane | Contract level | Notes |
|---|---|---|---|
| SQLite | legacy compatibility lane | bridge-only | Keep for legacy data recovery and compatibility scripts; not the default dev/control-plane path. |
| PostgreSQL | enterprise shared-db lane | first-class target | Primary portability parity target. |
| MySQL | enterprise shared-db lane | default active lane | Default `controlplane` profile datasource for dev/CI with aligned worker datasource settings. |
| SQL Server | enterprise integration lane | first-class target | Validate in CI or scheduled integration lanes. |
| Oracle | enterprise integration lane | first-class target | Validate in scheduled integration lanes first. |

## Profile and mode contract

Use configuration to choose persistence behavior at deploy time.

- `memory`: in-memory retained history path for control-plane runtime.
- `jdbc`: relational persistence path driven by configured datasource and dialect.

Contract rules:

1. Persistence mode selection must be explicit and environment-driven.
2. `jdbc` mode requires a complete datasource + dialect pairing.
3. Invalid mode/dialect combinations fail fast with operator-friendly startup errors.
4. Missing or unavailable control-plane persistence must not break direct ETL execution semantics.

## Properties by profile

Use this as the `R2` implementation-ready matrix for deploy-time configuration shape.

| Profile intent | `controlplane.persistence.mode` | Required datasource shape | Dialect intent |
|---|---|---|---|
| Memory baseline | `memory` | none | none |
| SQLite JDBC | `jdbc` | SQLite URL + credentials where required by environment | SQLite dialect |
| PostgreSQL JDBC | `jdbc` | PostgreSQL URL + username + password | PostgreSQL dialect |
| MySQL JDBC | `jdbc` | MySQL URL + username + password | MySQL dialect |
| SQL Server JDBC | `jdbc` | SQL Server URL + username + password | SQL Server dialect |
| Oracle JDBC | `jdbc` | Oracle URL + username + password | Oracle dialect |

Validation expectations:

- `memory` mode must not require JDBC datasource properties
- `jdbc` mode must require datasource + dialect pairing
- vendor and dialect intent must match

## Datasource and dialect matrix

Use one of these vendor-intent pairings when `jdbc` mode is selected.

| Vendor intent | Datasource family | Dialect intent |
|---|---|---|
| SQLite | SQLite datasource | SQLite dialect |
| PostgreSQL | PostgreSQL datasource | PostgreSQL dialect |
| MySQL | MySQL datasource | MySQL dialect |
| SQL Server | SQL Server datasource | SQL Server dialect |
| Oracle | Oracle datasource | Oracle dialect |

Portability guardrails:

- keep read-model semantics stable across vendors
- isolate unavoidable vendor-specific behavior to migration deltas
- avoid vendor forks in shared API/read-model contracts

## Fail-fast validation expectations

At startup, fail fast when:

- `jdbc` mode is selected without required datasource settings
- dialect intent does not match configured vendor lane
- an unsupported vendor token is configured
- configuration requests a mixed or ambiguous mode contract

## Non-SQLite example lane

PostgreSQL example (illustrative contract shape):

```properties
controlplane.persistence.mode=jdbc
spring.datasource.url=jdbc:postgresql://<host>:5432/<database>
spring.datasource.username=<username>
spring.datasource.password=<password>
spring.jpa.database-platform=<postgresql-dialect>
```

Use environment-specific secret management for credentials; do not commit real values.

Current implementation note:

- The shipped control-plane path is JDBC-first (no JPA requirement) and now uses one canonical vendor token plus vendor-neutral datasource properties in the `controlplane` profile:

```properties
controlplane.db.vendor=${CONTROLPLANE_DB_VENDOR:mysql}
controlplane.db.url=${CONTROLPLANE_DB_URL:...}
controlplane.db.username=${CONTROLPLANE_DB_USERNAME:...}
controlplane.db.password=${CONTROLPLANE_DB_PASSWORD:...}
controlplane.db.driver-class-name=${CONTROLPLANE_DB_DRIVER_CLASS_NAME:...}
```

- `spring.datasource.*` and `controlplane.job-launch.worker.datasource.*` are wired from these canonical properties unless explicitly overridden by worker-specific env vars.

- Local/CI MySQL run with the default `controlplane` profile:

```powershell
$env:CONTROLPLANE_DB_VENDOR="mysql"
$env:CONTROLPLANE_DB_URL="jdbc:mysql://localhost:3306/etl_controlplane?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:CONTROLPLANE_DB_USERNAME="root"
$env:CONTROLPLANE_DB_PASSWORD="<password>"
$env:CONTROLPLANE_DB_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver"
mvn --no-transfer-progress "-Dspring-boot.run.profiles=controlplane" spring-boot:run
```

- Local SQL Server run with the default `controlplane` profile:

```powershell
$env:CONTROLPLANE_DB_VENDOR="mssql"
$env:CONTROLPLANE_DB_URL="jdbc:sqlserver://localhost:1433;databaseName=etl_controlplane;encrypt=true;trustServerCertificate=true"
$env:CONTROLPLANE_DB_USERNAME="sa"
$env:CONTROLPLANE_DB_PASSWORD="<password>"
$env:CONTROLPLANE_DB_DRIVER_CLASS_NAME="com.microsoft.sqlserver.jdbc.SQLServerDriver"
mvn --no-transfer-progress "-Dspring-boot.run.profiles=controlplane" spring-boot:run
```


- Keep `controlplane.job-launch.worker.datasource.*` aligned to the same URL/credentials so trigger events, run records, step projections, and Spring Batch metadata remain linkable without cross-database joins.
- Bootstrap both `controlplane_*` and `BATCH_*` tables into the same selected database (`scripts/setup-controlplane-mysql.ps1` or `scripts/setup-controlplane-mssql.ps1`) before starting the control-plane profile.
- Ensure `controlplane.job-launch.worker.connection-init-sql` is blank (or vendor-valid) so SQLite-only `PRAGMA` statements are not passed to MySQL/SQL Server workers.
- Active trigger/run JDBC read paths now use vendor-aware paging clauses (`LIMIT/OFFSET` for MySQL, `OFFSET ... FETCH NEXT` for SQL Server) to keep control-plane list/detail endpoints runnable across both lanes.
- The currently verified no-server portability scope is registry startup plus update/insert write paths without SQLite-only `on conflict ... excluded`, string-concatenation, or `cast(... as text)` SQL. Legacy SQLite bridge migrations (`pragma_table_info`, `rowid`, SQLite trigger DDL) remain isolated to SQLite-gated paths and still need real MySQL-lane validation before MySQL can be treated as full parity.

## Related docs

- [`README.md`](README.md)
- [`../product/backlog-items/scheduler/R2-multi-rdbms-datasource-and-dialect-profile-contract.md`](../product/backlog-items/scheduler/R2-multi-rdbms-datasource-and-dialect-profile-contract.md)
- [`../product/epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md`](../product/epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)
- [`../architecture/control-plane/control-plane-persistence-boundary-contract.md`](../architecture/control-plane/control-plane-persistence-boundary-contract.md)


