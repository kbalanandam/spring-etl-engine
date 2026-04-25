# Product Backlog

## Purpose

This document preserves the product goal for `spring-etl-engine` from the starting point to the current state, and from the current state toward an enterprise-grade ETL product.

It is the single product roadmap and execution backlog for the product at this stage. Capabilities such as scheduling/orchestration should be tracked here as dedicated epics, not maintained in separate standalone roadmaps unless they later become truly independent platform products.

It is intentionally different from the architecture roadmap:

- `docs/architecture/etl-product-evolution-roadmap.md` explains **direction and phases**
- this backlog explains **what to do next, why it matters, and how to know when it is done**

Use this file to keep the team aligned when implementation pressure, feature requests, or uncertainty create drift.

This file now serves two purposes at the same time:

- **narrative backlog** — preserve product intent, capability gaps, and milestone outcomes
- **execution board** — track what should start next, what is in progress now, and what is blocked

---

## Product Goal

Build a config-driven ETL product that can reliably:

1. read data from supported sources
2. transform it through explicit mappings and validation rules
3. write it to supported targets
4. be operated, diagnosed, and extended safely
5. evolve into an enterprise-grade ETL and integration foundation

---

## Current Product Snapshot

### What already exists

The product already has a meaningful engineering foundation:

- Spring Batch-based ETL runtime
- config-driven source / target / processor model
- explicit `job-config.yaml` scenario selection
- explicit `steps`-driven orchestration for business-scenario runs
- dynamic reader / processor / writer factories
- generated model contract approach
- CSV, XML, and relational support foundations
- scenario-aware logging with run correlation metadata
- machine-readable run and step lifecycle events for operator evidence
- daily scenario log layout
- architecture docs and ADR discipline
- automated tests across config loading, listeners, app context, and flow slices
- improved path portability for tests and default demo runtime behavior
- fail-fast relational placeholder validation for selected scenario configs
- repeatable verification reporting with categorized Markdown evidence output

### Current maturity assessment

| Area | Status |
|---|---|
| Config-driven design | Strong |
| Scenario selection | Strong |
| Connector foundation | Good |
| Logging / traceability | Good |
| Test discipline | Good |
| Runtime portability | Improving |
| Fault tolerance | Emerging |
| Restartability / idempotency | Emerging |
| Security / secret handling | Emerging |
| Audit / reconciliation | Gap |
| Operational metrics / dashboards | Gap |
| Enterprise governance | Gap |

### What the product is today

Today the product is best described as:

> a serious ETL engine foundation that is beyond prototype stage, but not yet an enterprise-grade ETL product.

That is a healthy stage.

---

## Product Principles

When choosing backlog priorities, prefer work that improves one or more of these:

- reliability
- explicitness
- operator visibility
- portability
- extensibility
- testability
- security readiness

Avoid work that adds broad platform complexity before the ETL foundation is stable.

---

## Backlog Structure

This backlog is organized in three layers:

1. **Completed / established foundation**
2. **Near-term backlog** — needed to become a reliable ETL product
3. **Enterprise backlog** — needed to become an enterprise-grade product

It also includes a lightweight execution board so the team can work from the same document day to day.

---

## How to Use This Document

Use this file in two modes:

### 1. Product mode
Use the narrative sections to answer:

- what are we building?
- what stage are we in?
- what does enterprise-grade mean for this product?
- what capabilities matter most next?

### 2. Execution mode
Use the execution board to answer:

- what are we actively doing now?
- what is next?
- what is blocked?
- which milestone does this work belong to?

---

## Execution Board Conventions

### Priority legend

- **P0** — critical now; should be completed before lower-priority work expands
- **P1** — important next; should follow once P0 items are stable
- **P2** — useful later; valuable but not urgent for the current milestone

### Status legend

- **Ready** — approved to start when capacity is available
- **In Progress** — actively being worked
- **Blocked** — cannot move until a dependency or decision is resolved
- **Done** — completed to the expected operational level
- **Deferred** — intentionally postponed to a later milestone

---

## Current Execution Board

This table is the day-to-day execution view for the current product stage.

