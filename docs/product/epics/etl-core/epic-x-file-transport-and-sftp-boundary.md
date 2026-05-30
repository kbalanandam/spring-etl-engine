# Epic X - File transport and SFTP boundary

## Summary

Epic X covers staged inbound/outbound transport work that may surround the ETL runtime, starting with SFTP-focused file acquisition and post-success handling while preserving a clear deployment boundary between ETL processing and transport responsibilities.

## Scope

This epic is the home for work that:

- defines staged transport scope and partner-facing boundaries
- introduces inbound SFTP pull behavior and post-success remote handling
- clarifies when transport should stay native to OneFlow versus external MFT or isolated workers
- preserves transfer evidence and security expectations for partner-facing flows

This epic is **not** the place to make transport mandatory for the ETL core or to collapse transport and control-plane concerns into one inseparable runtime.

## Related backlog items

- [`X1 - Define SFTP transport contract and deployment boundary`](../../backlog-items/etl-core/X1-sftp-transport-contract-and-deployment-boundary.md)
- [`X2 - Add first inbound SFTP staged pull capability`](../../backlog-items/etl-core/X2-first-inbound-sftp-staged-pull-capability.md)
- [`X3 - Add remote post-success file handling and failure categorization for SFTP`](../../backlog-items/etl-core/X3-remote-post-success-file-handling-and-failure-categorization.md)
- [`X4 - Define partner-facing transport security rules and optional isolated worker deployment`](../../backlog-items/etl-core/X4-partner-facing-transport-security-and-isolated-worker-boundary.md)

## Related docs

- [`../../architecture/etl-core/sftp-transport-capability.md`](../../../architecture/etl-core/sftp-transport-capability.md)
- [`../../architecture/control-plane/control-plane-worker-boundary.md`](../../../architecture/control-plane/control-plane-worker-boundary.md)

## Maintenance note

Use [`../product-backlog.md`](../../product-backlog.md) for live item-level execution-board fields. Use this page for the shared Epic X capability boundary.

