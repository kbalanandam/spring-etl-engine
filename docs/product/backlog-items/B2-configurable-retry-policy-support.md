# B2 - Introduce configurable retry policy support where appropriate

## Summary

Add retry behavior only where the runtime can distinguish transient failures from deterministic configuration or data failures.

## Current board status

- Epic: **[Epic B](../epics/epic-b-runtime-hardening-and-file-behavior.md)**
- Priority: **P1**
- Status: **In Progress**
- Milestone: **M1**
- Dependency: **B1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

A retry feature is useful only when it improves resilience. Applied too broadly, it can hide permanent failures, duplicate side effects, or delay clearer fail-fast diagnosis.

The immediate risk is mixing transient operational faults (where retry helps) with deterministic failures (where retry only increases noise and latency).

## Goal

Define where retry behavior is appropriate and observable in the selected-job runtime.

## Scope

- retry-policy contract for supported transient failure classes
- operator-visible retry evidence
- boundaries that prevent retry from masking deterministic config/data errors
- educational examples that show safe retry placement on explicit step boundaries

## Out of scope

- retry-everything defaults
- scheduler-level re-trigger policy
- full restartability design
- broad idempotency redesign across all connectors

## Proposed approach

Build retry only after skip/failure semantics are clearer, and limit it to runtime boundaries where repeated attempts are safe and meaningful.

Use one bounded contract for the selected-job runtime:

1. retry only for explicitly categorized transient failures
2. require bounded attempts and backoff policy
3. fail fast when retry budget is exhausted
4. emit retry attempt evidence and final status in operator-visible logs

## Educational examples (pre-implementation)

### Example A - Transient target connectivity failure (retry candidate)

Context:

- step `load-customers` writes to relational target
- temporary network timeout occurs during write

Teaching point:

- this is a classic retry candidate if idempotency/transaction semantics are acceptable
- retries must be capped (for example, `maxAttempts`) with delay/backoff
- final evidence must show attempt count and terminal result (`succeeded_after_retry` or `failed_after_retries`)

### Example B - Validation rule failure (not a retry candidate)

Context:

- processor `rules[]` marks record invalid (`notNull`, `timeFormat`, duplicate rule outcomes)

Teaching point:

- deterministic data-quality failures do not become valid by retrying immediately
- this path stays with reject handling / explicit failure semantics, not retry

### Example C - Selected-job configuration error (must fail fast)

Context:

- missing required config file or invalid selected-job contract field

Teaching point:

- retries are unsafe and misleading here
- startup/configuration failures should remain immediate fail-fast outcomes

## Retry boundary matrix (draft)

- `configuration/startup` -> fail fast, no retry
- `processor validation rule outcome` -> no retry, use reject/fail policy
- `transient I/O or connectivity` -> retry allowed when explicitly configured
- `unknown/unmapped failure category` -> fail fast by default

## Trade-off snapshot

- **Benefit:** improves resilience to short-lived infrastructure faults without manual reruns
- **Cost:** can delay diagnosis and amplify side-effect risk if applied broadly
- **Guardrail:** require explicit category mapping, bounded attempts, and final-outcome evidence

## Operator / runtime impact

- operators need clear retry counts and final outcomes
- some transient failures could self-recover
- unsafe retry paths should remain fail-fast
- run and step evidence should preserve first-failure cause plus terminal post-retry outcome

## Acceptance criteria

- [ ] one documented retry-policy contract exists for supported failure classes
- [ ] retry behavior emits clear evidence and final outcome state
- [ ] deterministic config/data failures are not silently retried as if they were transient
- [ ] examples prove retry boundary against B1 skip semantics and processor reject semantics
- [ ] exhausted-retry behavior is deterministic and test-covered

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`File ingestion hardening`](../../architecture/etl-core/file-ingestion-hardening.md)
- [`Job history and operational observability`](../../architecture/control-plane/job-history-and-operational-observability.md)
- [`Runtime flow`](../../architecture/etl-core/runtime-flow.md)
- [`Default processor reference`](../../config/processor/default-processor.md)

## Implementation notes

Keep first implementation intentionally narrow:

- implement retry for clear transient classes only
- make retry policy opt-in with bounded defaults
- align final failure category names with `D1`

## Status notes

The current first runtime slice now proves a narrow retry boundary:

- keep retry step-scoped and opt-in on explicit selected-job runs
- treat retry as transient-failure handling, not as a substitute for reject/skip semantics
- keep configuration/startup and deterministic data-quality failures fail-fast by default
- wire retry only through Spring Batch fault-tolerant chunk execution, overriding tasklet planning when needed
- keep ordered duplicate winner selection incompatible with retry in this slice because that duplicate path intentionally requires tasklet buffering
- keep operator-visible `retry_attempt` and `retry_summary` evidence on the active retry-callback path, with deterministic test coverage

Current preserved runtime proof bundle:

- `src/main/resources/config-jobs/customer-load-retry-policy-runtime-failure/` (malformed CSV first row)
- expected startup/runtime evidence: `STEP_READY event=retry_policy_enabled` plus runtime failure categorization on the read path

Follow-up scope:

- add a preserved runtime scenario that deterministically traverses retry callbacks (likely process/write transient-failure path) so scenario logs prove `retry_attempt` and terminal `retry_summary` evidence end-to-end

