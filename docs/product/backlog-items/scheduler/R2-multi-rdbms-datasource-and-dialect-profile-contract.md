# R2 - Multi-RDBMS datasource and dialect profile contract

## Summary

Define one deploy-time configuration contract for selecting control-plane datasource and ORM dialect so OneFlow can target major RDBMS engines without code forks.

## Current board status

- Epic: **[Epic R](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M3**
- Dependency: **R1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Current persistence configuration must remain explicit and portable across supported relational engines without reviving SQLite-only historical assumptions.

## Goal

Make database selection a deployment concern with explicit profile contracts and safe defaults.

## Scope

- define supported first-class targets: PostgreSQL, SQL Server, MySQL, Oracle
- define required datasource and dialect properties per target
- define supported `persistence.mode` choices and fallback behavior
- document baseline connection/transaction expectations for control-plane persistence

## Out of scope

- full production hardening per vendor
- schema migration implementation details
- UI/operator changes unrelated to persistence configuration

## Proposed approach

Publish one configuration matrix in docs and properties guidance that is environment-driven, profile-safe, and explicit about optional control-plane behavior.

The active contract anchor is [`docs/config/control-plane/control-plane-persistence-profiles.md`](../../../config/control-plane/control-plane-persistence-profiles.md).

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

- [x] documented profile matrix covers PostgreSQL, SQL Server, MySQL, and Oracle
- [x] property contract clearly separates optional control-plane persistence from ETL worker launch
- [x] invalid profile/dialect combinations fail fast with operator-friendly messages
- [x] at least one preserved deployment example is documented for supported relational modes

## R2 validation checklist

### Scope guardrails

- [x] keep this slice docs-first (no persistence implementation changes)
- [x] keep selected-job ETL launch contract unchanged (`etl.config.job` boundary)
- [x] keep control-plane persistence optional and additive

### Property-surface freeze

- [x] define one properties-by-profile matrix for PostgreSQL, MySQL, SQL Server, and Oracle
- [x] keep `persistence.mode` contract explicit (`memory` or `jdbc`)
- [x] map final property keys to `application-controlplane.properties` defaults
- [x] confirm profile-token naming is consistent across docs and runtime config validation

### Startup validation test matrix (planned)

#### Valid combinations

- [x] `memory` mode starts without JDBC datasource settings
- [x] `jdbc + postgresql` starts with PostgreSQL datasource + dialect pairing
- [x] `jdbc + mysql` starts with MySQL datasource + dialect pairing
- [x] `jdbc + sqlserver` starts with SQL Server datasource + dialect pairing
- [x] `jdbc + oracle` starts with Oracle datasource + dialect pairing

#### Invalid combinations (fail-fast expected)

- [x] `jdbc` mode without datasource URL/credentials fails fast with operator-friendly error
- [x] unsupported vendor token fails fast with operator-friendly error
- [x] vendor/dialect mismatch fails fast with operator-friendly error
- [x] ambiguous mode selection fails fast (`memory` and `jdbc` mixed)

#### Fallback and boundary checks

- [x] direct selected-job ETL run remains valid when control-plane persistence is disabled
- [x] direct selected-job ETL run remains valid when control-plane persistence is unavailable
- [x] run read-model fallback remains deterministic when optional persistence rows are missing

### Evidence and rollout handoff

- [x] link focused validation tests to `R2` and `R5` execution notes
- [x] add one short runbook note for supported relational environment bring-up
- [x] confirm docs and backlog status updates before opening `R3`

### Evidence snapshot (2026-08-18)

- Targeted config and fallback tests passed (`110 tests`): `target/tmp-r2-targeted-tests.log`
  - Includes `ConfigLoaderJobConfigTest`, `RelationalConnectionConfigTest`, `RunSummaryReadModelServiceTest`, and `TriggerEventPersistenceModeGuardTest`
  - Surefire reports: `target/surefire-reports/TEST-com.etl.config.ConfigLoaderJobConfigTest.xml`, `target/surefire-reports/TEST-com.etl.config.relational.RelationalConnectionConfigTest.xml`, `target/surefire-reports/TEST-com.etl.controlplane.monitoring.RunSummaryReadModelServiceTest.xml`, `target/surefire-reports/TEST-com.etl.controlplane.triggers.TriggerEventPersistenceModeGuardTest.xml`
- Profile/bootstrap contract tests passed (`5 tests`): `target/tmp-r2-profile-tests.log`
  - Includes `ApplicationDevProfileDatasourceTest`, `SystemControllerTest`, and `ControlPlaneBootstrapScriptsTest`
  - Surefire reports: `target/surefire-reports/TEST-com.etl.config.ApplicationDevProfileDatasourceTest.xml`, `target/surefire-reports/TEST-com.etl.controlplane.api.SystemControllerTest.xml`, `target/surefire-reports/TEST-com.etl.controlplane.monitoring.ControlPlaneBootstrapScriptsTest.xml`
- Smoke/fallback verification passed: `target/tmp-r2-verify-recent.log`
  - Produced artifacts: `target/verify-customer-load.log`, `target/verify-csv-to-sqlserver.log`, `target/verify-trigger-now.log`
- Persistence contract guard matrix/fail-fast tests passed (`11 tests`): `target/tmp-r2-guard-tests.log`
  - Includes `ControlPlanePersistenceContractGuardTest`
  - Surefire report: `target/surefire-reports/TEST-com.etl.controlplane.ControlPlanePersistenceContractGuardTest.xml`
- Trigger-now persistence-unavailable fallback tests passed: `target/tmp-r2-next-task-tests.log`
  - Includes `JobBundleControllerTriggerNowUnitTest` and `JobBundleControllerTest` fallback coverage where trigger registry read/write is unavailable.
- Verification workflow passed with intentionally unavailable control-plane DB settings: `target/tmp-r2-verify-recent-unavailable-controlplane.log`
  - Confirms direct selected-job positive smoke run (`customer-load`) remains successful.

Remaining `R2` gate scope before moving status to `Done`:

- none; `R2` acceptance scope is closed.

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic R`](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)
- [`docs/config/README.md`](../../../config/README.md)
- [`docs/config/control-plane/control-plane-persistence-profiles.md`](../../../config/control-plane/control-plane-persistence-profiles.md)
- [`docs/operations/control-plane-non-sqlite-bring-up.md`](../../../operations/control-plane-non-sqlite-bring-up.md)
- [`Control-plane persistence boundary contract`](../../../architecture/control-plane/control-plane-persistence-boundary-contract.md)
- [`application-controlplane.properties`](../../../../src/main/resources/application-controlplane.properties)

## Implementation notes

Prefer minimal property surface area and explicit defaults. Avoid vendor-specific flags in shared docs unless strictly required.

## Status notes

Kickoff moved into active implementation support after R1 boundary freeze; deploy-time datasource/dialect contract is now documented in the config reference set and aligned with the active control-plane profile/property surface.

Validation do-ahead planning merged into this page is now closed for `R2`, including matrix proof, control-plane-disabled fallback, and control-plane-persistence-unavailable fallback evidence.

Execution sequencing update: `R2` closure is complete and the persistence lane handoff now proceeds into active `R3`, followed by `R4`/`R5`.

