# R2 - Multi-RDBMS datasource and dialect profile contract

## Summary

Define one deploy-time configuration contract for selecting control-plane datasource and ORM dialect so OneFlow can target major RDBMS engines without code forks.

## Current board status

- Epic: **[Epic R](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M3**
- Dependency: **R1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Current persistence configuration is optimized for local SQLite-oriented behavior and does not yet provide one clear, documented deployment matrix for major RDBMS engines.

## Goal

Make database selection a deployment concern with explicit profile contracts and safe defaults.

## Scope

- define supported first-class targets: SQLite (local/dev), PostgreSQL, SQL Server, MySQL, Oracle
- define required datasource and dialect properties per target
- define supported `persistence.mode` choices and fallback behavior
- document baseline connection/transaction expectations for control-plane persistence

## Out of scope

- full production hardening per vendor
- schema migration implementation details
- UI/operator changes unrelated to persistence configuration

## Proposed approach

Publish one configuration matrix in docs and properties guidance that is environment-driven, profile-safe, and explicit about optional control-plane behavior.

The active contract anchor is [`docs/config/control-plane-persistence-profiles.md`](../../../config/control-plane-persistence-profiles.md).

## Operator / runtime impact

- deployment teams can switch DB targets through config
- ETL direct selected-job execution remains unchanged when control-plane is disabled
- startup behavior and failure modes become more predictable

## Trade-off Snapshot

- Decision: profile-driven DB selection over hardcoded vendor path
- Benefit: portability and simpler deployment governance
- Cost: more configuration validation paths
- Risk: misconfigured dialect/profile combinations
- Use when: deploying to multiple enterprise environments
- Avoid when: only local demo mode is required
- Default: keep local-first defaults and fail-fast on invalid combinations
- Evidence: documented profile matrix and config validation checks

## Acceptance criteria

- [x] documented profile matrix covers SQLite, PostgreSQL, SQL Server, MySQL, and Oracle
- [x] property contract clearly separates optional control-plane persistence from ETL worker launch
- [x] invalid profile/dialect combinations fail fast with operator-friendly messages
- [x] at least one preserved deployment example is documented for non-SQLite mode

## R2 validation checklist

### Scope guardrails

- [x] keep this slice docs-first (no persistence implementation changes)
- [x] keep selected-job ETL launch contract unchanged (`etl.config.job` boundary)
- [x] keep control-plane persistence optional and additive

### Property-surface freeze

- [x] define one properties-by-profile matrix for SQLite, PostgreSQL, MySQL, SQL Server, and Oracle
- [x] keep `persistence.mode` contract explicit (`memory` or `jdbc`)
- [ ] map final property keys to `application-controlplane.properties` defaults
- [ ] confirm profile-token naming is consistent across docs and runtime config validation

### Startup validation test matrix (planned)

#### Valid combinations

- [ ] `memory` mode starts without JDBC datasource settings
- [ ] `jdbc + sqlite` starts with SQLite datasource + dialect pairing
- [ ] `jdbc + postgresql` starts with PostgreSQL datasource + dialect pairing
- [ ] `jdbc + mysql` starts with MySQL datasource + dialect pairing
- [ ] `jdbc + sqlserver` starts with SQL Server datasource + dialect pairing
- [ ] `jdbc + oracle` starts with Oracle datasource + dialect pairing

#### Invalid combinations (fail-fast expected)

- [ ] `jdbc` mode without datasource URL/credentials fails fast with operator-friendly error
- [ ] unsupported vendor token fails fast with operator-friendly error
- [ ] vendor/dialect mismatch fails fast with operator-friendly error
- [ ] ambiguous mode selection fails fast (`memory` and `jdbc` mixed)

#### Fallback and boundary checks

- [ ] direct selected-job ETL run remains valid when control-plane persistence is disabled
- [ ] direct selected-job ETL run remains valid when control-plane persistence is unavailable
- [ ] run read-model fallback remains deterministic when optional persistence rows are missing

### Evidence and rollout handoff

- [ ] link focused validation tests to `R2` and `R5` execution notes
- [ ] add one short runbook note for non-SQLite environment bring-up
- [ ] confirm docs and backlog status updates before opening `R3`

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic R`](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)
- [`docs/config/README.md`](../../../config/README.md)
- [`docs/config/control-plane-persistence-profiles.md`](../../../config/control-plane-persistence-profiles.md)
- [`Control-plane persistence boundary contract`](../../../architecture/control-plane/control-plane-persistence-boundary-contract.md)
- [`application-controlplane.properties`](../../../../src/main/resources/application-controlplane.properties)

## Implementation notes

Prefer minimal property surface area and explicit defaults. Avoid vendor-specific flags in shared docs unless strictly required.

## Status notes

Kickoff started after R1 boundary freeze; deploy-time datasource/dialect contract is now documented in the config reference set.

Validation do-ahead planning is merged into this page under `R2 validation checklist`.

Execution sequencing update: keep `R2` in docs-ready/parked state while `Epic F` (`F1`) remains the active near-term delivery lane.

