# Epic B — Runtime hardening and file behavior

## Summary

Epic B covers operational hardening on the active ETL path, especially around file validation, retry/skip behavior, archive/reject handling, a shared zip/unzip service boundary first used by file flows, and reader/writer robustness.

The shipped parser-adjacent hardening slices such as `B4` and `B5` remain part of this epic's history, but broader parser-roadmap planning now lives under **[Epic P](epic-p-source-native-parser-maturity.md)** so future parser growth stays explicitly CSV/XML-first and source-native.

## Scope

This epic is the home for work that:

- improves file-ingestion hardening and fail-fast validation
- defines skip/retry policies for operational resilience
- hardens CSV and XML reader behavior on the operational path, especially where that work is coupled to file-ingestion hardening
- clarifies reject/archive behavior and staged publication expectations
- defines a narrow shared zip/unzip service boundary that OneFlow can call where needed, with the first product slice focused on local file-based source preparation and archive packaging

This epic is **not** the place for scheduling, transport acquisition, or product-board/reporting concerns.

## Related backlog items

- [`B1 ? — Introduce configurable skip policy support`](../backlog-items/B1-configurable-skip-policy-support.md)
- [`B2 ? — Introduce configurable retry policy support where appropriate`](../backlog-items/B2-configurable-retry-policy-support.md)
- [`B3 ? — Archive processed source files after successful file-based runs`](../backlog-items/B3-archive-processed-source-files-after-success.md)
- [`B4 ? — Add strict XML source validation mode with optional XSD checks`](../backlog-items/B4-strict-xml-source-validation-and-optional-xsd.md)
- [`B5 ? — Add CSV parsing hardening with configurable quote/escape behavior`](../backlog-items/B5-csv-reader-parsing-hardening.md)
- [`B6 ? — Add a shared zip/unzip service boundary for file-based source preparation and archive packaging`](../backlog-items/B6-shared-zip-unzip-service-boundary-for-file-based-source-preparation-and-archive-packaging.md)

## Related docs

- [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md)
- [`../../architecture/file-ingestion-hardening-checklist.md`](../../architecture/file-ingestion-hardening-checklist.md)
- [`../../architecture/validation-extension-architecture.md`](../../architecture/validation-extension-architecture.md)
- [`../../architecture/csv-to-xml-runtime-flow.md`](../../architecture/csv-to-xml-runtime-flow.md)

## Maintenance note

Use [`../product-backlog.md`](../product-backlog.md) for the live board values. Use this page for the shared hardening boundary across the Epic B work items.

