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

### Phase-2 contract hardening (next)

Phase-2 stays docs-first and evidence-first while retaining shipped execution behavior:

- define advisory recovery semantics for retained `attempt_link` and `checkpoint_anchor` evidence
- define explicit resume-eligibility rules and why the current shipped runtime still reports `resumeSupported=false`
- define one portability-safe parity checklist for `runMode`/`recoveryPolicy` evidence across local and control-plane views

### Phase-3 execution readiness boundary (later)

Phase-3 remains gated and should only start after Phase-2 semantics are frozen:

- define idempotent rerun boundary by target type and step behavior
- define execution-mode-specific preconditions before any checkpoint-resume implementation is considered
- define release-gate evidence requirements for any future transition away from rerun-only behavior

### Top F1 continuation deliverables

- [ ] **D1 advisory recovery semantics freeze** - one explicit semantics note for advisory recovery evidence (`attempt_link`, `checkpoint_anchor`, `resumeSupported=false`)
- [ ] **D2 resume eligibility rules freeze** - one explicit rule set that explains when resume is ineligible vs future-eligible without changing shipped runtime behavior
- [ ] **D3 idempotent rerun boundary freeze** - one explicit rerun safety boundary per target pattern before any resume execution work is proposed

## Operator / runtime impact

- operators gain clearer expectations for rerun vs restart
- future scheduler/control-plane work can align with one explicit restart contract
- documentation and verification can reason about recovery more safely

## Acceptance criteria

- [x] one explicit restart-semantics model exists for the main execution modes
- [x] required state/artifact/evidence expectations are documented
- [x] future restart implementation work can reference one stable contract
- [ ] advisory recovery semantics are documented consistently across F1, runtime flow, and control-plane API docs
- [ ] explicit resume-eligibility rules are documented with no implied shipped checkpoint-resume execution
- [ ] idempotent rerun boundary guidance is documented for follow-on Epic F planning

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

Phase-1.5 operator filtering started: `/api/v1/runs` now accepts optional `runMode` and `recoveryPolicy` filters, and Operator Runs UI passes those filters through route state and API requests for evidence-focused run triage.

Phase-1.3 guardrail coverage expanded: selected-run config metadata and runtime-descriptor assembly paths now have explicit fail-fast regression coverage for unsupported `resume-from-checkpoint`.

Phase-1 contract ergonomics continued: selected `job-config.yaml` now accepts short `recoveryPolicy` aliases (`rerun`, `restart`) that normalize to canonical runtime evidence tokens while preserving the current fail-fast guardrail for unsupported checkpoint resume execution.

Phase-2 advisory recovery view started: `/api/v1/runs/{jobExecutionId}/recovery` now exposes retained `attempt_link` and `checkpoint_anchor` evidence as advisory-only diagnostics; `resumeSupported=false` remains explicit while checkpoint-resume execution is not shipped.

