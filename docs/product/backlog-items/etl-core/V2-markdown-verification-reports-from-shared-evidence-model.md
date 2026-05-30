# V2 - Generate Markdown verification reports from the shared evidence model

## Summary

Render the shared verification evidence model into Markdown so change validation and release-readiness outputs are easier to review, retain, and share.

## Current board status

- Epic: **[Epic V](../../epics/etl-core/epic-v-verification-evidence-and-reporting.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M3**
- Dependency: **V1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The shared evidence model is only useful operationally if teams can render it into a readable review artifact.

## Goal

Produce Markdown verification reports from the shared evidence model as the first human-friendly reporting surface.

## Scope

- Markdown rendering from the shared evidence model
- categorized verification outputs
- repeatable local workflow around generated reports

## Out of scope

- richer HTML drill-down views
- long-term retention/provenance policy
- replacing the shared evidence model with renderer-specific logic

## Proposed approach

Keep Markdown as the first report surface while preserving strict reuse of the evidence model introduced in V1.

## Operator / runtime impact

- reviewers gain a portable, readable report artifact
- local verification workflows become easier to interpret
- later HTML/report-gating work can build on the same evidence source

## Acceptance criteria

- [x] Markdown reports render from the shared evidence model
- [x] report categories remain aligned with the shared verification baseline
- [x] local verification workflow can generate and retain the Markdown output

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`ADR 0005`](../../../adr/foundations/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md)
- [`Job history and operational observability`](../../../architecture/control-plane/job-history-and-operational-observability.md)

## Implementation notes

V2 is intentionally the first renderer, not a second evidence model.

## Status notes

Shipped baseline: Markdown verification reports now render from the shared evidence contract.


