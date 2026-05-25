# T11 â€” Cross-record window and aggregation transformation semantics

## Summary

Define explicit semantics for stateful transformation patterns (group/window/aggregate) that operate across records, including restart and determinism expectations.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **T10, F1**
- Sequence rank: **#7** in deferred advanced transform sequence

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Cross-record transformations need state management and replay/restart semantics that are not covered by current per-record transform/rule contracts.

## Goal

Provide a clear, deterministic model for window/group/aggregate transformation behavior with operationally safe restart and evidence semantics.

## Scope

- define cross-record transform contract and boundaries
- define checkpoint/restart implications and deterministic replay expectations
- define interaction with duplicate handling and reject behavior
- define evidence model for stateful transform outcomes

## Out of scope

- full enterprise governance lifecycle implementation
- source-native adaptation concerns
- replacing simpler field/record-local transform paths

## Proposed approach

- introduce explicit stateful transformation semantics only where required
- align with restart/recovery contracts before implementation
- keep integration with explicit scenario-step ordering

## Operator / runtime impact

- enables advanced aggregation/window scenarios
- improves clarity around restart and replay behavior
- prevents ambiguous state handling in production operations

## Concrete transformation examples

```yaml
# conceptual windowed aggregate transform
crossRecordTransforms:
  - type: rollingSum
    partitionBy: accountId
    orderBy: transactionTimestamp
    window:
      size: 5
      unit: records
    inputField: amount
    outputField: rollingAmount5
```

```yaml
# conceptual group aggregate with deterministic emission
crossRecordTransforms:
  - type: groupAggregate
    keyFields: [invoiceDate, region]
    aggregations:
      - function: sum
        field: netAmount
        as: totalNetAmount
      - function: count
        as: invoiceCount
```

Expected behavior:

- outputs are deterministic for the same ordered input and checkpoint state
- restart/replay continues from a well-defined checkpoint without duplicate aggregate emission
- evidence logs include window/group key, emitted aggregate count, and replay markers

## Developer expectations

- finalize restart/idempotency semantics with `F1` before activation
- define memory/disk state constraints and overflow behavior explicitly
- require preserved-scenario proof for both rolling-window and grouped-aggregate patterns
- reject ambiguous ordering configurations at startup (missing `orderBy` where required)

## Acceptance criteria

- [ ] stateful transform semantics are documented with examples
- [ ] restart/replay behavior is explicitly defined
- [ ] runtime evidence requirements are documented
- [ ] at least one focused scenario/test proves deterministic behavior when implemented

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Transformation capability catalog`](../../architecture/etl-core/transformation-capability-catalog.md)
- [`Restart semantics per execution mode`](F1-restart-semantics-per-execution-mode.md)
- [`Runtime flow`](../../architecture/etl-core/runtime-flow.md)

## Implementation notes

This item should not be activated until restart semantics are strong enough for stateful transformation guarantees.

## Status notes

Deferred pending record-level stage contract and restart-semantics alignment.



