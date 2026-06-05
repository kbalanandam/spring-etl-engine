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
| SQLite | local developer and smoke lanes | baseline | Keep lightweight local-first setup and troubleshooting path. |
| PostgreSQL | enterprise shared-db lane | first-class target | Primary portability parity target. |
| MySQL | enterprise shared-db lane | first-class target | Primary portability parity target. |
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

## Related docs

- [`README.md`](README.md)
- [`../product/backlog-items/scheduler/R2-multi-rdbms-datasource-and-dialect-profile-contract.md`](../product/backlog-items/scheduler/R2-multi-rdbms-datasource-and-dialect-profile-contract.md)
- [`../product/epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md`](../product/epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)
- [`../architecture/control-plane/control-plane-persistence-boundary-contract.md`](../architecture/control-plane/control-plane-persistence-boundary-contract.md)