| ID | Item | Epic | Priority | Status | Milestone | Dependency | Notes |
|---|---|---|---|---|---|---|---|
| A1 | Replace positional source-target pairing with explicit step pairing or step definitions | Epic A | P0 | Done | M1 | none | Explicit `steps` orchestration is now the selected-scenario runtime contract |
| A2 | Validate scenario completeness before job start | Epic A | P0 | Done | M1 | A1 | Startup now fails fast for missing `steps`, missing referenced files, and unknown named step bindings |
| T1 | Add field-level validation rules and first reject-handling slice for file scenarios | Epic T | P1 | Ready | M1 | A1 | Start with configurable `notNull` and time-format checks plus controlled rejected-record output |
| T2 | Add expression-based derived field support | Epic T | P1 | Deferred | M2 | T1 | Restore the next explicit transformation step after the first validation/reject slice is stable |
| T3 | Add conditional transformation rule support | Epic T | P1 | Deferred | M2 | T2 | Best introduced after expression contract is stable |
| T4 | Expand validation and reject/quarantine handling in transformation flow | Epic T | P1 | Deferred | M2 | T1, T2, T3 | Broaden beyond the first file-based validation slice into richer transformation behavior |
| T5 | Define lookup/enrichment processor baseline | Epic T | P1 | Deferred | M2 | T2 | Bridges toward more classic ETL transformation patterns |
| B1 | Introduce configurable skip policy support | Epic B | P1 | Deferred | M1 | A1 | Better after orchestration rules are explicit |
| B2 | Introduce configurable retry policy support where appropriate | Epic B | P1 | Deferred | M1 | B1 | Add after failure handling model is defined |
| B3 | Archive processed source files after successful file-based runs | Epic B | P1 | Ready | M1 | A1, T1 | First file lifecycle slice should archive originals only after successful processing |
| C1 | Emit machine-readable run summary with scenario, status, and duration | Epic C | P1 | Done | M1 | none | `RUN_EVENT` / `RUN_SUMMARY` and step lifecycle evidence are now emitted for selected runs |
| C2 | Capture source count, written count, and rejected count | Epic C | P1 | In Progress | M1 | C1 | Step-finished evidence now includes read/write/filter/skip counts; run-level reconciliation rollup remains to be completed |
| D1 | Add stable error taxonomy / error categories | Epic D | P1 | Deferred | M2 | C1 | Best done after run-summary model exists |
| E1 | Finalize cross-platform defaults and path handling rules | Epic E | P0 | Done | M1 | none | Portable defaults and test/runtime path cleanup completed |
| E2 | Add packaged-run guidance for jar execution with scenario configs | Epic E | P1 | Ready | M1 | E1 | Important next portability step |
| F1 | Define restart semantics per execution mode | Epic F | P1 | Deferred | M2 | A1, C1 | Needs clearer orchestration and run evidence first |
| S1 | Define schedule model and trigger contract for scenario-based execution | Epic S | P1 | Deferred | M2 | A1, C1 | Keep scheduler work inside the main product roadmap; establish scope before implementation |
| S2 | Add time-based schedule definitions with pause/resume controls | Epic S | P1 | Deferred | M2 | S1 | First practical scheduler slice after run-state and audit direction are clearer |
| S3 | Add overlap policy, missed-run handling, and basic trigger audit trail | Epic S | P1 | Deferred | M3 | S1, S2, F1 | Enterprise scheduler credibility depends on run control and evidence |
| G1 | Support secret injection via environment or secure config source | Epic G | P1 | Deferred | M3 | C1 | Important for enterprise readiness, but not first delivery blocker |
| V1 | Define enterprise verification evidence model and report categories | Epic V | P1 | Done | M3 | C1, C2 | Shared in-memory evidence model and phase-1 report categories are now defined in the report generator and ADRs |
| V2 | Generate Markdown verification reports from the shared evidence model | Epic V | P1 | Done | M3 | V1 | Categorized Markdown verification reporting now renders from the shared evidence model |
| V3 | Generate HTML verification reports with drill-down enterprise views | Epic V | P1 | Deferred | M3 | V1, V2 | Add richer navigation, drill-down, and audience-friendly release evidence presentation |
| V4 | Define verification-report retention, provenance, and release gating rules | Epic V | P2 | Deferred | M3 | V1, V2 | Make verification evidence auditable and suitable for milestone/release decisions |

### Current working focus

The intended near-term focus order is:

1. `T1` and `B3` — field validation rules, rejected-record output, and processed-file archiving for file scenarios
2. `T2` and `T3` — expression-based mapping and then conditional transformation rules
3. `B1`, `B2`, `C2`, and `D1` — controlled skip/retry behavior plus richer counts, reconciliation, and stable error taxonomy
4. `E2` — packaged-run guidance
5. `V3` and `V4` — enterprise HTML reporting plus retention / release-gating rules

Scheduler/orchestration remains part of this same roadmap as **Epic S**. It should become active only after the product has clearer run-state, audit, and restartability foundations.

