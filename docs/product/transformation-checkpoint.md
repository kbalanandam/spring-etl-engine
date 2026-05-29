# Transformation checkpoint

Checkpoint date: **2026-05-23**

## Purpose

Use this page as the short memory aid for the current transformation decisions.

This is **not** a second execution board. The canonical place for `Priority`, `Status`, `Milestone`, and `Dependency` remains [`product-backlog.md`](./product-backlog.md).

## What is decided today

### 1. What is shipped today

The active shipped transformation path is still the processor-centered contract documented in [`default-processor.md`](../config/processor/default-processor.md):

- field-to-field mapping through `mappings[].fields[]`
- shipped built-in transform types: `valueMap`, `expression`, `conditional`, and `zoneConvert`
- shipped showcase extension transform type (ServiceLoader provider): `partnerStatusTranslate`
- shipped validation rules: `notNull`, `timeFormat`, and `duplicate`
- rejected-record output on the active processor path
- duplicate handling on the active processor-rule path, not source validation

### 2. What is next

`T4` hardening is complete, and [`T15`](./backlog-items/etl-core/T15-xml-native-duplicate-identity-for-nested-xml-sources.md) is now closed on the active runtime path.

Current state: ordered-duplicate resolver evidence, optional `storageMode` override (`auto|memory|embeddedDb`) for `duplicate + orderBy`, additive reject-quarantine publication, additive XML duplicate identity support through `duplicateIdentityMode: flatMapped|xmlNative`, and the intentional non-compatible `S6` processor-contract cutover are shipped.

Near-term roadmap addition: `T16` is now the active planning track for customer-owned processor transform extensibility on the existing `type: default` processor seam.

### 3. What remains future or conceptual

The examples on deferred items `T8` through `T14` are planning examples for development expectations.

They should **not** be read as shipped runtime/config support today.

That future-only boundary includes:

- reusable transform profiles (`T8`)
- source-native transform seam (`T9`)
- record-level transform stage (`T10`)
- cross-record/window transforms (`T11`)
- governance/lineage for transform definitions (`T12`)
- transform-stage metrics (`T13`)
- secure data-shaping transforms (`T14`)

### 4. Deferred advanced transformation order

When those deferred items are activated, use this order:

`T8` -> `T10` -> `T12` -> `T13` -> `T9` -> `T14` -> `T11`

Board anchors:

- [`Epic T`](./epics/epic-t-transformation-capability.md)
- [`product-backlog.md`](./product-backlog.md)

### 5. Duplicate-handling reminder

`T15` is closed. Resume duplicate follow-on work from [`T7`](./backlog-items/etl-core/T7-duplicate-tracking-scalability-redesign-deferment.md) when scale redesign is reactivated.

Keep the larger duplicate-scale redesign separate under [`T7`](./backlog-items/etl-core/T7-duplicate-tracking-scalability-redesign-deferment.md).

Current duplicate baseline to remember:

- keep-first duplicate behavior exists today
- ordered winner selection through `orderBy` exists today
- duplicate storage mode is selectable for ordered winner selection (`duplicate` + `orderBy`) through `storageMode: auto|memory|embeddedDb`
- XML duplicate identity is now shipped as an additive processor-rule option through `duplicateIdentityMode: xmlNative`
- `T15` (including `S6`) is complete and no longer a deferred duplicate boundary

### 6. Runtime model to preserve

Do not forget these boundaries:

- one selected scenario per run
- explicit `job-config.yaml` step ordering stays the runtime contract
- current transform/rule flow stays: source validation -> reader emits runtime record -> processor transforms -> processor rules -> write
- future source-native adaptation is only a planned seam, not current shipped behavior

### 7. Practical reading order for tomorrow

If you need to resume quickly, read in this order:

1. [`product-backlog.md`](./product-backlog.md) - canonical status and sequencing
2. [`Epic T`](./epics/epic-t-transformation-capability.md) - shared transformation boundary
3. [`T4`](./backlog-items/etl-core/T4-transformation-quarantine-and-duplicate-hardening.md) - completed quarantine and duplicate hardening baseline
4. [`T15`](./backlog-items/etl-core/T15-xml-native-duplicate-identity-for-nested-xml-sources.md) - closed XML-native duplicate identity item (`S1`-`S6` complete)
5. [`T7`](./backlog-items/etl-core/T7-duplicate-tracking-scalability-redesign-deferment.md) - deferred duplicate-scale boundary
6. [`default-processor.md`](../config/processor/default-processor.md) - shipped contract today
7. [`transformation-capability-roadmap.md`](../architecture/etl-core/transformation-capability-roadmap.md) - future direction
8. [`A7 + T16 extensibility charter`](../architecture/etl-core/a7-t16-extensibility-charter.md) - bounded extension model for custom steps and custom transforms

## One-line reminder

**Today we keep the shipped runtime processor-centered, treat `T4` and `T15` (including `S6`) as completed, keep larger duplicate-scale redesign under `T7`, keep `T16` as additive customer-transform seam planning on the same processor path, and treat `T8`-`T14` examples as future planning only.**


