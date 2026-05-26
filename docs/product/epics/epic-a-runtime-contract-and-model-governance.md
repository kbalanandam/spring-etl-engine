# Epic A - Runtime contract and generated-model governance

## Summary

Epic A covers the core runtime contract that keeps one selected job run explicit, predictable, and safe to operate. It includes orchestration guardrails, selected-job activation/startup checks, and the generated-model naming/package rules that keep runtime and build-time behavior aligned.

## Scope

This epic is the home for work that:

- keeps `job-config.yaml` as the explicit run entry point
- validates selected-job completeness before execution starts
- defines or hardens generated-model naming, package derivation, and handoff guardrails
- keeps multi-step orchestration explicit rather than inferred from config ordering side effects
- defines bounded pairing rules for customer-owned custom steps that run before/after standard steps without introducing a second orchestration model

This epic is **not** the place for transformation richness, retry/skip semantics, scheduling, or transport-specific work.

## Related backlog items

- [`A1 - Replace positional source-target pairing with explicit step pairing or step definitions`](../backlog-items/A1-explicit-step-pairing-and-step-definitions.md)
- [`A2 - Validate scenario completeness before job start`](../backlog-items/A2-validate-scenario-completeness-before-job-start.md)
- [`A3 - Add job-level activation guardrail so inactive selected jobs fail before wiring`](../backlog-items/A3-job-level-activation-guardrail.md)
- [`A4 - Standardize generated-model naming and package derivation`](../backlog-items/A4-standardize-generated-model-naming-and-package-derivation.md)
- [`A5 - Add relational source column alias contract and reader mapping`](../backlog-items/A5-relational-source-column-alias-contract.md)
- [`A6 - Retire remaining internal generated-model package bridge`](../backlog-items/A6-retire-internal-generated-model-package-bridge.md)
- [`A7 - Add custom-step pairing, context handoff, and failure-contract baseline`](../backlog-items/A7-custom-step-pairing-context-handoff-and-failure-contract.md)

## Related docs

- [`../../architecture/etl-core/runtime-flow.md`](../../architecture/etl-core/runtime-flow.md)
- [`../../architecture/etl-core/job-level-activation-and-startup-guardrails.md`](../../architecture/etl-core/job-level-activation-and-startup-guardrails.md)
- [`../../architecture/etl-core/generated-model-naming-standard.md`](../../architecture/etl-core/generated-model-naming-standard.md)
- [`../../architecture/etl-core/extension-points.md`](../../architecture/etl-core/extension-points.md)

## Maintenance note

Use [`../product-backlog.md`](../product-backlog.md) for the live item status, priority, milestone, and dependency values. Use this page only for the shared product boundary across the Epic A backlog items.