Avoid starting new `P1` or `P2` items while `P0` items remain open unless the higher-priority item is genuinely blocked.

---

## Layer 1 — Completed / Established Foundation

These are not “finished forever,” but they represent meaningful product progress already made.

### Foundation backlog already achieved

- [x] Config-driven ETL flow with explicit source, target, and processor YAMLs
- [x] Scenario/job-config-driven business run selection
- [x] Explicit `steps`-driven orchestration for selected business scenarios
- [x] Dynamic reader / processor / writer extension model
- [x] Spring Batch job / step runtime baseline
- [x] Generated model resolution and metadata-driven runtime behavior
- [x] Daily scenario-aware log file strategy with run correlation IDs
- [x] Machine-readable lifecycle logging for run planning, run summary, and step events
- [x] Initial relational ETL architecture path
- [x] Fail-fast validation for placeholder relational connection values in selected scenarios
- [x] Architecture docs and ADR workflow in-repo
- [x] Automated test coverage for key configuration and runtime slices
- [x] Improved path portability across tests and demo defaults
- [x] Local verification reporting with smoke checks, categorized Markdown output, and retained report history

---

## Layer 2 — Near-Term Backlog

These items move the product from “strong ETL foundation” to “reliable product for real operational use.”

## Epic A — Runtime correctness and orchestration clarity

### Goal
Make each run explicit, predictable, and less fragile.

### Backlog
- [x] Replace positional source-target pairing with explicit step pairing or step definitions
- [x] Validate scenario completeness before job start
- [ ] Add stronger config validation error messages for operators
- [ ] Make step definitions more business-meaningful and less index-driven
- [ ] Document supported orchestration patterns and limitations

### Done criteria
- no ambiguous source-target pairing remains
- config errors fail fast with operator-friendly messages
- step orchestration is documented and test-covered

---

## Epic B — Fault tolerance and data quality behavior

### Goal
Handle bad data and transient failures in a controlled way.

### Backlog
- [ ] Introduce configurable skip policy support
- [ ] Introduce configurable retry policy support where appropriate
- [ ] Add validation/rejection handling strategy for file-based ingestion
- [ ] Add bad-record reporting or quarantine output option
- [ ] Add processed-source-file archiving after successful runs
- [ ] Define fail-fast vs tolerate-and-report rules per scenario type

### Done criteria
- operators can tell how invalid rows are handled
- source-file lifecycle behavior is explicit for processed files
- failure mode is explicit and testable
- at least one scenario demonstrates controlled rejection behavior
- at least one preserved realistic file scenario demonstrates accepted records, rejected records, and archived-original-file behavior together

---

## Epic T — Transformation capability maturity

### Goal
Grow the product from structural field mapping into richer transformation behavior comparable to traditional ETL expectations, but in phased and controlled steps.

### Backlog
- [ ] Add field-level validation rule support such as `notNull` and time-format checks
- [ ] Add expression-based derived field support
- [ ] Add conditional transformation rule support
- [ ] Add validation-aware transformation behavior
- [ ] Add reject/quarantine handling for invalid records
- [ ] Define lookup/enrichment processor baseline
- [ ] Document transformation maturity levels and non-goals

### Done criteria
- transformation support goes beyond direct `from` → `to` mapping
- first validation rules are explicit, configurable, and testable in preserved file scenarios
- derived fields and conditions are explicit and testable
- validation/reject behavior is operator-visible
- at least one preserved realistic file scenario proves the first validation slice before broader expression work expands
- transformation evolution is documented as part of product direction

---

## Epic C — Run summary, audit, and reconciliation

### Goal
Make each ETL run auditable beyond raw logs.

### Backlog
- [x] Emit a run summary with start/end time, scenario, status, and duration
- [ ] Capture source count, written count, and rejected count
- [ ] Define a reconciliation model for input vs output records
- [ ] Persist or export run summary metadata
- [ ] Document operational evidence expectations

### Done criteria
- every run has a machine-readable summary
- operators can answer “what happened?” without reading the full log
- reconciliation expectations are documented

---

## Epic D — Observability and operator usability

### Goal
Make operations support practical for production-like usage.

### Backlog
- [ ] Add structured operational event output or summary logs
- [ ] Add stable error taxonomy / error categories
- [ ] Add job history retention design and first implementation slice
- [ ] Add operator-friendly log search guidance and examples
- [ ] Add health/readiness guidance for runtime operation

### Done criteria
- operational investigation does not depend only on stack traces
- job history and log evidence can be correlated by run ID and scenario
- common failure classes are documented and searchable

