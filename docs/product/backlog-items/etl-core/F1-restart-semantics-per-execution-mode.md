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

- [x] **D1 advisory recovery semantics freeze** - one explicit semantics note for advisory recovery evidence (`attempt_link`, `checkpoint_anchor`, `resumeSupported=false`)
- [x] **D2 resume eligibility rules freeze** - one explicit rule set that explains when resume is ineligible vs future-eligible without changing shipped runtime behavior
- [x] **D3 idempotent rerun boundary freeze** - one explicit rerun safety boundary per target pattern before any resume execution work is proposed

### D1 canonical advisory recovery semantics

Treat retained recovery data as **diagnostic lineage only** in the current shipped runtime.

- `attempt_link` identifies retained run-to-run lineage when a stored recovery row exists; it explains relationship, not executable resume eligibility.
- `checkpoint_anchor` identifies retained evidence anchors for the run (for example a `RUN_LOG` anchor and later step-linked anchors where available); it does not represent a resumable checkpoint contract yet.
- `resumeSupported` is always `false` in the current shipped runtime.
- `resumeBlockedReason` must remain explicit and operator-facing: `resume-from-checkpoint is not supported in the current shipped runtime; rerun-from-start remains the active execution boundary.`
- when retained recovery rows are missing but the run itself exists, the API still returns a deterministic advisory payload rather than a missing-resource response:
  - `runRecordId` defaults to `rr-<jobExecutionId>`
  - attempt-lineage fields remain `null`
  - checkpoint anchors fall back to one `RUN_LOG` anchor when `RUN_SUMMARY.logPath` is available, otherwise an empty list
- this endpoint remains evidence-first and advisory-only; it does not widen the shipped execution boundary beyond `rerun-from-start`

### D2 canonical resume-eligibility rules

Treat `runMode` as **resolved observability metadata** and `recoveryPolicy` as **authored or defaulted restart intent evidence**. Neither field by itself authorizes checkpoint-resume execution in the shipped runtime.

| Resolved `runMode` | Authored/defaulted `recoveryPolicy` | Current shipped behavior | Resume eligibility now |
| --- | --- | --- | --- |
| `explicit-job` | `rerun-from-start` | Allowed. The whole selected ordered scenario reruns from the beginning. | Ineligible for checkpoint resume; rerun-only behavior is the shipped boundary. |
| `explicit-job` | `resume-from-checkpoint` (or authored alias `restart`) | Not allowed. Selected-run startup/runtime descriptor assembly fails fast as unsupported. | Ineligible. The runtime rejects this policy before execution starts. |
| `demo-fallback` | `rerun-from-start` | Allowed. Demo/local compatibility runs rerun from the beginning. | Ineligible for checkpoint resume; demo mode does not widen restart semantics. |
| `demo-fallback` | `resume-from-checkpoint` | Not a shipped execution lane. Demo fallback still remains rerun-only behavior. | Ineligible. No shipped demo-fallback path enables checkpoint resume. |

Operator rules:

- `resumeSupported=false` is the stable current answer for every shipped execution mode.
- `resumeBlockedReason` explains the product boundary, not a per-run transient failure.
- authored aliases normalize to canonical evidence tokens (`rerun` -> `rerun-from-start`, `restart` -> `resume-from-checkpoint`) but alias acceptance does not imply shipped resume support.
- the recovery endpoint remains diagnostic-only even when retained lineage/anchor rows exist.

Future-gated conditions before any run could become resume-eligible:

1. D1 advisory recovery semantics stay frozen and portability-safe.
2. D3 idempotent rerun boundary is documented by target behavior.
3. checkpoint state shape, replay preconditions, and operator release-gate evidence are explicitly defined in a later Epic F slice.
4. a shipped runtime path exists that does more than fail fast for `resume-from-checkpoint`.

### D3 canonical idempotent rerun boundary

Treat rerun safety as a **target-pattern classification**, not as a blanket promise for every failed run.

| Target pattern | Current shipped rerun boundary | Operator interpretation now |
| --- | --- | --- |
| File target only (`csv` / `json` / `xml`) | Conditionally rerun-safe when the scenario treats the final output path as replaceable/publish-on-success output and no external side effect is committed before successful completion. | Rerun-from-start is the shipped recovery action, but teams must still own final-path overwrite/versioning conventions. |
| Relational target (`format: relational`, current insert-first baseline) | Not assumed idempotent by default. A partial or repeated rerun can duplicate rows unless the target contract adds its own protections outside the current shipped F1 boundary. | Operators should treat rerun as potentially duplicating writes until a later slice freezes target-specific idempotent load patterns. |
| Mixed handoff (`step -> intermediate artifact -> downstream step`) | Safe only when each handoff artifact is reproducible from the beginning of the ordered plan and each downstream final target also satisfies its own rerun boundary. | A rerun restarts the whole selected scenario; it does not resume from an intermediate handoff checkpoint. |

