# X2 - Add first inbound SFTP staged pull capability

## Summary

Implement the first inbound SFTP slice so OneFlow can stage remote files locally before normal ETL processing begins.

## Current board status

- Epic: **[Epic X](../../epics/etl-core/epic-x-file-transport-and-sftp-boundary.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **X1, B2, C2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Transport acquisition is still external to the runtime examples, leaving a gap for staged inbound file pickup when teams want OneFlow to own that first step.

## Goal

Add one narrow staged inbound SFTP capability that hands off a local file to the normal ETL runtime.

## Scope

- remote SFTP pull into a local staging area
- transfer evidence for the staged inbound slice
- handoff into the existing explicit selected-job runtime contract

## Out of scope

- remote post-success cleanup rules
- partner-facing transport security redesign
- making transport mandatory for ETL execution

## Proposed approach

Keep the first slice file-staging-only so transport remains clearly separated from the ETL worker's main processing contract.

## Operator / runtime impact

- inbound files can be acquired through a controlled staged pull path
- operators need clear transfer evidence and staged-file visibility
- normal ETL processing should still run against local staged artifacts

## Acceptance criteria

- [ ] first inbound SFTP pull can stage remote files locally
- [ ] transfer evidence is emitted for the staged inbound action
- [ ] the staged file hands off cleanly into the normal ETL runtime path

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`SFTP transport capability`](../../../architecture/etl-core/sftp-transport-capability.md)
- [`Control-plane worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)

## Implementation notes

Keep the first implementation narrow and staged; do not mix transport acquisition with business transformation logic.

## Status notes

Deferred until the transport boundary and supporting hardening/reporting seams are ready.