---

## Epic E — Portability and packaging

### Goal
Make local, CI, and deployment usage more consistent across environments.

### Backlog
- [x] Finalize cross-platform defaults and path handling rules
- [ ] Add packaged-run guidance for jar execution with scenario configs
- [ ] Separate repo-demo mode from external-runtime mode more cleanly
- [ ] Document expected directory conventions for local and deployed runs
- [ ] Add smoke checks for packaged runtime paths where practical

### Done criteria
- running from IDE, Maven, and packaged jar has clear documented expectations
- demo mode does not depend on Windows-only assumptions
- scenario execution instructions are portable

---

## Epic S — Scheduling and orchestration capability

### Goal
Add controlled job-trigger capability as part of the ETL product itself, while keeping scheduler evolution inside the main roadmap instead of creating a separate scheduler roadmap or pseudo-product too early.

### Backlog
- [ ] Define schedule model and trigger contract for scenario-based execution
- [ ] Add time-based schedule definitions with timezone awareness where needed
- [ ] Add pause/resume and disable controls per schedule
- [ ] Define overlap policy for already-running jobs (skip, defer, reject, or queue)
- [ ] Define missed-run handling policy after downtime or blackout periods
- [ ] Add basic trigger audit trail and schedule-to-run traceability
- [ ] Document the boundary between scheduling, orchestration, retry, and restartability

### Done criteria
- schedules are explicit, testable, and tied to scenario/job execution contracts
- operators can tell why a scheduled run started, skipped, or was blocked
- pause/resume and overlap behavior are documented and observable
- scheduler work remains aligned to the main ETL product roadmap rather than drifting into a separate platform track prematurely

---

## Layer 3 — Enterprise Backlog

These items move the product from “reliable ETL engine” to “enterprise-grade ETL product.”

## Epic F — Restartability and idempotent re-run model

### Goal
Ensure production-safe reruns and recovery.

### Backlog
- [ ] Define restart semantics per execution mode
- [ ] Define idempotent load patterns by target type
- [ ] Support safe rerun strategies for failed or partial jobs
- [ ] Document duplicate-handling strategy
- [ ] Test restart and rerun behavior explicitly

### Done criteria
- rerun behavior is predictable and documented
- target duplication risk is controlled
- restartability is not left to operator guesswork

---

## Epic G — Security and secret handling

### Goal
Make runtime configuration safe for enterprise environments.

### Backlog
- [ ] Remove any expectation of secrets in committed YAML
- [ ] Support secret injection via environment or secure config source
- [ ] Review logs for secret leakage risk
- [ ] Add redaction rules for sensitive values
- [ ] Document secure deployment expectations

### Done criteria
- credentials are never expected in version-controlled config
- logs are safe by default for operational sharing
- secure runtime configuration is documented

---

## Epic H — Governance and compliance readiness

### Goal
Support enterprise audit, control, and change visibility.

### Backlog
- [ ] Add config version traceability per run
- [ ] Define lineage expectations from source to target
- [ ] Add change audit for important scenario and connector behavior
- [ ] Define retention rules for logs and run history
- [ ] Document governance boundaries and responsibilities

### Done criteria
- runs can be tied back to config state and business scenario
- retained evidence supports audit review
- governance expectations are explicit

---

## Epic I — Scale and performance maturity

### Goal
Make large-volume flows predictable and tunable.

### Backlog
- [ ] Benchmark chunk vs tasklet decision behavior under realistic volumes
- [ ] Review memory usage of tasklet buffering paths
- [ ] Add performance guidance by connector type
- [ ] Expand relational tuning model (`fetchSize`, `batchSize`, count strategies)
- [ ] Define scale test scenarios and thresholds

### Done criteria
- large-volume behavior is measured, not assumed
- memory and throughput risks are documented
- connector tuning guidance exists for operators and implementers

---

## Epic J — Operator product experience

### Goal
Make the product usable by operators, not just developers.

### Backlog
- [ ] Add operator-facing runbook documentation
- [ ] Add scenario onboarding checklist
- [ ] Add failure investigation checklist
- [ ] Add example deployment configurations
- [ ] Add release-readiness checklist per milestone

### Done criteria
- a new operator can run and diagnose a scenario without deep code knowledge
- operations guidance exists inside the repo

---

## Epic V — Verification reporting and release evidence

### Goal
Turn local test output and scenario evidence into a structured verification-reporting capability that supports enterprise-grade change validation, regression visibility, and release-readiness decisions.

