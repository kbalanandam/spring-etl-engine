# S4c - Attempt-link/checkpoint-anchor checklist

Use this checklist to execute the `S4c` slice under [`S4-control-plane-operational-data-model.md`](S4-control-plane-operational-data-model.md) in small, review-friendly commits.

## Scope guardrails

- [x] limit this slice to durable `attempt_link` + `checkpoint_anchor` persistence
- [x] preserve optional-control-plane boundary (direct ETL worker runs with no control-plane DB)
- [x] keep scheduler overlap/missed-run policy redesign out of this slice

## Data-model and schema shape

- [x] define `attempt_link` table shape with stable retained identifiers
- [x] define `checkpoint_anchor` table shape with explicit anchor identity/lifecycle fields
- [x] add indexes for recovery-oriented lookup paths
- [x] keep external IDs stable while using relational PK linkage internally

## Linkage and invariants

- [x] enforce valid current/prior attempt lineage semantics
- [x] enforce checkpoint ownership invariants to avoid ambiguous associations
- [x] define behavior for missing/expired checkpoint anchors

## Write paths

- [x] add projection/write path for attempt lineage records
- [x] add projection/write path for checkpoint anchor records
- [x] ensure writes remain best-effort where control-plane persistence is optional

## Read paths

- [x] add read/query path for attempt lineage by run/job context
- [x] add read/query path for checkpoint anchors by run/step context
- [x] expose minimal retained read-model fields needed for operator diagnosis

## Test coverage

- [x] schema-shape tests for new tables/columns/indexes
- [x] invariant tests for attempt/checkpoint ownership and linkage
- [x] compatibility tests for startup on pre-S4c local SQLite state
- [x] retention/cleanup tests covering cascading deletion from parent run records

## Docs and board alignment

- [x] update architecture docs for shipped S4c behavior and ER semantics
- [x] update `docs/product/product-backlog.md` notes to reflect S4c progress/completion state
- [x] update changelog wording for the shipped S4c slice

## Verification

- [x] run focused JDBC tests for schedule/trigger/run + S4b + new S4c persistence tests
- [x] run full repo verification script before final merge

