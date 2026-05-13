# A4 — Standardize generated-model naming and package derivation

## Summary

Define one shared naming contract for generated model packages, class names, and standardized generated comments so explicit job bundles can stop depending on authored `packageName` values while runtime lookup, XML generation, and processor mapping all stay deterministic and validated.

## Current board status

- Epic: **Epic A**
- Priority: **P1**
- Status: **In Progress**
- Milestone: **M2**
- Dependency: **A2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

## Problem

The product already has a centralized generated-model resolver and bridge support for default package derivation in explicit `job-config.yaml` runs, but the active contract is still too dependent on Java-centric authored config.

Today that leaves several product and runtime issues:

- source and target YAML can still carry handwritten `packageName` values
- runtime lookup and build-time generation still depend on simple-name conventions that are not fully explicit as a product contract
- XML Java type names are still too tightly coupled to XML element names
- generated identity still needs to be obvious to reviewers without relying on noisy class prefixes when package path already conveys that distinction
- naming drift is still possible between config, generated classes, runtime resolution, and preserved scenario docs

## Goal

Establish one deterministic generated-model naming policy that derives package and class identity from the selected job plus logical config names, keeps XML element names separate from Java type names, and adds the validation guardrails needed before authored `packageName` can be removed from source and target configs.

## Scope

This item covers:

- a centralized naming policy for generated model package and simple-class derivation
- required use of `job-config.yaml -> name` as the long-term naming anchor for explicit job mode
- selected-job uniqueness of logical source/target names
- shared naming behavior for runtime resolution and build-time generation
- XML-specific `XmlRecord` and `XmlRoot` naming that preserves configured `rootElement` and `recordElement` through annotations instead of Java type names
- one standardized generated comment/header for human-readable OneFlow identity
- validation for duplicate logical names, forward-reference mistakes, naming collisions, and legacy `packageName` mismatch
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
7. treat authored `packageName` as a temporary bridge only: matching values warn, conflicting values fail
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

- [ ] one documented naming policy derives generated packages from the selected explicit job name and config side
- [ ] one documented naming policy derives generated class names from job-unique logical config names plus shape, without source/target markers or generated prefixes in the simple class name
- [ ] explicit job mode requires a non-blank `job-config.yaml -> name` for the new contract
- [ ] runtime and build-time generation use the same naming rules for flat and XML-generated models
- [ ] XML record/root Java class names no longer need to equal `recordElement` and `rootElement`, while JAXB output/input still honors the configured XML element names
- [ ] generated source files emit one standardized OneFlow-generated comment/header for human readability
- [ ] validation fails fast when logical source/target names are not unique across the selected job, when intermediate reuse is invalid, when forward references are incorrect, and when legacy `packageName` values conflict with the derived contract
- [ ] preserved docs and at least one preserved or private-style multi-step example are updated to show the intended contract

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Generated model naming standard`](../../architecture/generated-model-naming-standard.md)
- [`Adaptive step selection and generated-model contract`](../../adr/0003-adaptive-step-selection-and-generated-model-contract.md)
- [`Job config reference`](../../config/job-config.md)

## Implementation notes

Keep the first delivery slice compatibility-aware. The most important product step is to freeze one naming policy and fail fast on drift, not to remove every bridge field everywhere in a single risky change.

A developer-local multi-step TVL-style example in `private-jobs/<collection>/xml-nested-to-csv-tag-validation/` is a useful design proof because it contains an ingress XML source, an intermediate CSV handoff, and a final relational target in one selected job.

## `packageName` deprecation plan

Treat `packageName` removal as a phased compatibility change, not a one-shot cleanup.

### Phase 1 — shipped bridge baseline

- explicit `job-config.yaml` runs may already omit `packageName`
- runtime and build-time generation already derive `...source` and `...target` packages from the selected non-blank job name
- generated-name collision checks and cross-step handoff-order checks are already active on the selected-job path

### Phase 2 — deprecate authored use without breaking active bundles

- mark `packageName` in config docs as a deprecated bridge field rather than a normal authored field
- keep accepting omitted values as the preferred path
- keep accepting explicit matching values for compatibility while steering new bundles away from them
- make operator-facing warnings/errors name the selected job, config file, logical source/target name, authored value, and derived value

### Phase 3 — tighten drift handling

- fail fast when authored `packageName` conflicts with the derived contract on the active explicit-job path
- keep this step separate from Phase 2 so preserved bundles and private bundles can be cleaned up deliberately
- update focused tests so runtime and build-time paths enforce the same mismatch behavior

### Phase 4 — package-free preserved examples and docs

- remove `packageName` from preserved `config-jobs/...` examples except for one intentionally documented deprecated bridge example if still needed
- update config reference pages so package-free YAML becomes the default authoring style everywhere
- keep docs explicit that generated package identity still exists, but it is now product-derived rather than authored

### Phase 5 — final field removal

- stop binding `packageName` in source/target config classes only after docs, preserved bundles, and upgrade guidance are ready
- keep runtime lookup fully centralized behind the shared naming policy and resolver
- mark A4 done only when authored `packageName` is no longer part of the normal config contract

## Status notes

- Updated to **In Progress** because the active runtime and build-time bridge now ship default source/target package derivation for explicit `job-config.yaml` runs when `packageName` is omitted.
- The active explicit-job naming path now also requires a non-blank `job-config.yaml -> name` during runtime loading and build-time XML generation, so folder-name fallback is no longer part of the shipped generated-model contract.
- The active runtime and build-time path now also fail fast when selected logical names collide after generated-name normalization and when a named handoff is consumed before any earlier step produced it.
- Same-step logical-name reuse such as `Customers -> Customers` remains compatibility-supported on the active path; the stricter selected-job uniqueness direction is now focused on cross-step handoff reuse and collision safety rather than breaking shipped single-step bundles immediately.
- Remaining A4 scope is still open: stricter legacy `packageName` tightening, richer warning/deprecation handling, XML Java-name vs XML-element separation rollout across all paths, and the broader package-free authored-config direction documented in the architecture note.





