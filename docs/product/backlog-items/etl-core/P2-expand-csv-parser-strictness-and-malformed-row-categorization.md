# P2 - Expand CSV parser strictness and malformed-row categorization on the read path

## Summary

Build the next CSV parser-maturity slice only where the behavior is truly source-native: tokenization, quote/escape strictness, malformed-row detection, and categorized read-time failure handling before a normal runtime record exists.

## Current board status

- Epic: **[Epic P](../../epics/etl-core/epic-p-source-native-parser-maturity.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **B5, P1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

`B5` shipped a narrow CSV quote-character hardening slice, but many real CSV feeds also require clearer parser-native strictness around malformed quoting, unexpected token counts, or categorized row-level parse failures that happen before business transforms or validation rules should run.

## Goal

Add one disciplined CSV parser slice that strengthens source-native read behavior without turning the CSV reader into a general transform or business-rules engine.

## Scope

- CSV tokenization strictness that remains parser-native
- malformed-row or malformed-token categorization before runtime-record creation
- focused config and documentation only where truly needed for parser behavior
- preserved scenario coverage for realistic CSV edge cases

## Out of scope

- business-value cleanup after a row is successfully parsed
- header-to-business mapping heuristics
- duplicate handling, reject-policy business decisions, or target-routing logic
- opening a broader parser plug-in framework for speculative future formats

## Proposed approach

Start from the shipped `B5` reader baseline and grow only the narrowest parser contracts needed for real preserved CSV scenarios. Validate each addition against the parser-boundary note: if a behavior depends on a normal runtime record and business meaning, it belongs downstream, not here.

## Operator / runtime impact

- CSV failures become easier to classify as source-native parse problems instead of generic downstream errors
- scenario authors gain a clearer contract for difficult CSV feeds without changing simple bundles
- preserved scenarios can prove realistic CSV ingestion readiness for more time-sensitive operational use
- documentation becomes clearer about which CSV issues are parser-owned vs processor-owned

## Acceptance criteria

- [ ] CSV parser growth is limited to source-native token/row interpretation concerns
- [ ] malformed CSV row outcomes can be categorized before normal runtime-record creation
- [ ] preserved test scenarios cover representative malformed/strict CSV inputs
- [ ] docs describe both the added CSV parser behavior and the remaining non-goals

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`OneFlow file parser capabilities and boundaries`](../../../architecture/etl-core/oneflow-file-parser-capabilities.md)
- [`CSV source reference`](../../../config/source/csv-source.md)

## Implementation notes

Use `B5` as the shipped baseline and avoid reopening that item's intentionally narrow design unless preserved scenarios prove the need. Keep config additions minimal and source-native.

## Status notes

Deferred intentionally until the frozen parser roadmap is revisited with concrete CSV preserved-scenario demand.


