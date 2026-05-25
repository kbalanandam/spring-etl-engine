# E2 - Add packaged-run guidance for jar execution with scenario configs

## Summary

Document and preserve the supported packaged-run workflow so teams can execute selected jobs from the built jar with clear expectations around config paths and generated-model prerequisites.

## Current board status

- Epic: **[Epic E](../epics/epic-e-portability-and-packaged-run-guidance.md)**
- Priority: **P1**
- Status: **Ready**
- Milestone: **M1**
- Dependency: **E1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The runtime supports jar execution, but contributor-facing guidance is still stronger for source-tree or local development runs than for the packaged path.

## Goal

Make packaged-run expectations and commands a first-class documented path for selected jobs.

## Scope

- packaged jar execution guidance for selected-job runs
- scenario-path guidance for preserved bundles
- clarification around XML generation prerequisites when required

## Out of scope

- changing the runtime contract itself
- introducing a new deployment launcher
- scheduler/control-plane packaging

## Proposed approach

Expand docs and preserved examples so packaged selected-job execution is explained as a supported portability path, not only a local developer fallback.

## Operator / runtime impact

- operators and reviewers gain clearer packaged-run guidance
- scenario bundle documentation becomes easier to follow outside IDE-driven runs
- XML-generation prerequisites stay visible where needed

## Acceptance criteria

- [ ] packaged-run guidance is documented in the active docs set
- [ ] preserved example commands cover jar execution clearly
- [ ] XML-generation prerequisite guidance remains explicit for scenarios that require it

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`docs/README.md`](../../README.md)
- [`Config docs`](../../config/README.md)

## Implementation notes

This is the next portability/documentation step after the shipped cross-platform path cleanup.

## Status notes

Ready for a documentation-first slice that keeps the existing runtime contract unchanged.

