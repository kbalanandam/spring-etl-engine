# B6 — Shared zip/unzip service boundary for file-based source preparation and archive packaging

## Summary

Track a shared zip/unzip service boundary that OneFlow can call whenever compression or extraction is needed, with the first product slice focused on file-based scenarios that unzip local input artifacts before processing starts and optionally emit zipped archive artifacts after successful completion.

## Current board status

- Epic: **[Epic B](../epics/epic-b-runtime-hardening-and-file-behavior.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **B3, E1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Many realistic business feeds arrive as compressed files, and operators often want completed inputs archived in compressed form to save space and keep ingress folders tidy.

The active product baseline already handles normal file validation and archive-on-success, but it does not yet define a first-class contract for unzipping a selected local source artifact before read-time processing or zipping the archived output after a successful run. Without that shared contract, teams fall back to one-off scripts, manual staging steps, or parser-specific shortcuts that weaken portability and operator clarity.

## Goal

Define a narrow, reviewable shared zip/unzip service contract so compression and extraction are handled through one reusable OneFlow-owned boundary instead of ad hoc scenario logic, starting with CSV and XML file flows and staying reusable for later JSON file scenarios without implying a broader JSON source-parser commitment.

## Scope

This item covers:

- a shared zip/unzip service boundary that OneFlow can invoke for unzip and zip operations when needed
- a first-slice config/runtime contract for unzipping supported local source artifacts before normal file processing begins
- a first-slice config/runtime contract for writing zipped archive artifacts after successful file-based runs
- behavior that is format-agnostic across file-backed flows such as CSV, XML, and future JSON file scenarios when those flows are otherwise supported
- source-validation, runtime-evidence, and documentation updates needed to make compressed-file behavior explicit
- preserved scenario coverage that proves the intended unzip-before-process and zip-on-archive lifecycle

## Out of scope

This item does not cover:

- remote transport acquisition or partner-delivery compression rules; those stay under `Epic X`
- broad parser redesign work or parser-native compressed-stream ownership under `Epic P`
- a new commitment to general JSON source parsing on the active roadmap
- multi-entry archive extraction policies beyond the first narrow supported contract
- password-protected or encrypted archive handling in the first slice

## Proposed approach

Preferred implementation direction:

1. define zip/unzip as an independent shared service boundary that OneFlow can call where needed, not as CSV-only or XML-only parser logic
2. keep the first product contract narrow: explicit opt-in settings, predictable local path behavior, and one documented supported archive shape
3. use that shared service first on the file-ingestion path to perform unzip work before normal source validation and reader processing consume the prepared file
4. reuse the existing archive-on-success lifecycle so the same shared service can support optional zip-on-archive behavior aligned with operator evidence
5. document clearly that the service boundary is reusable for file-backed scenario types without reopening the deferred JSON parser roadmap by itself

## Operator / runtime impact

Expected impact when this item ships:

- operators can run more realistic inbound file scenarios without separate pre-processing scripts
- source-file disposition becomes clearer because both extraction and archive packaging behavior are explicit
- future OneFlow features can call the same shared service boundary instead of introducing new zip/unzip logic each time
- preserved scenario bundles can prove compressed-input and compressed-archive behavior end to end
- zip/unzip behavior stays centralized in one reusable service boundary instead of drifting into parser-specific or scenario-specific branching

## Acceptance criteria

- [ ] OneFlow has a documented shared zip/unzip service boundary that can be called for either unzip or zip behavior when needed
- [ ] selected file-based scenarios can opt into unzip-before-process behavior through that shared service boundary
- [ ] selected file-based scenarios can opt into zip-on-archive behavior after successful completion through that same shared service boundary
- [ ] the first supported contract is documented with explicit scope limits, including how CSV/XML flows use it and how it remains reusable for later JSON file scenarios without changing the active parser roadmap
- [ ] runtime evidence and preserved scenario coverage make compressed-file preparation and archive outcomes operator-visible

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`File ingestion hardening`](../../architecture/file-ingestion-hardening.md)
- [`Config docs index`](../../config/README.md)

## Implementation notes

Keep this backlog item narrow and reality-first. The value is not “support every archive format”; it is to define one reusable OneFlow-owned zip/unzip service boundary, then apply it first to real file-based scenarios without letting compression drift into parser-specific logic or one-off scenario scripts.

## Status notes

Deferred for later implementation. When work starts, prefer one explicit shared service contract plus at least one preserved scenario bundle that proves unzip-before-process and zip-on-archive behavior together through the same reusable boundary.



