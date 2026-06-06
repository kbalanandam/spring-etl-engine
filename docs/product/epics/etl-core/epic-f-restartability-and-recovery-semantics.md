# Epic F - Restartability and recovery semantics

## Summary

Epic F covers the future restart and recovery behavior of the ETL runtime. It exists to make restart semantics explicit rather than accidental, especially as the product grows beyond simple one-shot file runs.

## Scope

This epic is the home for work that:

- defines restart expectations per execution mode
- clarifies what artifacts, state, or evidence make a run restartable
- keeps recovery semantics aligned with explicit job/step behavior

This epic is **not** the place for broader control-plane persistence design by itself, though it depends on that direction becoming clearer.

## Current phased direction

- **Phase-1 (active baseline)** - rerun-from-start remains the shipped execution boundary with explicit `runMode`/`recoveryPolicy` evidence
- **Phase-2 (docs-first hardening)** - advisory recovery semantics and resume-eligibility rules are now frozen without changing execution behavior
- **Phase-3 (gated later)** - idempotent rerun boundaries are now frozen at pattern level; any later resume execution design still requires explicit release-gate requirements and target-specific runtime proof

Current checkpoint: the docs-first `F1` freeze is complete through `D1`/`D2`/`D3`; any follow-on work should now build on that frozen contract rather than reopening baseline wording.

## Related backlog items

- [`F1 - Define restart semantics per execution mode`](../../backlog-items/etl-core/F1-restart-semantics-per-execution-mode.md)

## Related docs

- [`../../architecture/etl-core/runtime-flow.md`](../../../architecture/etl-core/runtime-flow.md)
- [`../../architecture/control-plane/job-history-and-operational-observability.md`](../../../architecture/control-plane/job-history-and-operational-observability.md)
- [`../../architecture/control-plane/control-plane-operational-data-model.md`](../../../architecture/control-plane/control-plane-operational-data-model.md)
- [`../../architecture/control-plane/control-plane-local-relational-schema.md`](../../../architecture/control-plane/control-plane-local-relational-schema.md)
- [`../../architecture/control-plane/operator-ui-mvp-api-surface.md`](../../../architecture/control-plane/operator-ui-mvp-api-surface.md)

## Maintenance note

Use [`../product-backlog.md`](../../product-backlog.md) for live item tracking. Use this page for the shared Epic F intent and boundary.

