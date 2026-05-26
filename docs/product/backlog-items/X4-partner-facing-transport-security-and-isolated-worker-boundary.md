# X4 - Define partner-facing transport security rules and optional isolated worker deployment

## Summary

Define the stronger security and deployment boundaries needed when OneFlow participates in partner-facing transport flows, including when transport should run in an isolated worker or be delegated to external MFT.

## Current board status

- Epic: **[Epic X](../epics/epic-x-file-transport-and-sftp-boundary.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **X1, G1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Partner-facing transport can require stronger isolation and security posture than a local/internal staged-file workflow.

## Goal

Clarify when OneFlow should own transport directly, when it should use isolation boundaries, and when external MFT remains the better fit.

## Scope

- partner-facing transport security rules
- isolated-worker vs native runtime boundary
- external-MFT compatibility guidance

## Out of scope

- first inbound SFTP implementation details
- generic secret injection by itself
- mandatory control-plane redesign

## Proposed approach

Treat X4 as a boundary-definition item before any broad partner-facing transport rollout.

## Operator / runtime impact

- deployment guidance becomes clearer for security-sensitive flows
- transport can remain optional and bounded instead of becoming mandatory ETL core behavior
- teams can choose native, isolated, or external-MFT modes more intentionally

## Acceptance criteria

- [ ] partner-facing transport security expectations are documented
- [ ] isolated-worker and external-MFT boundaries are explicit
- [ ] future transport implementation work can reference one stable deployment/security boundary

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`SFTP transport capability`](../../architecture/etl-core/sftp-transport-capability.md)
- [`Control-plane worker boundary`](../../architecture/control-plane/control-plane-worker-boundary.md)

## Implementation notes

Keep this as a definition/boundary item, not an implementation shortcut that overcommits the product to one deployment mode.

## Status notes

Deferred for the broader enterprise-facing transport/security maturity phase.

