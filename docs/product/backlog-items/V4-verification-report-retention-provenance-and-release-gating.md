# V4 - Define verification-report retention, provenance, and release gating rules

## Summary

Define how verification reports should be retained, attributed, and used in milestone/release decisions so reporting becomes auditable rather than only convenient.

## Current board status

- Epic: **[Epic V](../epics/epic-v-verification-evidence-and-reporting.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **V1, V2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Markdown/HTML reports are useful, but enterprise release decisions need clearer retention, provenance, and gating rules before those artifacts become authoritative.

## Goal

Define how verification evidence and reports should be retained, traced back to their source, and used in release/milestone gates.

## Scope

- retention expectations for generated verification reports
- provenance rules linking reports to source evidence and runs
- release/milestone gating guidance

## Out of scope

- report rendering itself
- runtime observability emission by itself
- full compliance platform behavior

## Proposed approach

Keep V4 definition-oriented first: decide what must be retained and trusted before automating stricter release gates.

## Operator / runtime impact

- release decisions can rely on clearer verification provenance
- report retention becomes more deliberate
- future enterprise reviewers gain better auditability

## Acceptance criteria

- [ ] retention expectations are defined for verification outputs
- [ ] provenance rules connect reports back to source evidence/runs
- [ ] release/milestone gating guidance is documented clearly enough to enforce later

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`ADR 0005`](../../adr/foundations/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md)
- [`Job history and operational observability`](../../architecture/control-plane/job-history-and-operational-observability.md)

## Implementation notes

Do not treat convenience reporting as audit-grade release evidence until V4 defines the provenance and gating model.

## Status notes

Deferred for the later enterprise verification maturity pass.


