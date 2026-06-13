# A7 - Add custom-step pairing, context handoff, and failure-contract baseline

## Summary

Define one additive runtime contract that lets customer-owned custom steps run before or after standard OneFlow steps while still using the same ordered `job-config.yaml` plan, failure semantics, and operator evidence model.

## Current board status

- Epic: **[Epic A](../../epics/etl-core/epic-a-runtime-contract-and-model-governance.md)**
- Priority: **P1**
- Status: **In Progress**
- Milestone: **M2**
- Dependency: **A1, D1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Customers need scenario-local behavior that the shared runtime should not hardcode, such as writing source-file header audit rows before loading detail rows, carrying generated `fileId` into standard write steps, and updating status to `FAILED` whenever any downstream step fails.

## Goal

Support customer-owned custom step logic as a first-class step type that is paired with standard OneFlow steps through one shared runtime contract for ordering, context, failure handling, and observability.

## Scope

This item covers:

- explicit `job-config.yaml` authored custom steps in ordered `steps[]`
- pre-step and post-step custom hooks around standard steps
- cross-step context handoff for values such as `fileId`
- a stable custom-step outcome contract that maps to job continuation/stop/fail behavior
- class-level design anchors for config, factory dispatch, context bridge, and outcome mapping
- failure-category alignment for config/binding/execution/context errors
- preserved example bundles for header/detail database patterns

## Out of scope

This item does not cover:

- scenario auto-discovery
- replacing the current flat ordered Spring Batch runtime with nested executable subflows
- scheduler/control-plane trigger orchestration
- broad stored-procedure framework design beyond this custom-step seam
- reopening deprecated legacy validation path behavior

## Proposed approach

Preferred architecture direction:

1. extend step config with a `kind` discriminator (`standard` or `custom`) while preserving explicit order
2. keep one runtime assembly path in `BatchConfig`; resolve standard steps through existing factories and custom steps through a new `DynamicCustomStepFactory`
3. define `CustomStepProvider` and `CustomStepHandler` SPIs for customer-owned code registration and execution
4. use one shared context bridge (`JobExecutionContext`/`StepExecutionContext`) so custom output keys can feed standard step input bindings
5. normalize custom outcomes into one shared runtime action model (`CONTINUE`, `STOP`, `FAIL`) before step-flow decisions
6. guarantee final status update hooks for failure paths through a bounded job-level completion listener/failure handler contract

## Phase-1 canonical config contract

The first implementation slice keeps the authored custom-step identity intentionally narrow:

- `steps[].name` remains the operator-visible step identity used for plan/logging only
- `steps[].kind` is optional; omission defaults to `standard`
- `steps[].kind: custom` requires `steps[].custom.type`
- `steps[].custom.type` is the only runtime binding key used to resolve a `CustomStepProvider`
- `steps[].source` / `steps[].target` remain the standard-step-only contract and are not required on `kind: custom`

Phase-1 non-goals for identity:

- no additional `taskRef` field
- no dual-binding model where both `name` and another identity key resolve providers
- no trigger-position sub-model in the first slice; custom step placement is expressed only by ordered `steps[]`

## Locked review criteria

A7 implementation and review should follow the canonical invariants in:

