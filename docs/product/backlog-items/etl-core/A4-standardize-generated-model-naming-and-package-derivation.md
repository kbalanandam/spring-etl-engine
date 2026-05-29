# A4 - Standardize generated-model naming and package derivation

## Summary

Define one shared naming contract for generated model packages, class names, and standardized generated comments so explicit job bundles can stop depending on authored `packageName` values while runtime lookup, XML generation, and processor mapping all stay deterministic and validated.

## Current board status

- Epic: **[Epic A](../../epics/epic-a-runtime-contract-and-model-governance.md)**
- Priority: **P1**
- Status: **Done**
- Milestone: **M2**
- Dependency: **A2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The runtime originally depended on authored `packageName`, implicit simple-name conventions, and XML element names doubling as Java type names. That made generated-model identity too Java-centric for the selected-job product contract and too vulnerable to naming drift.

## Goal

Establish one deterministic generated-model naming policy for the active selected-job contract that derives package and class identity from the selected job plus logical config names, keeps XML element names separate from Java type names, and adds the validation guardrails needed to remove authored `packageName` from supported runtime YAML.

## Scope

This item covers:

- a centralized naming policy for generated model package and simple-class derivation
- required use of `job-config.yaml -> name` as the long-term naming anchor for explicit job mode
- selected-job uniqueness of logical source/target names
- shared naming behavior for runtime resolution and build-time generation
- XML-specific `XmlRecord` and `XmlRoot` naming that preserves configured `rootElement` and `recordElement` through annotations instead of Java type names
- one standardized generated comment/header for human-readable OneFlow identity
- validation for duplicate logical names, forward-reference mistakes, naming collisions, and legacy `packageName` cleanup
- documentation and preserved-example alignment for the new naming contract

## Out of scope

This item does not cover:

- scenario auto-discovery or registry-style job selection
- a redesign of flat ordered Spring Batch execution into a nested runtime plan
- scheduler features or job pause/resume behavior
- XML namespace extensions
- a full immediate deletion of all legacy bridge behavior in one first step
- non-naming transformation or mapping feature expansion

## Proposed approach

The preferred delivery direction is:

1. keep `GeneratedModelClassResolver` as the central compatibility boundary, but move the naming decisions behind one explicit derived naming policy
2. derive source and target packages from the selected explicit job name using the existing job-scoped package direction
3. require logical source/target names to be unique across the selected job, with intentional reuse only for the same handoff artifact across steps
4. standardize generated class names as `<NormalizedLogicalName><Shape>` and keep source/target distinction in the derived package path instead of the simple class name
5. keep XML Java type names separate from XML contract names by using JAXB annotations for `rootElement` and `recordElement`
6. require one short standardized generated comment/header for developer readability, but keep package/class naming as the real lookup contract
7. remove authored `packageName` from the active explicit-job contract: explicit selected-job runs now fail when it is present, while remaining work focuses on final field cleanup
8. update docs, preserved bundles, and focused tests in the same slice so the contract is executable and reviewable

## Operator / runtime impact

Expected impact when this item ships:

- explicit job bundles become easier for non-Java config authors to understand because package names stop being a normal authored field
- startup failures become clearer when naming collisions, missing job names, or inconsistent intermediate handoff naming are present
- runtime and build-time generation should stop drifting because both paths share the same naming policy
- XML scenarios become more stable because XML element naming and Java class naming are no longer the same concern
- preserved docs and examples become clearer about the difference between logical config identity and generated Java identity
- generated class names remain shorter and cleaner because package path, not class prefix/suffix noise, carries source-vs-target identity
- generated source files should show one short standardized OneFlow-generated comment for reviewers without making comments part of runtime resolution

## Acceptance criteria

- [x] one documented naming policy derives generated packages from the selected explicit job name and config side
- [x] one documented naming policy derives generated class names from job-unique logical config names plus shape on the active selected-job path, without source/target markers or generated prefixes in the simple class name
- [x] explicit job mode requires a non-blank `job-config.yaml -> name` for the new contract
- [x] runtime and build-time generation use the same naming rules for flat and XML-generated models on the shipped selected-job/runtime-owned path, with centralized package resolution to keep those callers aligned
- [x] XML record/root Java class names no longer need to equal `recordElement` and `rootElement`, while JAXB output/input still honors the configured XML element names on the active runtime/build-time path
- [x] generated source files emit one standardized OneFlow-generated comment/header for human readability
- [x] validation fails fast when logical source/target names are not unique after normalization on the selected job path, when intermediate reuse is invalid, when forward references are incorrect, and when legacy `packageName` values are still authored on supported runtime YAML
- [x] preserved docs and preserved examples are updated to show the intended package-free supported contract, with remaining internal bridge retirement tracked separately

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Generated model naming standard`](../../../architecture/etl-core/generated-model-naming-standard.md)
- [`Adaptive step selection and generated-model contract`](../../../adr/etl-core/0003-adaptive-step-selection-and-generated-model-contract.md)
- [`Job config reference`](../../../config/job-config.md)

## Implementation notes

Keep the first delivery slice compatibility-aware. The most important product step is to freeze one naming policy and fail fast on drift, not to remove every bridge field everywhere in a single risky change.

A developer-local multi-step TVL-style example in `private-jobs/<collection>/xml-nested-to-csv-tag-validation/` is a useful design proof because it contains an ingress XML source, an intermediate CSV handoff, and a final relational target in one selected job.

## `packageName` deprecation plan

Treat `packageName` removal as a phased compatibility change, not a one-shot cleanup.

### Phase 1 - shipped package-free explicit-job baseline

- explicit `job-config.yaml` runs are now package-free and derive packages internally
- runtime and build-time generation already derive `...source` and `...target` packages from the selected non-blank job name
- generated-name collision checks and cross-step handoff-order checks are already active on the selected-job path

### Phase 2 - remove the field from docs and preserved examples

- selected-job source/target YAML, runtime-owned XML definition YAML, and bundled demo fallback YAML are now package-free
- config reference docs now describe `packageName` as unsupported in runtime YAML rather than as a normal authored field
- operator-facing errors name the selected job, config file, logical source/target name, and derived value

### Phase 3 - follow-on internal bridge retirement

- remaining internal cleanup now moves to [`A6 - Retire remaining internal generated-model package bridge`](A6-retire-internal-generated-model-package-bridge.md)
- that follow-on item covers config-object package cache removal, remaining compatibility defaults, and other non-contract bridge seams that no longer need to block A4 completion

## Status notes

- Updated to **Done** because the shipped selected-job contract is now package-free, requires non-blank job names, fails fast on naming drift, uses centralized package resolution, and keeps XML Java class naming separate from configured XML element names on the active runtime/build-time path.
- Runtime YAML no longer supports authored `packageName`; selected-job and direct-config paths both reject it before deserialization while still applying internal compatibility defaults only where preserved fallback behavior intentionally remains.
- Same-step logical-name reuse such as `Customers -> Customers` remains compatibility-supported on the active path; the shipped collision and handoff guardrails focus on deterministic generated identity without breaking preserved single-step bundles.
- Remaining internal bridge retirement is now tracked separately under [`A6`](A6-retire-internal-generated-model-package-bridge.md) so the delivered selected-job contract can be treated as complete.







