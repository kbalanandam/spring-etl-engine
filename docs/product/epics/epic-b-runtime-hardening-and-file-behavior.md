# Epic B — Runtime hardening and file behavior

## Summary

Epic B covers operational hardening on the active ETL path, especially around file validation, retry/skip behavior, archive/reject handling, and reader/writer robustness.

## Scope

This epic is the home for work that:

- improves file-ingestion hardening and fail-fast validation
- defines skip/retry policies for operational resilience
- hardens CSV and XML reader behavior
- clarifies reject/archive behavior and staged publication expectations

This epic is **not** the place for scheduling, transport acquisition, or product-board/reporting concerns.

## Related backlog items

- [`B1 ? — Introduce configurable skip policy support`](../backlog-items/B1-configurable-skip-policy-support.md)
- [`B2 ? — Introduce configurable retry policy support where appropriate`](../backlog-items/B2-configurable-retry-policy-support.md)
- [`B3 ? — Archive processed source files after successful file-based runs`](../backlog-items/B3-archive-processed-source-files-after-success.md)
- [`B4 ? — Add strict XML source validation mode with optional XSD checks`](../backlog-items/B4-strict-xml-source-validation-and-optional-xsd.md)
- [`B5 ? — Add CSV parsing hardening with configurable quote/escape behavior`](../backlog-items/B5-csv-reader-parsing-hardening.md)

## Related docs

- [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md)
- [`../../architecture/file-ingestion-hardening-checklist.md`](../../architecture/file-ingestion-hardening-checklist.md)
- [`../../architecture/validation-extension-architecture.md`](../../architecture/validation-extension-architecture.md)
- [`../../architecture/csv-to-xml-runtime-flow.md`](../../architecture/csv-to-xml-runtime-flow.md)

## Maintenance note

Use [`../product-backlog.md`](../product-backlog.md) for the live board values. Use this page for the shared hardening boundary across the Epic B work items.

