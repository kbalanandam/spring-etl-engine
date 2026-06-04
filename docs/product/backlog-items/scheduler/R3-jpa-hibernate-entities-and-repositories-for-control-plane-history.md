# R3 - JPA/Hibernate entities and repositories for control-plane history

## Summary

Implement a JPA/Hibernate persistence path for retained control-plane history (trigger/run/step/artifact/recovery anchors) while preserving current read-model semantics and external IDs.

## Current board status

- Epic: **[Epic R](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M3**
- Dependency: **R1, R2, S4**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Current control-plane persistence logic relies on engine-specific SQL and startup DDL behavior, which limits portability and increases maintenance cost.

## Goal

Provide a portable ORM-backed persistence implementation behind existing service interfaces without changing runtime-flow contracts.

## Scope

- define entity mappings for retained control-plane history tables
- define repositories and transactional write patterns
- preserve stable external IDs and read-model contracts
- introduce implementation-level compatibility bridge where needed during migration

## Out of scope

- redesigning API contracts
- changing run/step observability semantics
- forcing ETL worker startup to require control-plane persistence

## Proposed approach

Add a phased JPA-backed registry path behind existing interfaces, keep behavior parity tests, and retain controlled fallback until parity is proven.

## Operator / runtime impact

- no expected behavioral change for selected-job ETL launches
- optional control-plane reads/writes become DB-portable
- operational evidence fields remain consistent

## Trade-off Snapshot

- Decision: migrate persistence internals behind existing interfaces
- Benefit: multi-engine support without API churn
- Cost: temporary dual-path complexity during migration
- Risk: subtle parity regressions across historical fields
- Use when: replacing engine-specific persistence internals
- Avoid when: contract-level redesign is also in scope
- Default: preserve read-model output shape and semantics
- Evidence: parity tests for run summary, step/artifact, and recovery views

## Acceptance criteria

- [ ] JPA entities/repositories cover retained control-plane history needed by existing read-model APIs
- [ ] stable external IDs and current response shapes are preserved
- [ ] behavior parity tests pass for run summary, step/artifact, and recovery lookup paths
- [ ] ETL worker direct execution remains independent from control-plane persistence

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic R`](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)
- [`S4 - Control-plane operational data model`](S4-control-plane-operational-data-model.md)
- [`Job history and operational observability`](../../../architecture/control-plane/job-history-and-operational-observability.md)

## Implementation notes

Treat this as an internal persistence swap with parity gates. Avoid widening feature scope beyond repository portability.

## Status notes

Pending R1/R2 completion.

