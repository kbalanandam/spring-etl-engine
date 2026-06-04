# F1 - Define restart semantics per execution mode

## Summary

Define what restartable execution should mean for the selected-job runtime so rerun, resume, and recovery expectations stop being implicit or accidental.

## Current board status

- Epic: **[Epic F](../../epics/etl-core/epic-f-restartability-and-recovery-semantics.md)**
- Priority: **P1**
- Status: **In Progress**
- Milestone: **M2**
- Dependency: **A1, C1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

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

### Phase-1 contract baseline (current)

The first F1 slice is contract-first and evidence-first:

- define one explicit restart matrix for current execution modes (`runMode`, `recoveryPolicy`)
- keep shipped behavior on `rerun-from-start` semantics for selected-job runs
- treat checkpoint resume semantics as future work gated by follow-on F1 slices
- keep scheduler overlap/run-state governance aligned to this contract rather than introducing implicit retry/restart behavior

This baseline intentionally does not add new restart execution behavior yet.

## Operator / runtime impact

- operators gain clearer expectations for rerun vs restart
- future scheduler/control-plane work can align with one explicit restart contract
- documentation and verification can reason about recovery more safely

## Acceptance criteria

- [x] one explicit restart-semantics model exists for the main execution modes
- [x] required state/artifact/evidence expectations are documented
- [x] future restart implementation work can reference one stable contract

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)
- [`Job history and operational observability`](../../../architecture/control-plane/job-history-and-operational-observability.md)
- [`Control-plane operational data model`](../../../architecture/control-plane/control-plane-operational-data-model.md)

## Implementation notes

Keep the first slice design-oriented; the product needs explicit restart semantics before code-level restart features.

Current shipped baseline: selected-job runs continue to favor deterministic `rerun-from-start` behavior, with run evidence carrying `runMode` and `recoveryPolicy` context.

## Status notes

Phase-1 contract baseline started: restart expectations are now explicit and scoped to current runtime behavior, with code-level resume semantics deferred.

Phase-1.3 guardrail started: descriptor assembly now fails fast when `resume-from-checkpoint` is selected, preserving the shipped `rerun-from-start` runtime boundary until checkpoint restart semantics are explicitly implemented.

Phase-1.4 contract wiring started: selected `job-config.yaml` now carries optional `recoveryPolicy` into runtime descriptor/readiness metadata so operator evidence reflects authored intent while unsupported resume behavior remains fail-fast guarded.

Preserved-bundle alignment continued: executable `config-jobs/*/job-config.yaml` examples now declare `recoveryPolicy: rerun-from-start` explicitly so shipped references match the active F1 baseline contract.

