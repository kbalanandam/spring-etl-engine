# P5 - Define native parser adoptability and CSV-first sidecar integration readiness

## Summary

Preserve one explicit future-ready path for adopting native parser engines, including C/C++, without breaking OneFlow's current Java/Spring Batch runtime boundary. The first concrete design target should be a CSV-first sidecar protocol, not a rushed JNI-first or parser-centered redesign.

## Current board status

- Epic: **[Epic P](../epics/epic-p-source-native-parser-maturity.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **P4, E1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Future parser-performance or specialized-source discussions could pressure the product toward deep native coupling before the current CSV/XML parser baseline is mature enough or before the reader/runtime seam is protected clearly enough. Without one explicit backlog item, native-parser interest could reopen parser scope in ad hoc ways and blur the boundary between source-native parsing and ETL-core behavior.

## Goal

Define one future-ready native-parser adoption path that keeps Java in charge of the runtime contract and treats a native parser, if introduced later, as a replaceable sidecar-backed parser engine behind the existing reader seam.

## Scope

- preserve native-parser adoptability as explicit future work rather than an implied design drift
- define a CSV-first sidecar protocol as the preferred first concrete integration shape
- keep native parser scope limited to source-native parsing and checkpoint emission
- document why sidecar integration is preferred before deep in-process JNI/JNA coupling

## Out of scope

- shipping a native parser now
- redesigning the runtime around native parsing
- moving transforms, validation rules, duplicate policy, or writer behavior into parser code
- committing to native XML parsing before the CSV-first proof and boundary remain credible

## Proposed approach

Capture native-parser adoption in a dedicated future backlog item, anchor it to the parser-boundary and native-adoptability notes, and treat the CSV sidecar protocol as the first acceptable concrete design target if native parsing is ever activated.

## Operator / runtime impact

- future parser experimentation stays reviewable against a stable architecture boundary
- native-parser interest does not silently alter the selected-job runtime contract
- portability and deployment concerns remain visible before any native code is introduced
- parser planning becomes more concrete without pretending the native path is already shipped

## Acceptance criteria

- [ ] the product backlog tracks native-parser adoptability explicitly under `Epic P`
- [ ] the preferred future integration shape is documented as CSV-first sidecar, not parser-centered runtime replacement
- [ ] native parser scope is limited to source-native parsing and checkpointing
- [ ] the Java reader/runtime seam remains the preserved handoff into generated runtime records and downstream ETL behavior

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Native parser adoptability`](../../architecture/etl-core/native-parser-adoptability.md)
- [`CSV native parser sidecar protocol`](../../architecture/etl-core/csv-native-parser-sidecar-protocol.md)
- [`Java native parser reader adapter contract`](../../architecture/etl-core/java-native-parser-reader-adapter-contract.md)

## Implementation notes

This item should stay deferred until the current Java CSV/XML parser baseline has stronger preserved-scenario proof. If activated later, use the sidecar protocol note as the concrete starting point and review it again against restartability, packaging, and operator evidence expectations.

## Status notes

Deferred intentionally so native-parser readiness is documented now without creating pressure to bypass the existing Java parser/runtime boundary.

