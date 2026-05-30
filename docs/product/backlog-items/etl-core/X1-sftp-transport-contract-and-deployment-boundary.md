# X1 - SFTP transport contract and deployment boundary

## Summary

Define the first product-level SFTP transport contract and deployment boundary so the ETL runtime can standardize staged file pickup without pulling transport security and partner-edge concerns into the center of transformation logic.

## Current board status

- Epic: **[Epic X](../../epics/etl-core/epic-x-file-transport-and-sftp-boundary.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M2**
- Dependency: **E1, C1, G1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Many real scenarios depend on daily inbound or outbound SFTP activity, but the product does not yet define where SFTP belongs in the architecture or how it should interact with the ETL core.

Without a clear contract, teams may:

- keep writing one-off SFTP code
- mix transport behavior into transformation logic
- blur the security boundary between partner-edge transfer and internal ETL processing
- produce inconsistent operational evidence for transfer failures and outcomes

## Goal

Define a narrow, explicit first transport contract for SFTP that keeps transport staged around the ETL core, supports both native and MFT-managed modes, and establishes the deployment boundary before implementation begins.

## Scope

This item covers:

- the first SFTP transport contract
- staged inbound scope as the preferred first slice
- native product mode vs external MFT-managed mode
- deployment-boundary guidance for partner-facing transport concerns
- minimum transfer evidence and failure-category expectations

## Out of scope

This item does not cover:

- full enterprise MFT replacement
- broad multi-party routing/orchestration
- final outbound reconciliation for every scenario
- every future transport protocol beyond the SFTP baseline

## Proposed approach

The preferred direction is:

1. treat SFTP as transport-oriented capability around the ETL core
2. land inbound files in a controlled local staging folder
3. let the existing file readers process staged files through normal ETL paths
4. support both external MFT-managed edge mode and native product SFTP mode
5. define transfer evidence and deployment-boundary rules before building runtime behavior

## Operator / runtime impact

Expected impact when this item ships:

- SFTP work becomes standardized and reviewable instead of ad hoc
- operators gain clearer transfer-oriented evidence for what was pulled, skipped, or failed
- partner-facing transport can remain isolatable from unrelated ETL flows
- the product preserves ETL-first scope without prematurely becoming a full transport platform

## Acceptance criteria

- [ ] the first SFTP slice is defined as staged inbound scope, not broad multi-protocol transport
- [ ] native SFTP mode and external MFT-managed mode are both described clearly
- [ ] deployment-boundary guidance is documented for partner-facing transport isolation
- [ ] minimum transfer evidence and failure categories are defined
- [ ] follow-on transport implementation can start from this contract without re-deciding the product boundary

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`SFTP transport capability`](../../../architecture/etl-core/sftp-transport-capability.md)
- [`Job history and operational observability`](../../../architecture/control-plane/job-history-and-operational-observability.md)

## Implementation notes

Keep the first slice narrow: inbound SFTP pull to local staging, then normal ETL processing. Avoid mixing transformation logic into transport and avoid expanding into a broad MFT feature set too early.

## Status notes

Ready for deeper review because the board row already identifies this as the transport entry point for later implementation, but the contract and deployment boundary need more space than a single line in the execution board.

