# E3 - Centralize product-brand naming and doc refresh automation

## Summary

Define one controlled source of truth for product-facing brand naming so future rebrands can refresh approved docs and related copy consistently, while keeping `spring-etl-engine` as the stable technical identity for code, packages, Maven coordinates, and file paths.

## Current board status

- Epic: **[Epic E](../../epics/etl-core/epic-e-portability-and-packaged-run-guidance.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **none**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The repository currently treats `spring-etl-engine` as the stable technical identity and **OneFlow** as product-facing copy, which is the correct boundary.

What is still missing is a centralized, reviewable way to refresh product-facing naming when the brand changes. Today that wording lives in several docs and a small amount of generated copy, so a future rename would rely on manual edits or a risky broad search/replace that could accidentally touch technical identifiers that must remain stable.

## Goal

Create a narrow, explicit product-brand naming contract so approved brand-facing names can be changed once and then refreshed across supported documentation and copy surfaces in a controlled way, without implying that technical repository identity should also be renamed.

## Scope

This item covers:

- a single source of truth for product-facing brand values such as display name and approved dashboard wording
- a controlled refresh approach for supported brand-facing docs and copy surfaces
- explicit documentation of which surfaces are rebrandable and which must stay on `spring-etl-engine`
- guardrails that exclude package names, class names, Maven coordinates, environment keys, and file paths from automatic brand refresh
- documentation updates for the approved workflow when future brand wording changes

## Out of scope

This item does not cover:

- renaming the repository’s technical identity away from `spring-etl-engine`
- broad automatic rewriting of source code, packages, or Maven coordinates
- asset redesign or binary/image regeneration by itself
- changing GitHub Project settings or external platform metadata automatically unless that contract is explicitly added later
- introducing a runtime feature or changing the ETL execution model

## Proposed approach

Preferred implementation direction:

1. keep the technical/product naming split explicit: `spring-etl-engine` stays technical, brand-facing copy stays centrally managed
2. define one approved source of truth for brand-facing values
3. refresh only approved text surfaces through a controlled script or template workflow rather than a blind repository-wide replace
4. document exceptions such as historical asset names or technical identifiers that must stay unchanged
5. keep the first slice docs-first and low-risk before any broader metadata automation is considered

## Operator / runtime impact

Expected impact when this item ships:

- future brand changes become easier to apply consistently across approved docs
- maintainers reduce the risk of accidental renames in technical identifiers
- GitHub-facing wording stays aligned with the approved promotion guide
- runtime behavior remains unchanged because this is documentation and naming-governance work, not ETL-core work

## Acceptance criteria

- [ ] the repository documents one approved source of truth for product-facing brand naming
- [ ] supported refreshable docs/copy surfaces are listed explicitly, together with non-refreshable technical surfaces that must remain on `spring-etl-engine`
- [ ] the preferred refresh workflow is documented as a controlled process rather than a blind global replace
- [ ] at least the primary product-facing docs can be refreshed consistently from that approved branding contract without changing technical identifiers accidentally

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`GitHub Promotion Guide`](../github-promotion.md)
- [`README`](../../README.md)

## Implementation notes

Keep this item intentionally narrow. The value is not a full marketing automation system; it is a safe, maintainable way to refresh product-facing naming while preserving the repo rule that technical identity and product branding are different contracts.

## Status notes

Deferred for later implementation. When work starts, prefer a docs-first source-of-truth plus controlled refresh workflow before expanding into broader metadata or asset automation.


