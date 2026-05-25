# P4 â€” Prove CSV and XML parser maturity through preserved scenarios and verification

## Summary

Define the proof gate for parser maturity: before the product opens new parser-family scope, CSV and XML parser growth should be backed by preserved scenario bundles, focused verification evidence, and explicit proof that parser behavior is strong enough for more demanding operational file scenarios.

## Current board status

- Epic: **[Epic P](../epics/epic-p-source-native-parser-maturity.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **P2, P3**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Parser maturity is easy to overestimate when it is discussed only in abstractions. Without preserved scenario proof, the team can reopen new parser-family ideas, including JSON source parsing, before the existing CSV/XML parser paths have demonstrated enough stability on realistic edge cases.

## Goal

Use preserved scenario bundles and verification evidence as the freeze gate that demonstrates parser maturity on CSV/XML before the backlog widens.

## Scope

- preserved CSV parser scenarios that prove realistic token/quoting/malformed-row handling
- preserved XML parser scenarios that prove realistic fragment/namespace or malformed-fragment handling where applicable
- verification expectations that distinguish parser-native evidence from downstream transform/rule evidence
- explicit review checkpoint before any new parser-family backlog is activated

## Out of scope

- adding a JSON source parser now
- redefining the end-to-end runtime as a streaming or parser-centered engine
- replacing broader regression/reporting strategy with a parser-only framework
- moving business validation into parser proof scenarios

## Proposed approach

Treat parser maturity like any other shipped runtime capability: preserve runnable scenario bundles, add focused automated proof where practical, and keep the verification story tied to source-native parser outcomes. Only after that proof is credible should the team consider widening parser-family scope.

## Operator / runtime impact

- parser-readiness claims become evidence-backed instead of anecdotal
- future work can distinguish parser failures from processor or target failures more cleanly
- the product gets a clearer maturity gate before taking on additional parser families
- stakeholders can see why JSON source parsing remains intentionally later

## Acceptance criteria

- [ ] preserved CSV/XML scenarios exist for the parser edge cases the team claims to support
- [ ] verification evidence distinguishes parser-native proof from downstream ETL-core behavior
- [ ] parser maturity is reviewed against preserved proof before new parser-family work is activated
- [ ] JSON source parsing remains outside the active parser backlog until that proof gate is satisfied and a concrete source contract exists

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`OneFlow file parser capabilities and boundaries`](../../architecture/etl-core/oneflow-file-parser-capabilities.md)
- [`Verification evidence and reporting`](../epics/epic-v-verification-evidence-and-reporting.md)

## Implementation notes

This item is the operational proof companion to `P1`. If CSV/XML parser maturity is not yet evidenced through preserved scenarios, keep new parser-family requests parked.

## Status notes

Deferred intentionally as a freeze gate, not because the parser direction is unimportant.

