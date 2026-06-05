# Control-plane persistence boundary contract

## Purpose

Freeze the Epic R boundary before portability implementation so control-plane persistence can evolve across RDBMS engines without changing the selected-job ETL runtime contract.

## Contract statement

Control-plane persistence is an optional operational layer. ETL worker execution remains anchored on one selected job (`etl.config.job`) and must continue to run when control-plane persistence is disabled, unavailable, or configured in memory mode.

## Invariants

- Keep ETL worker launch and step execution semantics unchanged.
- Keep control-plane persistence additive and optional.
- Keep operator-facing read-model behavior stable while internals evolve.
- Keep migration and schema evolution explicit and portable-first.

## Read-model parity guardrails

The following views are behavior contracts and must remain backward compatible while persistence internals move from JDBC SQL toward JPA/Hibernate:

- `RunSummaryView`
- `RunRecoveryView`
- `RunStepRecordView`
- `RunArtifactRecordView`

Compatibility expectations:

- existing API fields keep their semantic meaning
- missing optional persistence rows return deterministic fallback views where already supported
- stable run identity (`jobExecutionId`) remains the correlation anchor for API consumers

## Non-goals for R1

- introducing mandatory control-plane database usage for ETL runs
- changing scheduler overlap/recovery runtime semantics
- implementing final JPA/Hibernate entity/repository mappings (R3)
- introducing cross-vendor migration mechanics (R4)

## Implementation sequencing

- `R1`: freeze boundary and invariants
- `R2`: define datasource/dialect profile contract
- `R3`: implement JPA/Hibernate entities and repositories
- `R4`: establish cross-RDBMS migration baseline
- `R5`: prove parity and fallback behavior

## Related docs

- [`ADR-0014: freeze JPA/Hibernate control-plane persistence boundary`](../../adr/control-plane/0014-freeze-jpa-hibernate-control-plane-persistence-boundary.md)
- [`control-plane-worker-boundary.md`](./control-plane-worker-boundary.md)
- [`control-plane-operational-data-model.md`](./control-plane-operational-data-model.md)
- [`../../config/control-plane-persistence-profiles.md`](../../config/control-plane-persistence-profiles.md)
- [`docs/product/backlog-items/scheduler/R1-freeze-jpa-hibernate-control-plane-persistence-boundary.md`](../../product/backlog-items/scheduler/R1-freeze-jpa-hibernate-control-plane-persistence-boundary.md)

