# B5 — CSV reader parsing hardening

## Summary

Capture the shipped practical CSV reader improvement: preserve the current simple delimited-reader baseline, while allowing scenario authors to opt into explicit quote-character handling for more realistic CSV feeds.

## Current board status

- Epic: **Epic B**
- Priority: **P2**
- Status: **Done**
- Milestone: **M2**
- Dependency: **none**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

## Problem

`CsvDynamicReader` currently builds a `DelimitedLineTokenizer` with the configured delimiter and field names, which is adequate for simple CSV inputs.

The current source contract does not yet expose parser controls for quoted values, escaped delimiters, or similar real-world CSV quirks. That leaves a gap for vendor exports where embedded commas, quoting rules, or escaped content are part of the normal file contract.

## Goal

Add a small, explicit CSV parsing-hardening contract so the reader can handle realistic quoted/escaped CSV inputs without forcing teams into ad hoc preprocessing or one-off reader forks.

## Scope

This item covers:

- explicit CSV source-config fields for quote/escape parsing behavior
- `CsvDynamicReader` support for those parsing options
- focused tests for quoted delimiters and escaped content
- documentation updates to the CSV source contract and current limitations

## Out of scope

This item does not cover:

- schema inference for CSV columns
- broad alternate header-mapping policies
- source-native transform chains for CSV cleanup before normal records exist
- turning the current CSV reader bridge into a large parser framework

## Proposed approach

Preferred implementation direction:

1. keep today's default behavior unchanged for simple CSV scenarios
2. extend `CsvSourceConfig` with a narrow parser contract for quoting/escaping
3. configure `DelimitedLineTokenizer` or the narrowest compatible Spring Batch reader seam with those settings
4. add focused automated coverage for realistic CSV examples with embedded delimiters inside quoted fields
5. document the contract clearly so preserved scenario bundles can opt in only when needed

## Operator / runtime impact

Expected impact when this item ships:

- more real-world CSV feeds can be consumed without pre-cleaning scripts
- scenario authors gain an explicit parser contract instead of hidden assumptions
- simple CSV bundles remain unchanged unless they need the added parsing behavior
- test evidence becomes clearer for quoted-field edge cases

## Acceptance criteria

- [x] current simple CSV scenarios continue to work unchanged by default
- [x] the source config exposes a documented, narrow quote/escape parsing contract
- [x] quoted delimiter cases are parsed correctly through the active CSV reader path
- [x] automated tests cover common quoted/escaped CSV edge cases
- [x] CSV config docs clearly describe both the new contract and any remaining limitations

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Extension points`](../../architecture/extension-points.md)
- [`File ingestion hardening`](../../architecture/file-ingestion-hardening.md)
- [`CSV source reference`](../../config/source/csv-source.md)

## Implementation notes

Keep this item intentionally narrow. The goal is to harden the active CSV reader path for common quoted/escaped data, not to introduce a broad alternate CSV parsing subsystem or a source-transform framework.

## Status notes

Shipped as a narrow CSV reader hardening slice: `CsvSourceConfig` now exposes optional `parser.quoteCharacter`, the active CSV reader applies that setting through Spring Batch `DelimitedLineTokenizer`, doubled quote-character escaping is supported on the read path, and focused tests/docs cover the opt-in behavior while leaving broader escape-framework work deferred.

