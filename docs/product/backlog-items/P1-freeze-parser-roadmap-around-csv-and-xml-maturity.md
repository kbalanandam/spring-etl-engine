# P1 — Freeze parser roadmap around CSV and XML source-native maturity

## Summary

Create one explicit parser-planning baseline for the product so parser work stops being implied or scattered. The frozen scope should prioritize CSV/XML source-native maturity now, preserve clear parser boundaries, and intentionally leave JSON source parsing for a later product decision.

## Current board status

- Epic: **[Epic P](../epics/epic-p-source-native-parser-maturity.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **none**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Parser work currently appears only as individual hardening slices, which makes it easy to blur parser-native scope with processor or orchestration work and to reopen JSON-source discussions before the shipped CSV/XML parser baseline is mature enough.

## Goal

Define and freeze one parser roadmap that keeps current planning centered on CSV/XML source-native capability, preserved-scenario proof, and explicit non-goals.

## Scope

- parser boundary guidance linked to the active product backlog
- explicit CSV/XML-first parser planning stance
- explicit statement that JSON source parsing is not part of the active parser board yet
- preserved-scenario and verification expectations that must be met before widening parser-family scope

## Out of scope

- implementing a JSON source parser now
- moving transforms, business rules, or duplicate policy into parser code
- changing orchestration, scheduler, or writer design
- introducing streaming or real-time runtime redesign under the parser label

## Proposed approach

Capture the parser roadmap as a dedicated epic with a small set of parser-native backlog items, cross-link it to the parser-boundary architecture note, and treat that scope as frozen until preserved CSV/XML scenarios show enough maturity to justify broader parser investment.

## Operator / runtime impact

- maintainers get one explicit parser scope instead of scattered parser expectations
- future CSV/XML parser work can be reviewed against a stable boundary before implementation starts
- product planning avoids accidental pressure to treat JSON source parsing as already committed
- documentation becomes clearer about what parser maturity means for operational scenarios

## Acceptance criteria

- [ ] the product backlog contains a dedicated parser epic and parser backlog items
- [ ] parser scope is explicitly frozen around CSV/XML source-native maturity
- [ ] JSON source parsing is documented as intentionally later, not active-scope work
- [ ] parser backlog items link back to the architecture note that defines parser boundaries

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`OneFlow file parser capabilities and boundaries`](../../architecture/oneflow-file-parser-capabilities.md)
- [`ETL product evolution roadmap`](../../architecture/etl-product-evolution-roadmap.md)

## Implementation notes

Treat this item as the planning guardrail for the rest of the `P*` work. If parser scope later widens to new source families, update this item and the matching epic page first.

## Status notes

Deferred intentionally so the parser scope can be frozen now without forcing immediate delivery of every parser enhancement.

