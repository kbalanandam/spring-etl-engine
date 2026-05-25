# F1 â€” Define restart semantics per execution mode

## Summary

Define what restartable execution should mean for the selected-job runtime so rerun, resume, and recovery expectations stop being implicit or accidental.

## Current board status

- Epic: **[Epic F](../epics/epic-f-restartability-and-recovery-semantics.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **A1, C1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

As the runtime grows beyond simple one-shot file runs, teams need a clear answer for what can be rerun safely, what state matters, and what evidence makes restart behavior trustworthy.

## Goal

Document explicit restart semantics per execution mode before adding restart features piecemeal.

## Scope

- define restart expectations for current execution modes
- identify the runtime state, artifacts, and evidence needed for safe restart behavior
- align recovery semantics with explicit job/step orchestration

## Out of scope

- full control-plane persistence implementation
- broad retry policy design
- transport-specific replay semantics

## Proposed approach

Start with a design/documentation slice that clarifies restart boundaries before changing the active runtime behavior.

## Operator / runtime impact

- operators gain clearer expectations for rerun vs restart
- future scheduler/control-plane work can align with one explicit restart contract
- documentation and verification can reason about recovery more safely

## Acceptance criteria

- [ ] one explicit restart-semantics model exists for the main execution modes
- [ ] required state/artifact/evidence expectations are documented
- [ ] future restart implementation work can reference one stable contract

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Runtime flow`](../../architecture/etl-core/runtime-flow.md)
- [`Job history and operational observability`](../../architecture/control-plane/job-history-and-operational-observability.md)
- [`Control-plane operational data model`](../../architecture/control-plane/control-plane-operational-data-model.md)

## Implementation notes

Keep the first slice design-oriented; the product needs explicit restart semantics before code-level restart features.

## Status notes

Deferred until orchestration, evidence, and retained-state direction are clearer.