Current D3 rules:

- staged file publication improves rerun safety for file outputs because final artifacts are promoted only after successful step completion, but that does not by itself define cross-run overwrite/versioning policy
- archive/reject evidence helps diagnosis and replay decisions, but evidence presence does not make a target idempotent automatically
- the current relational baseline is still insert-oriented first; F1 therefore does not claim database-target reruns are inherently duplicate-safe
- intermediate handoff artifacts are diagnostic and reproducible-flow evidence, not resumable restart checkpoints

Preconditions before any future resume-execution design starts:

1. rerun safety is frozen per target pattern, including which targets are not idempotent by default
2. step/target publication semantics are explicit enough to distinguish replaceable output from duplicate-risk output
3. retained run/step/artifact lineage can prove which side effects were already published
4. scheduler/control-plane follow-on work continues to treat rerun-only execution as the active boundary until those target-specific guarantees are explicitly shipped

### F1 continuation execution checklist

#### Scope guardrails

- [x] keep checkpoint-resume execution unshipped (`resumeSupported=false` remains explicit)
- [x] keep selected-job launch semantics unchanged (`etl.config.job` boundary)
- [x] keep Epic R persistence portability implementation parked during F1 continuation

#### D1 - Advisory recovery semantics freeze

- [x] define one canonical semantics note for `attempt_link` and `checkpoint_anchor` advisory evidence
- [x] align wording for advisory recovery behavior across F1, runtime-flow, and control-plane API docs
- [x] confirm deterministic fallback semantics when no retained recovery row exists
- [x] confirm `resumeBlockedReason` guidance stays explicit and operator-facing

#### D2 - Resume-eligibility rules freeze

- [x] define explicit ineligible/eligible-later rule set for resume
- [x] document why shipped runtime still reports `resumeSupported=false`
- [x] align guardrail wording with fail-fast `resume-from-checkpoint` behavior
- [x] add one rule table that maps authored `runMode`/`recoveryPolicy` to shipped behavior

#### D3 - Idempotent rerun boundary freeze

- [x] document rerun safety boundary by target pattern (file, relational, mixed handoff)
- [x] document preconditions before any future resume-execution design is considered
- [x] define release-gate evidence expectations for any future transition beyond rerun-only behavior
- [x] link rerun boundary guidance to scheduler/control-plane follow-on notes that depend on F1 decisions

#### Verification and handoff

- [ ] link focused F1 continuation tests/evidence to this page as they are added
- [x] update `product-backlog.md` notes when D1/D2/D3 are closed
- [ ] confirm docs/backlog/changelog consistency before opening resume-execution design work

## Operator / runtime impact

- operators gain clearer expectations for rerun vs restart
- future scheduler/control-plane work can align with one explicit restart contract
- documentation and verification can reason about recovery more safely

## Acceptance criteria

- [x] one explicit restart-semantics model exists for the main execution modes
- [x] required state/artifact/evidence expectations are documented
- [x] future restart implementation work can reference one stable contract
- [x] advisory recovery semantics are documented consistently across F1, runtime flow, and control-plane API docs
- [x] explicit resume-eligibility rules are documented with no implied shipped checkpoint-resume execution
- [x] idempotent rerun boundary guidance is documented for follow-on Epic F planning

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

D1 advisory recovery semantics freeze completed: F1, runtime-flow, and control-plane API docs now use one explicit advisory-only contract for retained lineage/anchor evidence, deterministic fallback payloads, and operator-facing `resumeBlockedReason` wording.

D2 resume-eligibility rules freeze completed: `runMode` and `recoveryPolicy` now map to one explicit rerun-only shipped behavior matrix, with unsupported `resume-from-checkpoint` remaining fail-fast guarded and no implied checkpoint-resume execution.

D3 idempotent rerun boundary freeze completed: file, relational, and mixed-handoff target patterns now have one explicit rerun-safety classification, and scheduler/control-plane follow-on work remains downstream of those rerun-only boundaries.

Priority sequencing update: `F1` continuation work is the active near-term lane; portability implementation under `Epic R` remains parked after the current docs-freeze anchors.

