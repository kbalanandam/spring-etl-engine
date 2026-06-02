# S4b - Step/Artifact persistence checklist

Use this checklist to execute the `S4b` slice under [`S4-control-plane-operational-data-model.md`](S4-control-plane-operational-data-model.md) in small, review-friendly commits.

## Scope guardrails

- [ ] limit this slice to durable `step_record` + `artifact_record` persistence
- [ ] keep `attempt_link` and `checkpoint_anchor` out of this slice
- [ ] preserve optional-control-plane boundary (direct ETL worker runs with no control-plane DB)

## Schema and startup initialization

- [ ] add `controlplane_step_record` table initialization
- [ ] add `controlplane_artifact_record` table initialization
- [ ] add indexes for expected lookup paths (run, step, status/time where applicable)
- [ ] ensure startup behavior is compatibility-safe for existing local SQLite files

## Ownership and linkage invariants

- [ ] enforce one artifact owner per row (run-level or step-level)
- [ ] when step-linked, ensure run lineage consistency
- [ ] avoid nullable combinations that allow ambiguous artifact ownership

## Write paths

- [ ] add projection/write path for retained step outcomes under retained run identity
- [ ] add projection/write path for run-level artifact evidence references
- [ ] add projection/write path for step-level artifact evidence references

## Read paths

- [ ] add read/query path for step history by run
- [ ] add read/query path for artifact history by run
- [ ] add read/query path for artifact history by step

## Test coverage

- [ ] schema-shape tests for new tables/columns/indexes
- [ ] invariant tests for artifact ownership/linkage
- [ ] write/read tests for step and artifact persistence
- [ ] compatibility tests for startup on pre-S4b local SQLite state

## Docs and board alignment

- [ ] update architecture docs for shipped step/artifact persistence behavior
- [ ] update `docs/product/product-backlog.md` notes to reflect S4b completion when done
- [ ] update changelog entry wording for the shipped S4b slice

## Verification

- [ ] run focused JDBC tests for schedule/trigger/run + new step/artifact persistence tests
- [ ] run full repo verification script before final merge

