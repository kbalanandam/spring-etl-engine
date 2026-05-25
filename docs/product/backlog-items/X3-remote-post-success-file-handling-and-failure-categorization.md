# X3 â€” Add remote post-success file handling and failure categorization for SFTP

## Summary

Extend the staged SFTP capability so remote files can be moved, renamed, archived, or otherwise handled after successful processing, with clearer failure categorization along that path.

## Current board status

- Epic: **[Epic X](../epics/epic-x-file-transport-and-sftp-boundary.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **X2, D1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Once inbound transport exists, teams also need a consistent answer for what should happen to the remote artifact after success or categorized failure.

## Goal

Add remote post-success handling rules and tie them to clearer transport failure categories.

## Scope

- remote move/rename/archive behavior after success
- categorized transport-failure outcomes
- operator-facing evidence for remote disposition

## Out of scope

- initial staged inbound pull itself
- partner-facing deployment boundary redesign
- control-plane scheduling behavior

## Proposed approach

Build on the staged inbound slice first, then add remote file-disposition rules with explicit success/failure boundaries.

## Operator / runtime impact

- operators gain clearer remote artifact lifecycle behavior
- transport outcomes become easier to categorize and diagnose
- remote file handling stays explicit rather than ad hoc per deployment

## Acceptance criteria

- [ ] supported remote post-success handling rules are documented and implemented
- [ ] transport failures are categorized more clearly than generic runtime failure
- [ ] operator-facing evidence reports the remote disposition outcome

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`SFTP transport capability`](../../architecture/etl-core/sftp-transport-capability.md)
- [`Job history and operational observability`](../../architecture/control-plane/job-history-and-operational-observability.md)

## Implementation notes

Do not add X3 before X2 stabilizes; remote disposition rules need a proven inbound staging baseline first.

## Status notes

Deferred until the first inbound SFTP slice is stable and D1 failure-taxonomy direction is clearer.

