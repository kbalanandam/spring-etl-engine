# E1 — Finalize cross-platform defaults and path handling rules

## Summary

Remove workstation-specific assumptions from defaults and tests so preserved scenarios and demo paths behave more predictably across environments.

## Current board status

- Epic: **[Epic E](../epics/epic-e-portability-and-packaged-run-guidance.md)**
- Priority: **P0**
- Status: **Done**
- Milestone: **M1**
- Dependency: **none**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Hard-coded or Windows-biased default paths made local verification and example usage less portable.

## Goal

Make default/runtime paths repo-relative and cross-platform friendly.

## Scope

- portable default path handling
- cleaner repo-relative demo/runtime paths
- test/runtime path normalization improvements

## Out of scope

- packaged-run documentation by itself
- secret injection
- transport/scheduler concerns

## Proposed approach

Normalize defaults and path resolution rules first so preserved examples and tests behave consistently across environments.

## Operator / runtime impact

- local and CI runs become more portable
- preserved bundles are easier to execute from the repo root
- docs/examples can stop implying one OS-specific layout

## Acceptance criteria

- [x] default/runtime paths no longer assume a Windows-specific location
- [x] test/runtime path handling is more portable
- [x] preserved examples remain runnable with repo-relative expectations

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`docs/README.md`](../../README.md)
- [`Config docs`](../../config/README.md)

## Implementation notes

E1 is the portability baseline that E2 builds on for packaged-run guidance.

## Status notes

Shipped baseline: defaults and tests now rely on more portable repo-relative behavior.

