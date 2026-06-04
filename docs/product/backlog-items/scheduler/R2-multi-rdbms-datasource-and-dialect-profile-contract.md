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

- [ ] documented profile matrix covers SQLite, PostgreSQL, SQL Server, MySQL, and Oracle
- [ ] property contract clearly separates optional control-plane persistence from ETL worker launch
- [ ] invalid profile/dialect combinations fail fast with operator-friendly messages
- [ ] at least one preserved deployment example is documented for non-SQLite mode

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic R`](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)
- [`docs/config/README.md`](../../../config/README.md)
- [`application-controlplane.properties`](../../../../src/main/resources/application-controlplane.properties)

## Implementation notes

Prefer minimal property surface area and explicit defaults. Avoid vendor-specific flags in shared docs unless strictly required.

## Status notes

Pending R1 completion.

