# P3 - Expand XML parser maturity for namespace-aware and fragment-contract scenarios

## Summary

Capture the next XML parser-maturity slice for cases where preserved scenarios need richer source-native fragment interpretation, such as namespace-aware record selection or stricter fragment-shape handling, while keeping business rules and transformations out of the parser layer.

## Current board status

- Epic: **[Epic P](../../epics/epic-p-source-native-parser-maturity.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **B4, P1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The shipped XML path already supports root/record interpretation, optional XSD validation, and flattening strategies, but some realistic XML feeds may still need stronger parser-native handling around namespaces, fragment boundaries, or malformed-fragment categorization before normal runtime records exist.

## Goal

Add one controlled XML parser slice that improves source-native XML interpretation only where preserved scenarios prove the need.

## Scope

- namespace-aware or stricter fragment-selection behavior when required by preserved XML scenarios
- parser-native malformed-fragment categorization before runtime-record creation
- focused XML reader/validator tests for those source-native behaviors
- documentation that clarifies where XML parsing stops and processor/writer behavior begins

## Out of scope

- XML business normalization after record creation
- XPath-style business-rule validation on mapped values
- duplicate winner selection in the parser layer
- target-specific XML wrapper/publication behavior

## Proposed approach

Use the current XML reader/validation seam as the extension point and only open namespace/fragment contracts when a preserved scenario clearly requires them. Keep XML parser growth behind the existing factories/readers rather than spreading XML-specific conditionals through orchestration code.

## Operator / runtime impact

- realistic XML source failures can be classified earlier and more accurately
- preserved XML scenarios can prove stronger parser maturity without changing the flat ordered runtime model
- maintainers get clearer guidance on when XML work belongs in parsing vs processor rules or writers
- docs stay aligned with the generated-model and XML-flattening contracts already on the active path

## Acceptance criteria

- [ ] XML parser growth is justified by a preserved scenario with a real source-native need
- [ ] namespace/fragment behavior stays limited to pre-record parsing concerns
- [ ] malformed-fragment outcomes can be categorized before normal runtime-record creation
- [ ] docs/tests explain the new XML parser behavior and its limits clearly

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`OneFlow file parser capabilities and boundaries`](../../../architecture/etl-core/oneflow-file-parser-capabilities.md)
- [`XML source reference`](../../../config/source/xml-source.md)

## Implementation notes

Treat `B4` as the already shipped XML validation baseline. This item should only move if preserved XML scenarios show a parser-native gap that cannot be handled cleanly through existing validation, processor, or writer seams.

## Status notes

Deferred intentionally until the frozen parser roadmap is revisited with concrete XML preserved-scenario pressure.


