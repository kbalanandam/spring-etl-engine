# R4 - Cross-RDBMS schema migration baseline

## Summary

Introduce one schema migration/versioning baseline for control-plane persistence that is portable-first across major RDBMS engines and explicit about vendor-specific deltas.

## Current board status

- Epic: **[Epic R](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M3**
- Dependency: **R2, R3**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Runtime-managed DDL and engine-specific SQL patterns are hard to evolve safely across multiple database vendors.

## Goal

Adopt deterministic migration/versioning behavior for schema changes across supported engines.

## Scope

- choose and apply one migration governance approach
- define baseline schema migrations for control-plane persistence
- isolate vendor-specific SQL to explicit per-engine migrations only when required
- define upgrade guidance from existing SQLite-first states

## Out of scope

- full historical data migration automation for every legacy state
- non-relational persistence targets
- scheduler policy redesign

## Proposed approach

Use versioned migrations with portable core DDL first, then explicit vendor-specific delta scripts as exceptions.

## Operator / runtime impact

- schema upgrades become auditable and repeatable
- startup behavior becomes less dependent on ad hoc runtime DDL
- deployment teams gain clearer rollback/forward procedures

## Trade-off Snapshot

- Decision: explicit migrations over runtime schema mutation
- Benefit: safer upgrades and cross-engine governance
- Cost: added migration maintenance overhead
- Risk: incomplete vendor delta scripts can block rollout
- Use when: supporting multiple relational engines across environments
- Avoid when: persistence layer remains prototype-only
- Default: portable schema first; isolate engine-specific deltas
- Evidence: migration dry-run checks and integration tests per supported engine profile

## Acceptance criteria

- [ ] baseline migration history exists for retained control-plane schema
- [ ] vendor-specific deltas are isolated and documented
- [ ] startup no longer depends on broad runtime schema mutation for migrated environments
- [ ] upgrade path from existing SQLite-first deployments is documented

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic R`](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)
- [`Control-plane local relational schema`](../../../architecture/control-plane/control-plane-local-relational-schema.md)
- [`release-planning-and-delivery-control.md`](../../release-planning-and-delivery-control.md)

## Implementation notes

Keep migration naming/versioning deterministic and CI-friendly. Avoid hidden schema rewrites.

## Status notes

Pending R2/R3 completion.

