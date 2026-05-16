# Backlog item pages

Use this folder for item-level drill-down pages that sit under the execution board in [`../product-backlog.md`](../product-backlog.md) and beside the shared epic pages in [`../epics/README.md`](../epics/README.md).

## Purpose

These pages exist so every execution-board item can carry its own problem statement, scope boundary, acceptance criteria, and delivery notes without turning the board into a long narrative document.

## Maintenance rule

Keep the responsibilities separated:

- [`../product-backlog.md`](../product-backlog.md) is the canonical place for item-level `Priority`, `Status`, `Milestone`, and `Dependency`
- [`../epics/README.md`](../epics/README.md) and the epic pages hold shared capability intent across several backlog items
- this folder holds item-specific scope, acceptance criteria, and implementation notes
- backlog item pages should link back to their matching epic page so readers can move from one item to the broader capability track

## Naming rule

Use one file per backlog item in this format:

- `<ID>-<kebab-case-short-title>.md`

Examples:

- `A1-explicit-step-pairing-and-step-definitions.md`
- `T1a-processor-transform-spi-and-first-cleaner-normalization-slice.md`
- `V4-verification-report-retention-provenance-and-release-gating.md`

## Starting point

Use [`TEMPLATE.md`](TEMPLATE.md) when adding a new item page.

