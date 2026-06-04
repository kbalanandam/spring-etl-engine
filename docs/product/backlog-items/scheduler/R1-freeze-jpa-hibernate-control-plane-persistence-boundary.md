# R1 - Freeze JPA/Hibernate control-plane persistence boundary

## Summary

Freeze one explicit persistence boundary for optional control-plane history so JPA/Hibernate portability work can proceed without changing the selected-job ETL runtime contract.

## Current board status

- Epic: **[Epic R](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M3**
- Dependency: **S1, S4**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Current control-plane persistence behavior is implemented through SQLite-oriented JDBC SQL, making cross-database support difficult and creating risk of accidental coupling to one engine.

## Goal

Define and approve persistence boundaries, invariants, and non-goals before implementation so portability work stays coherent and optional.

## Scope

- define bounded context for retained control-plane history persistence
- freeze non-goals (no ETL launch-contract change, no mandatory control-plane DB)
- define parity expectations for current `RunSummaryRegistry`/read-model semantics
- define phased delivery guardrails for R2-R5

## Out of scope

- implementing entities/repositories
- choosing final production database vendor defaults
- changing scheduler overlap or restart semantics

## Proposed approach

Capture a short architecture/product decision set that pins boundary terms, keeps optional-control-plane behavior explicit, and defines what must remain behaviorally unchanged while implementation moves from JDBC SQL to JPA/Hibernate.

## Operator / runtime impact

- operators keep current selected-job run behavior
- persistence remains additive and optional
- deployment expectations become clearer before implementation starts

## Trade-off Snapshot

- Decision: freeze boundary first, implementation second
- Benefit: avoids partial portability rewrites and contract drift
- Cost: small up-front planning cycle
- Risk: over-specification can slow first code slice
- Use when: introducing multi-engine persistence support
- Avoid when: issue is a narrow bug fix with no boundary impact
- Default: preserve current runtime contract and observability semantics
- Evidence: approved Epic R + R1 scope and acceptance checklist

## Acceptance criteria

- [ ] a clear boundary statement exists for optional control-plane persistence vs ETL worker runtime
- [ ] non-goals explicitly prevent launch-contract changes and mandatory control-plane DB coupling
- [ ] parity invariants for retained run/step/artifact/recovery read models are documented
- [ ] R2-R5 phase sequencing and guardrails are documented and linked

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic R`](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)
- [`ADR-0014: freeze JPA/Hibernate control-plane persistence boundary`](../../../adr/control-plane/0014-freeze-jpa-hibernate-control-plane-persistence-boundary.md)
- [`Control plane and worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)
- [`S4 - Control-plane operational data model`](S4-control-plane-operational-data-model.md)

## Implementation notes

Use R1 as a freeze gate. Do not merge substantial portability implementation before this item's scope and invariants are agreed.

## Status notes

Pending kickoff.


