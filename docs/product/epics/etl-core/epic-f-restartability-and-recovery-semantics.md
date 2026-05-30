# Epic F - Restartability and recovery semantics

## Summary

Epic F covers the future restart and recovery behavior of the ETL runtime. It exists to make restart semantics explicit rather than accidental, especially as the product grows beyond simple one-shot file runs.

## Scope

This epic is the home for work that:

- defines restart expectations per execution mode
- clarifies what artifacts, state, or evidence make a run restartable
- keeps recovery semantics aligned with explicit job/step behavior

This epic is **not** the place for broader control-plane persistence design by itself, though it depends on that direction becoming clearer.

## Related backlog items

- [`F1 - Define restart semantics per execution mode`](../../backlog-items/etl-core/F1-restart-semantics-per-execution-mode.md)

## Related docs

- [`../../architecture/etl-core/runtime-flow.md`](../../../architecture/etl-core/runtime-flow.md)
- [`../../architecture/control-plane/job-history-and-operational-observability.md`](../../../architecture/control-plane/job-history-and-operational-observability.md)
- [`../../architecture/control-plane/control-plane-operational-data-model.md`](../../../architecture/control-plane/control-plane-operational-data-model.md)

## Maintenance note

Use [`../product-backlog.md`](../../product-backlog.md) for live item tracking. Use this page for the shared Epic F intent and boundary.