### Backlog
- [x] Define a shared verification evidence model that separates evidence capture from report rendering
- [x] Classify verification output into explicit categories such as change-focused testing, regression suite, runtime/smoke verification, configuration validity, and release readiness
- [x] Generate a canonical Markdown verification report for repository review, history retention, and pull-request evidence
- [ ] Generate an HTML verification report from the same evidence model for drill-down, navigation, and enterprise-friendly sharing
- [ ] Add provenance fields such as branch, commit/config identity, scenario selection, environment metadata, and generated artifact references
- [ ] Define verification report retention, versioning, and release-gating expectations
- [ ] Document which report sections are required before a milestone or release can be considered ready

### Done criteria
- verification evidence is organized by category instead of being one undifferentiated test dump
- one shared evidence model drives both Markdown and HTML outputs
- release stakeholders can distinguish change-focused proof from broader regression proof
- verification reports retain enough provenance to support audit and release review
- report generation and retention rules are documented as a product capability, not only as a local script detail

---

## Milestone View

## Milestone M1 — Reliable ETL Core

Focus:
- orchestration clarity
- fault tolerance basics
- run summaries
- portability cleanup

Current state:
- substantially achieved by the 1.3.0 release through explicit `steps` orchestration, strict startup validation, machine-readable lifecycle logging, relational placeholder fail-fast validation, and local verification reporting
- remaining M1-adjacent work is now mostly hardening work such as packaged-run guidance, richer count/reconciliation evidence, file-based validation/reject handling, processed-file archiving, and fault-tolerance behavior

Exit signal:
- product is credible for repeated controlled ETL runs across supported file scenarios

## Milestone M2 — Operable Product

Focus:
- structured run history
- reconciliation
- stronger diagnostics
- restart/rerun semantics
- first scheduler/orchestration controls built on top of explicit run-state and audit foundations

Exit signal:
- operations support can answer what failed, why, and what to do next

## Milestone M3 — Enterprise Readiness Baseline

Focus:
- secret handling
- governance
- idempotency
- scale validation
- enterprise verification reporting and release evidence
- operational controls
- advanced schedule control, missed-run policy, and trigger evidence

Current state:
- verification reporting direction is now established through `Epic V`, ADR-0005, categorized Markdown reporting, and a shared verification evidence model
- HTML drill-down reporting, provenance hardening, retention rules, and release gating remain future M3 work

Exit signal:
- product can be presented as enterprise-grade ETL foundation with known limits, not just a development framework

---

## Current Top Priorities

If the team has to choose only a few next steps, prioritize these in order:

1. `T1` / `B3` — field validation rules, rejected-record output, and processed-file archiving
2. `T2` / `T3` — expression-based mapping and conditional transformation capability
3. `B1` / `B2` / `C2` / `D1` — fault tolerance, richer count evidence, reconciliation output, and stable error taxonomy
4. `E2` — packaged-run guidance for jar execution with scenario bundles
5. `F1` / `S1` / `S2` — restartability plus scheduler trigger model and first operator controls
6. `V3` / `V4` / `G1` — enterprise HTML verification reporting, release-gating rules, and secure configuration maturity

---

## What “Enterprise Grade” Means Here

For this product, “enterprise grade” should mean:

- runs are explicit and reproducible
- failures are diagnosable and recoverable
- transformation capability extends beyond direct field mapping into rules, validation, and enrichment where justified
- operational evidence is retained and searchable
- secrets are handled safely
- large-volume behavior is understood
- config is governable
- operators can support the system without reading source code first

It should **not** mean adding every possible connector or building a full middleware platform immediately.

---

## Maintenance Rules

Update this backlog when:

- a major capability is completed
- priorities change
- a new risk changes the order of work
- the product phase changes materially

Keep the backlog honest:

- move items to done only when they are genuinely operational
- prefer fewer clear backlog items over a huge wish list
- link major backlog progress to architecture docs, ADRs, and changelog updates

### Execution board working rules

- keep `In Progress` items intentionally limited
- update status in the same PR where the underlying work changes materially
- when an item becomes `Blocked`, add the blocking reason in the notes column
- when an item becomes `Done`, ensure tests/docs/changelog reflect that completion level
- if priorities change, update both the execution board and `Current Top Priorities`

---

## Recommended Usage Pattern

For each meaningful feature or milestone change:

1. update this backlog
2. update the relevant architecture note if design changed
3. update the ADR if a decision changed
4. update tests and changelog with the same PR

This keeps the long-term goal visible while still allowing step-by-step delivery.


