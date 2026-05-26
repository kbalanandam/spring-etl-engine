# B3 - Archive processed source files after successful file-based runs

## Summary

Archive successfully processed file-based inputs so completed runs leave a clearer operational trail and reduce accidental re-processing of the same source artifact.

## Current board status

- Epic: **[Epic B](../epics/epic-b-runtime-hardening-and-file-behavior.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M1**
- Dependency: **A1, T1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

File-based scenarios need a consistent way to move successfully processed inputs out of the active ingress location and record where they went.

## Goal

Support archive-on-success for the active file-backed runtime path with evidence that operators can inspect later.

## Scope

- archive-on-success behavior for supported file sources
- scenario-relative archive-path handling
- operator-facing `archivedSourcePath` evidence

## Out of scope

- archive-on-failure quarantine redesign
- remote/SFTP post-success handling
- restart semantics by itself

## Proposed approach

Treat archiving as a shared file-backed source concern, not a CSV-only special case, and surface archive results through step evidence.

## Operator / runtime impact

- completed file runs leave clearer source-file disposition evidence
- reruns are less likely to pick up already-processed source files accidentally
- preserved examples can prove archive behavior directly

## Acceptance criteria

- [x] supported file sources can archive inputs after successful processing
- [x] archive paths resolve consistently relative to the selected job bundle
- [x] `archivedSourcePath` is emitted in runtime evidence

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`File ingestion hardening`](../../architecture/etl-core/file-ingestion-hardening.md)
- [`CSV to XML runtime flow`](../../architecture/etl-core/csv-to-xml-runtime-flow.md)

## Implementation notes

This item started as CSV-focused but the active baseline now treats archive-on-success as a shared file-backed concern.

## Status notes

Shipped baseline: archive-on-success is active for supported file-based scenarios and appears in step-finished evidence.

