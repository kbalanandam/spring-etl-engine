# T5 — Reference-set validation and enrichment baseline

## Summary

Define the first lookup/enrichment slice on the active processor path, starting with database-backed reference-set validation for accept/reject decisions such as checking whether `agencyCode` exists in an allowed runtime-loaded set before the record is written.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M2**
- Dependency: **T2**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

## Frozen planning direction

The first slice for `T5` is now intentionally frozen for backlog and architecture planning as:

- processor-side reference-set validation
- database-backed loading through a relational query
- named placeholders such as `referenceSet: agencyCodes`
- load-once, cache-and-reuse runtime behavior
- reject/accept validation first, with broader enrichment deferred

This freeze is for planning clarity only:

- it does **not** change `Status: Deferred`
- it is **not** a shipped config contract today
- it is **not** part of the active runtime path yet

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The product already supports processor-side transforms and record-level validation rules, but it still lacks a clean way to validate values against runtime reference data.

Typical example:

- input contains `agencyCode`
- allowed codes such as `0012`, `0013`, and later values live in a database table
- records with missing or unknown agency codes should be rejected
- config authors should not need custom Java for every scenario

Without an explicit contract, teams will either hardcode allow-lists in YAML, push business validation into source validation, or create job-specific code paths.

## Goal

Add one explicit processor-side reference-set validation pattern so config can name a runtime-loaded reference set and a field rule can reject records when the field value is not present in that set.

## Scope

This item covers:

- reject/accept validation against runtime-loaded reference sets
- database-backed allow-list checks such as agency code validation
- named placeholders in config such as `referenceSet: agencyCodes`
- startup validation for missing reference-set definitions and unsupported loading modes
- one first cache/load policy for reference sets during a run
- a future path from validation-only reference sets toward broader lookup/enrichment behavior

## Out of scope

This item does not cover:

- broad multi-table join logic inside the processor
- target-side upsert/merge semantics
- source-level artifact validation
- hidden writer-side defaults or validation
- a generic scripting or rules engine

## Proposed approach

The frozen first slice is:

1. keep this on the active processor-rule seam, not source validation
2. add a future processor rule type such as `referenceSet`
3. let `rules[]` reference a named reference set by placeholder, for example `referenceSet: agencyCodes`
4. define the named reference sets once per processor config so multiple field rules can reuse them
5. load each reference set from the declared relational source once per step or job, then cache it for rule evaluation
6. reject the record when the configured field value is absent from the loaded set

### Illustrative future config shape (draft only)

Illustrative example only — frozen for planning, not a shipped contract yet:

```yaml
type: default
referenceSets:
  agencyCodes:
    sourceType: relationalQuery
    connection:
      vendor: sqlserver
      host: <SQLSERVER_HOST>
      port: 1433
      database: <SQLSERVER_DATABASE>
      username: <SQLSERVER_USERNAME>
      password: <SQLSERVER_PASSWORD>
    query: "SELECT agency_code FROM dbo.ref_agency WHERE is_active = 1"
    valueColumn: agency_code
    cacheScope: job
mappings:
  - source: PartnerOrdersCsv
    target: PartnerOrdersSql
    fields:
      - from: agencyCode
        to: agencyCode
        rules:
          - type: notNull
            onFailure: rejectRecord
          - type: referenceSet
            referenceSet: agencyCodes
            onFailure: rejectRecord
```

This keeps the placeholder simple for the rule author:

- the field rule only says `referenceSet: agencyCodes`
- the runtime resolves that name against a shared `referenceSets:` block
- the loaded set is reused across records instead of querying the database per row

## Operator / runtime impact

Expected impact when this item ships:

- business validation against live reference tables becomes configurable instead of hardcoded
- records can be rejected based on current database-backed allowed values
- startup failures can be clearer when a referenced set is missing or misconfigured
- runtime cost stays predictable if reference sets are loaded once and cached per run scope

## Acceptance criteria

- [ ] the product defines one documented processor-side contract for runtime-loaded reference-set validation
- [ ] a field rule can reference a named set placeholder such as `referenceSet: agencyCodes`
- [ ] the first slice supports relational-query-backed reference sets for reject/accept decisions
- [ ] config validation fails fast for missing `referenceSets` entries, invalid loading config, and ambiguous rule definitions
- [ ] runtime loads and caches the set predictably rather than querying the database for every record
- [ ] at least one preserved or private relational-style job proves reject behavior against a runtime-loaded reference table when implementation begins

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Reference-set validation and enrichment`](../../architecture/reference-set-validation-and-enrichment.md)
- [`Validation extension architecture`](../../architecture/validation-extension-architecture.md)
- [`Relational database support`](../../architecture/relational-db-support.md)
- [`Default processor reference`](../../config/processor/default-processor.md)

## Implementation notes

Current code anchors for this future item are:

- `src/main/java/com/etl/config/processor/ProcessorConfig.java`
- `src/main/java/com/etl/processor/validation/ProcessorValidationRule.java`
- `src/main/java/com/etl/processor/validation/ValidationRuleEvaluator.java`
- `src/main/java/com/etl/mapping/ValidationAwareDynamicMapping.java`
- `src/main/java/com/etl/config/relational/RelationalConnectionConfig.java`
- `src/main/java/com/etl/config/relational/RelationalDataSourceFactory.java`

The first slice should stay narrow: validation against a loaded reference set, not full enrichment writes.

## Status notes

Added to turn generic “lookup/enrichment” language into a concrete first delivery shape: reference-set validation before broader enrichment joins or derived lookup outputs.

