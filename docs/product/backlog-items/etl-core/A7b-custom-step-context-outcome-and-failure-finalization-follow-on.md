# A7b - Extend custom-step context, outcome mapping, and failure-finalization contract

## Summary

Follow-on to `A7` that delivers the deferred custom-step runtime contracts for typed context handoff, explicit outcome mapping, and bounded failure-finalization behavior.

## Current board status

- Epic: **[Epic A](../../epics/etl-core/epic-a-runtime-contract-and-model-governance.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M3**
- Dependency: **A7, D1**

## Problem

`A7` phase-1 shipped custom-step declarations and ordered execution, but intentionally deferred typed cross-step context validation, explicit `CONTINUE/STOP/FAIL` semantics, and generalized failure-finalization callbacks.

## Goal

Complete the deferred runtime contracts so custom and standard steps share one explicit operator-visible behavior model for:

- context publish/consume ownership and fail-fast validation
- deterministic outcome mapping and job-status implications
- bounded failure-finalization hooks for audit/header update patterns

## Scope

This item covers:

- typed context handoff contract (`publish`/`consume`) with required-key/type checks
- explicit `CONTINUE`, `STOP`, and `FAIL` outcome mapping semantics
- bounded custom failure-finalizer contract invoked on upstream failures
- one preserved runnable scenario proving header/detail + failure-finalization behavior
- focused startup/runtime tests and structured evidence expectations

## Out of scope

- introducing a second orchestration model
- replacing the explicit ordered `steps[]` runtime contract
- scheduler/control-plane feature expansion beyond launch parity

## Acceptance criteria

- [ ] context handoff validates required keys and type expectations before dependent step execution
- [ ] context key ownership and overwrite behavior are deterministic and documented
- [ ] custom-step outcomes map through one explicit `CONTINUE` / `STOP` / `FAIL` contract
- [ ] job-level status implications for mapped outcomes are documented and tested
- [ ] bounded failure-finalizer contract executes for upstream-failure paths
- [ ] at least one preserved runnable bundle demonstrates header/detail with failure-finalization behavior
- [ ] architecture/config docs are synchronized with shipped behavior

## Related docs

- [`A7`](A7-custom-step-pairing-context-handoff-and-failure-contract.md)
- [`Product backlog`](../../product-backlog.md)
- [`Custom-step pairing and context handoff`](../../../architecture/etl-core/custom-step-pairing-and-context-handoff.md)
- [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)

