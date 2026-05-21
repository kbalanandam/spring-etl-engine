# T15 - XML-native duplicate identity for nested XML sources

## Summary

Add a dedicated follow-on item for XML-native duplicate identity so nested XML scenarios can resolve duplicates with source-structure-aware keys when flat mapped fields are not sufficient.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **T4, P3**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The shipped duplicate rule works on mapped runtime fields and is reliable for flat or clearly keyed records, but some nested XML sources require identity keys that preserve XML path, scope, or attribute context. Without that context, duplicate elimination can produce incorrect winners or false matches.

### Quick example to preserve

Nested XML source excerpt:

```xml
<Events>
  <Event id="E1">
	<Customer><Id>100</Id></Customer>
	<Tags>
	  <Tag code="A">VIP</Tag>
	  <Tag code="B">VIP</Tag>
	</Tags>
	<UpdatedAt>2026-05-21T10:00:00</UpdatedAt>
  </Event>
  <Event id="E2">
	<Customer><Id>100</Id></Customer>
	<Tags><Tag code="A">VIP</Tag></Tags>
	<UpdatedAt>2026-05-21T11:00:00</UpdatedAt>
  </Event>
</Events>
```

Why this matters:

- flat mapped duplicate keys such as `customerId + tagValue` can collapse different nested nodes into one logical key
- XML-native identity can include path/scope/attribute context (for example `Tag/@code`) so duplicate winner selection stays correct for repeated nested structures
- reject output flow stays on the existing processor path; this item only improves how duplicate identity keys are formed for nested XML

## Goal

Define an additive XML-native identity mode for duplicate elimination that improves correctness on nested XML while preserving the current processor-centered runtime contract and reject pipeline.

## Scope

This item covers:

- XML-source-focused duplicate identity extraction for nested or repeated-node scenarios
- additive configuration surface that keeps current flat-key behavior backward compatible
- explicit operator evidence for duplicate identity mode and identity-source rationale
- preserved nested XML scenario proof and focused regression tests

## Out of scope

This item does not cover:

- replacing the existing processor `duplicate` rule for all sources
- reopening deprecated validation paths under `src/main/java/com/etl/validation/`
- large duplicate-state scalability redesign (tracked under [`T7`](T7-duplicate-tracking-scalability-redesign-deferment.md))
- target-aware deduplication or restart semantics redesign

## Proposed approach

The preferred direction is:

1. keep current `duplicate` behavior as the default contract
2. add XML-native identity as an opt-in mode for nested XML scenarios first
3. emit clear runtime evidence for selected identity mode and key-construction inputs
4. keep duplicate decisioning and reject emission on the active processor-rule path
5. document UI and runtime guardrails so inefficient or unsafe choices are warned or blocked

UI guardrail note for collaborators:

- default to the current flat-key mode for simple XML
- recommend XML-native identity for nested/repeating-node XML inputs
- warn (or block in stricter environments) when a likely-unsafe flat-key choice is selected for nested XML

## Operator / runtime impact

Expected impact when this item ships:

- duplicate outcomes become more accurate for nested XML sources with repeating structures
- existing non-XML and flat XML scenarios remain unchanged unless opt-in is configured
- operators can see identity mode evidence in startup/step logs
- config docs gain explicit guidance for when to use flat mapped keys vs XML-native identity

## Acceptance criteria

- [ ] XML-native duplicate identity is available as an additive option for nested XML source scenarios
- [ ] backward compatibility is preserved for existing duplicate configurations that use flat mapped fields
- [ ] startup/runtime evidence clearly reports chosen identity mode and reason
- [ ] UI guardrails expose safe defaults and warnings for likely-unsafe flat-key choices on nested XML sources
- [ ] at least one preserved nested XML scenario demonstrates a case where XML-native keys prevent false duplicate matches
- [ ] docs under `docs/config/` and `docs/architecture/` explain boundaries, examples, and non-goals

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`T4 - Transformation quarantine and duplicate hardening`](T4-transformation-quarantine-and-duplicate-hardening.md)
- [`T7 - Duplicate tracking scalability redesign deferment`](T7-duplicate-tracking-scalability-redesign-deferment.md)
- [`P3 - XML parser maturity`](P3-expand-xml-parser-maturity-for-namespace-and-fragment-contracts.md)
- [`Default processor reference`](../../config/processor/default-processor.md)
- [`File ingestion hardening`](../../architecture/file-ingestion-hardening.md)

## Implementation notes

Treat this as a correctness-focused follow-on after T4 closure: source-aware identity extraction can be introduced without changing transform ordering or moving duplicate handling into source validation by default.

## Status notes

Created as a deferred follow-on when T4 moved to Done. Activate when nested XML duplicate cases require source-structure-aware identity beyond flat mapped fields.



