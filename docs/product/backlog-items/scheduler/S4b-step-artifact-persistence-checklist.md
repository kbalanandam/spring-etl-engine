# S4b - Step/Artifact persistence checklist

Use this checklist to execute the `S4b` slice under [`S4-control-plane-operational-data-model.md`](S4-control-plane-operational-data-model.md) in small, review-friendly commits.

## Scope guardrails

- [x] limit this slice to durable `step_record` + `artifact_record` persistence
- [x] keep `attempt_link` and `checkpoint_anchor` out of this slice
- [x] preserve optional-control-plane boundary (direct ETL worker runs with no control-plane DB)

## Schema and startup initialization

- [x] add `controlplane_step_record` table initialization
- [x] add `controlplane_artifact_record` table initialization
- [x] add indexes for expected lookup paths (run, step, status/time where applicable)
- [x] ensure startup behavior is compatibility-safe for existing local SQLite files

## Ownership and linkage invariants

- [x] enforce one artifact owner per row (run-level or step-level)
- [x] when step-linked, ensure run lineage consistency
- [x] avoid nullable combinations that allow ambiguous artifact ownership

## Write paths

- [x] add projection/write path for retained step outcomes under retained run identity
- [x] add projection/write path for run-level artifact evidence references
- [x] add projection/write path for step-level artifact evidence references

## Read paths

- [x] add read/query path for step history by run
- [x] add read/query path for artifact history by run
- [x] add read/query path for artifact history by step

## Test coverage

- [x] schema-shape tests for new tables/columns/indexes
- [x] invariant tests for artifact ownership/linkage
- [x] write/read tests for step and artifact persistence
- [x] compatibility tests for startup on pre-S4b local SQLite state

## Docs and board alignment

- [x] update architecture docs for shipped step/artifact persistence behavior
- [ ] update `docs/product/product-backlog.md` notes to reflect S4b completion when done
- [x] update changelog entry wording for the shipped S4b slice

## Verification

- [x] run focused JDBC tests for schedule/trigger/run + new step/artifact persistence tests
- [x] run full repo verification script before final merge