- [`A7 Architecture Invariants`](../../../architecture/etl-core/custom-step-pairing-and-context-handoff.md#a7-architecture-invariants)

Use those invariants as the source of truth when evaluating compatibility, observability, scheduling parity, completion behavior, and scale boundaries.

## Class-level design discussion

Proposed class anchors (names are design-level and may be finalized during implementation):

- `JobConfig.JobStepConfig` extension: add optional `kind` and `custom` blocks for authored custom-step metadata
- `CustomStepConfig`: typed config object for `type` plus optional context/result metadata (`publish`, `consume`, `onResult`, and provider-owned `config`)
- `DynamicCustomStepFactory`: central dispatch for custom-step provider selection
- `CustomStepProvider`: registration boundary for customer-owned step types
- `CustomStepHandler`: executable custom-step contract for user code
- `CustomStepContextBridge`: read/write boundary for context keys shared with standard steps
- `CustomStepOutcomeMapper`: maps handler outcomes to Spring Batch `ExitStatus`/flow actions
- `CustomStepFailureFinalizer`: bounded failure callback that updates header/audit status when any upstream step fails

## Exception category discussion

Proposed failure categories and exception boundaries:

- `config`: malformed custom-step YAML, missing required fields, invalid field combinations
  - proposed type: `CustomStepConfigException` (extends `ConfigException`)
- `binding`: unknown custom step `type`, duplicate provider conflicts, ambiguous registration
  - proposed type: `CustomStepBindingException` (extends `ConfigException`)
- `context`: missing required context key (for example `header.fileId`), type mismatch, unsafe overwrite
  - proposed type: `CustomStepContextException` (runtime categorized as step-execution failure)
- `execution`: customer handler SQL/IO/business failure
  - proposed type: `CustomStepExecutionException` (runtime categorized, preserves original cause)

This item should align with Epic D taxonomy so operator evidence can distinguish launch/config failures from runtime custom-step failures.

## Context shifting between custom and standard steps

Context-handoff rule set:

- custom steps can publish shared values under explicit namespaced keys (example: `header.fileId`)
- standard steps can bind to those keys through declared context bindings only
- context keys are immutable by default after first publish unless explicit override is allowed
- missing required keys fail fast before standard step execution starts
- every context publish/consume event should emit structured step evidence

## Preserved examples planned under `config-jobs/`

- `src/main/resources/config-jobs/csv-to-relational-with-header-status/`
  - custom pre-step creates header row and publishes `header.fileId`
  - standard `csv -> relational` detail load writes child rows with FK `file_id`
  - custom finalize step updates header to `SUCCESS` on happy path
  - failure finalizer updates header to `FAILED` when any step fails

- `src/main/resources/config-jobs/xml-to-csv-with-custom-run-audit/`
  - custom pre-step writes run-audit start marker
  - standard XML read/process/write runs unchanged
  - custom post-step writes completion marker and summarized counters

## Operator / runtime impact

Expected impact when this item ships:

- customers can add bounded custom logic without forking core runtime behavior
- step order remains explicit and reviewable in one `job-config.yaml`
- parent/header to child/detail FK patterns become reusable and less ad hoc
- failure visibility improves with consistent category/event reporting for custom and standard steps
- docs and preserved bundles become executable references for custom-step pairing patterns

## Trade-off Snapshot

- Decision: add first-class custom-step seam instead of ad hoc listener-side customization
- Benefit: deterministic pairing between customer-owned and standard steps with one runtime contract
- Cost: added config/schema, factory, SPI, and error-taxonomy complexity
- Risk: misconfigured context keys or provider binding can block startup/execution
- Use when: customer flow needs pre/post side effects or context handoff not covered by default steps
- Avoid when: behavior can be expressed safely in existing source/processor/target contracts
- Default: keep custom steps optional and disabled by omission; standard-only flows stay unchanged
- Evidence: preserved `config-jobs` examples plus focused startup/runtime tests and step-event logs

## Known cons and mitigation plan

1. **Step identity drift risk across docs and implementations**
   - Con: if `name` and `custom.type` responsibilities are not kept distinct, provider binding and observability can diverge.
   - Mitigation: lock one identity split for phase-1 (`name` for operator evidence, `custom.type` for provider binding) and reject alternate keys such as `taskRef` in this slice.
2. **Reduced config transparency with minimal custom-step YAML**
   - Con: operators cannot infer task internals from `job-config.yaml` alone.
   - Mitigation: require additive structured step evidence (`stepKind`, `customType`, mapped action, context keys used).
3. **Provider registration dependency**
   - Con: classpath/provider conflicts can block startup.
   - Mitigation: deterministic provider conflict policy, fail-fast `binding` errors, and startup diagnostics naming the unresolved `custom` type.
4. **Context handoff fragility**
   - Con: missing or type-mismatched keys can fail downstream steps.
   - Mitigation: namespaced keys, write-once default, required-key/type validation before dependent execution.
5. **Potential semantics drift between custom and standard paths**
   - Con: ad hoc custom result handling can diverge from standard job completion behavior.
   - Mitigation: enforce centralized `CONTINUE` / `STOP` / `FAIL` outcome mapping only.
6. **Scale and operability risk for heavy custom logic**
   - Con: long-running or stateful custom steps can reduce throughput and restart clarity.
   - Mitigation: keep context payloads lightweight (IDs/refs), keep handlers bounded/stateless where possible, and externalize heavy work.

## Pre-implementation multi-review workflow

A7 should complete these reviews before implementation starts:

1. **Runtime contract review (ETL core)**
   - confirm A7 invariants and backward compatibility for standard-only jobs
   - confirm one assembly path in `BatchConfig` and one outcome mapper contract
   - artifact links: [`A7 architecture invariants`](../../../architecture/etl-core/custom-step-pairing-and-context-handoff.md#a7-architecture-invariants), [`Runtime flow`](../../../architecture/etl-core/runtime-flow.md)
2. **Scheduler/control-plane review**
   - confirm scheduled runs and manual runs use the same selected-job launch contract
   - confirm no second orchestration or launch model is introduced for custom steps
   - artifact links: [`Scheduler architecture direction`](../../../architecture/control-plane/scheduler-architecture-direction.md), [`Control-plane and ETL worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)
3. **UI/operations review**
   - confirm monitoring evidence parity for custom and standard steps
   - confirm run/step troubleshooting remains consistent with existing operator evidence model
   - artifact links: [`Operator UI architecture direction`](../../../architecture/operator-ui/operator-ui-architecture-direction.md), [`Operator UI MVP API surface`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

Expected review artifacts:

- invariant checklist with pass/fail notes
- scheduler/control-plane review notes referencing selected-job launch parity checks
- UI/operations review notes referencing run/step evidence parity checks
- known-cons decisions and chosen mitigations
- updated docs links across runtime/config/product notes

## Balanced delivery lane handshake

This item is the ETL leg of the current balanced-growth lane:

- `A7` = ETL/runtime contract proof
- `S2` = scheduler/control-plane pause-resume baseline
- `U4` = operator-facing schedule visibility and guarded controls

Cross-track review expectation before implementation expands:

- A7 must not introduce a second launch or orchestration model that `S2` or `U4` would need to special-case
- scheduler-triggered runs and UI-exposed controls must continue to target the same selected-job boundary used by standard ETL execution
- custom-step evidence must stay compatible with the operator read-model assumptions already used by the UI lane

## Acceptance criteria

- [ ] pre-implementation multi-review workflow is completed (runtime, scheduler/control-plane, UI/operations)
- [ ] known design cons are reviewed with explicit mitigation decisions captured in this item
- [ ] existing standard-only jobs (with omitted `steps[].kind`) remain fully backward compatible
- [ ] `steps[].kind` omission defaults to `standard` without changing standard step semantics
- [ ] `job-config.yaml` supports explicit custom-step declarations without changing standard-step behavior
- [ ] custom and standard steps run through one ordered runtime assembly path in `BatchConfig`
- [ ] context handoff supports required-value fail-fast checks and deterministic key ownership
- [ ] outcome mapping supports `CONTINUE`, `STOP`, and `FAIL` with documented job-status implications
- [ ] failure finalization updates header/audit status when any upstream step fails
- [ ] exception categories are documented and aligned with Epic D taxonomy direction
- [ ] at least one preserved runnable bundle demonstrates header/detail + failure-finalization behavior

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic A - Runtime contract and generated-model governance`](../../epics/etl-core/epic-a-runtime-contract-and-model-governance.md)
- [`Epic D - Error taxonomy and failure categorization`](../../epics/etl-core/epic-d-error-taxonomy-and-failure-categorization.md)
- [`Extension points`](../../../architecture/etl-core/extension-points.md)
- [`ETL custom-step pairing and context handoff`](../../../architecture/etl-core/custom-step-pairing-and-context-handoff.md)
- [`A7 + T16 extensibility charter`](../../../architecture/etl-core/a7-t16-extensibility-charter.md)
- [`Job config reference`](../../../config/job-config.md)

## Implementation notes

Keep the first slice narrow: one custom-step seam, one context-bridge rule set, one failure-finalizer contract, and one preserved relational header/detail scenario. Do not expand to broad workflow orchestration in the same slice.

## Status notes

- Initial backlog proposal captured customer pre/post custom-step requests, FK handoff (`fileId`) patterns, and failure-finalization needs.
- Phase-1 runtime/config slice now supports ordered `steps[].kind` with `kind: custom` provider binding via `custom.type` while preserving standard-step defaults when `kind` is omitted.
- Current shipped scope is intentionally narrow: custom steps do not yet include full context bridge, `CONTINUE/STOP/FAIL` outcome mapping, or failure finalizer hooks.


