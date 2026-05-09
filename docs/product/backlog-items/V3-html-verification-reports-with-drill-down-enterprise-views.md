# V3 — HTML verification reports with drill-down enterprise views

## Summary

Add HTML verification reporting from the same shared evidence model already used for Markdown so enterprise users get richer navigation and drill-down without duplicating reporting logic.

## Current board status

- Epic: **Epic V**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **V1, V2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

## Problem

The product already has a shared verification evidence model and Markdown reporting, but enterprise-friendly presentation still stops at plain-text output.

Without HTML drill-down views, reviewers still lack:

- richer navigation across categories and sections
- easier evidence drill-down for broader stakeholder audiences
- a more presentation-friendly report format for release and enterprise review contexts

## Goal

Render HTML verification reports from the same shared evidence model as Markdown so the product gains richer navigation and drill-down without splitting reporting logic by format.

## Scope

This item covers:

- HTML rendering from the shared verification evidence model
- drill-down and navigation views for verification categories and evidence sections
- enterprise-friendly presentation layered on top of the same source evidence already used for Markdown

## Out of scope

This item does not cover:

- redefining the evidence model itself
- provenance, retention, or release-gating rules beyond what V4 will handle
- replacing Markdown as the repository-friendly report artifact
- introducing a separate report-generation logic path just for HTML

## Proposed approach

The preferred direction is:

1. preserve the shared verification evidence model as the canonical source
2. keep Markdown as the repository-friendly review artifact
3. add HTML rendering from the same model for richer navigation and drill-down
4. use HTML where enterprise presentation benefits from collapsible sections, clearer layout, and easier evidence traversal
5. defer provenance hardening, retention, and release-gating rules to `V4`

## Operator / runtime impact

Expected impact when this item ships:

- release and verification stakeholders can navigate evidence more easily
- HTML becomes a stronger presentation layer for broader audiences
- reporting remains governed by one shared model instead of diverging format-specific logic
- future release-readiness workflows have a better human-facing artifact to build on

## Acceptance criteria

- [ ] HTML reports render from the same shared evidence model used by Markdown
- [ ] HTML output adds meaningful navigation and drill-down beyond plain Markdown formatting
- [ ] no duplicate evidence-capture logic is introduced for HTML-only output
- [ ] Markdown remains available as the repository-native artifact
- [ ] follow-on provenance and release-gating work can build on this HTML layer without redesigning the renderer split

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`ADR-0005: Use a shared verification evidence model for Markdown and HTML reports`](../../adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md)
- [`Job history and operational observability`](../../architecture/job-history-and-operational-observability.md)

## Implementation notes

Do not bypass the shared evidence model for convenience. The key architectural value of this item is richer HTML presentation without duplicating verification business logic.

## Status notes

Deferred today, but important enough to document because it is the first item that turns the existing shared evidence model into a richer enterprise-facing presentation layer.

