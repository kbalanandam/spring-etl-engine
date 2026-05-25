# Foundations architecture notes

Use this folder for architecture notes that explain the product direction or cross-cutting guardrails across more than one layer.

## Purpose

This folder is the shared home for notes that do not belong only to the ETL core, only to the control plane, or only to the operator UI.

Use it when you want to understand:

- the current architectural baseline for the whole product
- what belongs in the ETL-first phase versus later platform phases
- broad risks, watchpoints, and architecture-wide decision criteria

## Current anchor notes

- [`overview.md`](overview.md) — current high-level architecture baseline
- [`etl-product-evolution-roadmap.md`](etl-product-evolution-roadmap.md) — ETL-first roadmap and later platform direction
- [`architectural-risks-and-watchpoints.md`](architectural-risks-and-watchpoints.md) — key architecture risks and watchpoints
- [`security-test-strategy.md`](security-test-strategy.md) — cross-cutting security validation direction
- [`TEMPLATE.md`](TEMPLATE.md) — template for new architecture notes

## Layering note

This folder now holds the cross-cutting architecture notes directly.

Compatibility stubs remain temporarily at old root paths under `docs/architecture/` so existing links can continue to resolve during migration cleanup.


