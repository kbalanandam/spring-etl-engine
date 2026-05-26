# V1 - Define enterprise verification evidence model and report categories

## Summary

Define one shared verification evidence model so different report formats can describe the same validation and release-readiness results consistently.

## Current board status

- Epic: **[Epic V](../epics/epic-v-verification-evidence-and-reporting.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M3**
- Dependency: **C1, C2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Without a shared evidence model, every report format risks inventing its own categories and interpretation rules.

## Goal

Freeze one reusable verification evidence baseline that future Markdown/HTML/report-gating outputs can share.

## Scope

- shared verification evidence model
- first report-category taxonomy
- reuse across multiple report renderers

## Out of scope

- one specific report format only
- retained enterprise report provenance rules by itself
- runtime observability emission itself

## Proposed approach

Separate evidence collection from rendering so later Markdown, HTML, and other views can all build on the same core model.

## Operator / runtime impact

- release/readiness reporting becomes more consistent
- future report formats avoid duplicating evidence logic
- milestone reviews can rely on shared interpretation categories

## Acceptance criteria

- [x] one shared verification evidence model exists
- [x] report categories are defined from that shared model
- [x] future report renderers can consume the same evidence baseline

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`ADR 0005`](../../adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md)
- [`Job history and operational observability`](../../architecture/control-plane/job-history-and-operational-observability.md)

## Implementation notes

V1 is the foundation under V2, V3, and V4 rather than a user-facing reporting format by itself.

## Status notes

Shipped baseline: the shared evidence model now underpins the current reporting workflow.

