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

The near-term product mission is to become the default internal runtime for repeatable file-based integration scenarios by reducing repetitive custom ETL code and standardizing scenario orchestration, validation, duplicate handling, reject/archive behavior, and common transport-oriented file flow concerns.

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

The most important product opportunity in this stage is not to match every traditional ETL feature immediately. It is to become the default internal runtime for repeatable file-driven business scenarios so teams stop rebuilding similar ingestion, validation, duplicate, reject, archive, and delivery logic in one-off code.

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
| T1 | Add field-level validation rules and first reject-handling slice for file scenarios | Epic T | P1 | Done | M1 | A1 | First shipped CSV-focused slice now supports `notNull`, `timeFormat`, duplicate handling, and controlled rejected-record output |
| T1a | Define processor transform SPI and first cleaner/normalization slice | Epic T | P1 | Done | M2 | T1 | Ordered `transforms[]` now run before validation, with shipped `valueMap` support for normalization, fallbacks, and case-insensitive matching |
| T2 | Add expression-based derived field support | Epic T | P1 | Done | M2 | T1a | Shipped through processor-side `transforms[].type: expression`, including derived fields without a physical `from` property when expression is first |
| T3 | Add conditional transformation rule support | Epic T | P1 | Deferred | M2 | T2 | Best introduced after expression contract is stable |
| T4 | Expand validation and rejected-record/quarantine handling in transformation flow | Epic T | P1 | Deferred | M2 | T1, T2, T3 | Follow-on hardening beyond the shipped CSV slice: broader quarantine, selectable duplicate storage mode, and XML-native duplicate identity |
| T5 | Define lookup/enrichment processor baseline | Epic T | P1 | Deferred | M2 | T2 | Bridges toward more classic ETL transformation patterns |
| B1 | Introduce configurable skip policy support | Epic B | P1 | Deferred | M1 | A1 | Better after orchestration rules are explicit |
| B2 | Introduce configurable retry policy support where appropriate | Epic B | P1 | Deferred | M1 | B1 | Add after failure handling model is defined |
| B3 | Archive processed source files after successful file-based runs | Epic B | P1 | Done | M1 | A1, T1 | First shipped slice now archives CSV source files only after successful processing |
| C1 | Emit machine-readable run summary with scenario, status, and duration | Epic C | P1 | Done | M1 | none | `RUN_EVENT` / `RUN_SUMMARY` and step lifecycle evidence are now emitted for selected runs |
| C2 | Complete run-level source / written / rejected count rollup | Epic C | P1 | In Progress | M1 | C1 | Step-level counts are shipped; run-level rollup and reconciliation remain in progress |
| D1 | Add stable error taxonomy / error categories | Epic D | P1 | Deferred | M2 | C1 | Best done after run-summary model exists |
| E1 | Finalize cross-platform defaults and path handling rules | Epic E | P0 | Done | M1 | none | Portable defaults and test/runtime path cleanup completed |
| E2 | Add packaged-run guidance for jar execution with scenario configs | Epic E | P1 | Ready | M1 | E1 | Important next portability step |
| F1 | Define restart semantics per execution mode | Epic F | P1 | Deferred | M2 | A1, C1 | Needs clearer orchestration and run evidence first |
| X1 | Define SFTP transport contract and deployment boundary | Epic X | P1 | Ready | M2 | E1, C1, G1 | Define staged inbound scope, native-vs-MFT modes, and deployment boundaries before implementation |
| X2 | Add first inbound SFTP staged pull capability | Epic X | P1 | Deferred | M2 | X1, B2, C2 | First slice should stage remote files locally and emit transfer evidence |
| X3 | Add remote post-success file handling and failure categorization for SFTP | Epic X | P1 | Deferred | M2 | X2, D1 | Add remote move/rename/archive semantics only after the first inbound pull slice is stable |
| X4 | Define partner-facing transport security rules and optional isolated worker deployment | Epic X | P1 | Deferred | M3 | X1, G1 | Preserve optional external MFT or isolated transport-worker deployment for stronger partner-facing isolation |
| S1 | Define schedule model and trigger contract for scenario-based execution | Epic S | P1 | Deferred | M2 | A1, C1 | Keep scheduler work inside the main product roadmap; establish scope before implementation |
| S2 | Add time-based schedule definitions with pause/resume controls | Epic S | P1 | Deferred | M2 | S1 | First practical scheduler slice after run-state and audit direction are clearer |
| S3 | Add overlap policy, missed-run handling, and basic trigger audit trail | Epic S | P1 | Deferred | M3 | S1, S2, F1 | Enterprise scheduler credibility depends on run control and evidence |
| G1 | Support secret injection via environment or secure config source | Epic G | P1 | Deferred | M3 | C1 | Important for enterprise readiness, but not first delivery blocker |
| V1 | Define enterprise verification evidence model and report categories | Epic V | P1 | Done | M3 | C1, C2 | Shared evidence model and phase-1 report categories are defined in the report generator and ADRs |
| V2 | Generate Markdown verification reports from the shared evidence model | Epic V | P1 | Done | M3 | V1 | Markdown reporting now renders from the shared evidence model |
| V3 | Generate HTML verification reports with drill-down enterprise views | Epic V | P1 | Deferred | M3 | V1, V2 | Add richer navigation and drill-down from the same evidence model |
| V4 | Define verification-report retention, provenance, and release gating rules | Epic V | P2 | Deferred | M3 | V1, V2 | Make verification evidence auditable and usable for milestone and release decisions |

### Current working focus

Use this section as the near-term sequencing view behind the execution board:

1. `T3` next, now that expression-derived fields are shipped on the processor transform seam.
2. Keep duplicate-handling follow-on work under `T4` scoped to deferred storage-mode and XML-native identity concerns, not a redesign of the shipped duplicate baseline.
3. Move next to `B1` / `B2` / `C2` / `D1` for skip/retry behavior, run-level count rollup, reconciliation, and error taxonomy.
4. Keep `E2` as the next portability/documentation step.
5. Start transport work with `X1`, then `X2` once the contract and boundary are clear.
6. Leave `V3` / `V4` and scheduler/restart work for the next wider operational maturity pass.

### Duplicate-handling checkpoint for next session

Resume from `T4` at the current duplicate-handling stage.

Current shipped duplicate baseline:

- built-in processor rule type: `duplicate`
- activation: optional and only active for mappings that configure a `duplicate` rule
- scope: shared processor-level duplicate handling for flat record-oriented sources through the built-in `duplicate` rule, including single-field or composite-key matching plus ordered winner selection through structured `orderBy`
- runtime behavior: keep-first by default when the mapped field alone or `keyFields` are used without `orderBy`, or retain the best record per duplicate key using configured `orderBy` field/direction entries when winner selection is configured
- storage mode today: keep-first duplicate elimination stays step-local/in-memory, while ordered duplicate winner selection runs behind a shared runtime abstraction that stages in memory or embedded DB based on runtime volume hints; explicit client-selectable storage mode remains future work
- duplicate handling stays in the active processor-rule extension point, not source validation
- current flat-record expectation: the shipped rule works through normal mapped fields after CSV, flat XML, relational, or similar source records are read into runtime objects
- deferred exception scope: XML-native/source-level duplicate identity based on XPath, namespaces, nested collections, or other pre-flattening structure details remains future work

Current code anchors:

- `src/main/java/com/etl/processor/validation/DuplicateProcessorValidationRule.java`
- `src/main/java/com/etl/runtime/InMemoryDuplicateResolver.java`
- `src/main/java/com/etl/runtime/EmbeddedDbDuplicateResolver.java`
- `src/main/java/com/etl/runtime/DuplicateResolverFactory.java`
- `src/main/java/com/etl/runtime/FileIngestionRuntimeSupport.java`
- `src/main/java/com/etl/processor/validation/ValidationRuleEvaluator.java`
- `src/main/java/com/etl/mapping/ValidationAwareDynamicMapping.java`
- `src/main/java/com/etl/config/BatchConfig.java`

Current proof anchors:

- `src/test/java/com/etl/processor/validation/ValidationRuleEvaluatorTest.java`
- `src/test/java/com/etl/mapping/ValidationAwareDynamicMappingTest.java`
- `src/test/java/com/etl/runtime/FileIngestionRuntimeSupportTest.java`
- `src/test/java/com/etl/runtime/InMemoryDuplicateResolverTest.java`
- `src/test/java/com/etl/runtime/EmbeddedDbDuplicateResolverTest.java`
- `src/test/java/com/etl/config/ConfigLoaderJobConfigTest.java`

Architecture anchors:

- `docs/architecture/validation-extension-architecture.md`
- `docs/architecture/file-ingestion-hardening.md`
- `docs/config/processor/default-processor.md`

Latest completed implementation step:

- added composite-key duplicate validation while preserving the future client-selectable `memory` vs `disk` tracking direction
- added generic ordered duplicate winner selection so the retained record per duplicate key can be chosen by configured field order before final write

Still deferred after that:

- actual disk-backed duplicate tracker implementation
- explicit client-selectable duplicate storage mode (`memory` vs `disk`) config surface
- XML-native/source-level duplicate identity when duplicate keys cannot be expressed cleanly through flat mapped fields
- target-aware duplicate detection
- restart/idempotency semantics for duplicate state

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
- [x] First CSV-based file-ingestion hardening slice with field validation rules, rejected-record output, processed-file archiving, and step-level reject/archive evidence
- [x] First processor-side transform/cleaner slice with optional ordered `transforms[]` chains and built-in `valueMap` normalization before validation rules

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
- source-to-target pairing is unambiguous
- config failures are fast, operator-friendly, and test-covered
- supported step orchestration patterns are documented

---

## Epic B — Fault tolerance and data quality behavior

### Goal
Handle bad data and transient failures in a controlled way.

### Backlog
- [ ] Introduce configurable skip policy support
- [ ] Introduce configurable retry policy support where appropriate
- [x] Add validation and rejected-record output strategy for file-based ingestion
- [x] Add bad-record reporting through controlled rejected-record output, with broader quarantine workflows deferred
- [x] Add processed-source-file archiving after successful runs
- [ ] Define fail-fast vs tolerate-and-report rules per scenario type

### Done criteria
- invalid-row handling is explicit to operators
- processed-source lifecycle behavior is explicit and documented
- failure-mode choices are scenario-appropriate and testable
- preserved file scenarios prove accepted, rejected, and archived outcomes together

---

## Epic T — Transformation capability maturity

### Goal
Grow the product from structural field mapping into richer transformation behavior comparable to traditional ETL expectations, but in phased and controlled steps.

### Backlog
- [x] Add field-level validation rule support such as `notNull`, time-format, and first duplicate checks
- [x] Add processor-side field transforms as optional ordered `transforms[]` chains, starting with built-in `valueMap` cleanup before validation
- [x] Add expression-based derived field support
- [ ] Add conditional transformation rule support
- [x] Add validation-aware transformation behavior
- [x] Add controlled rejected-record output for invalid records
- [ ] Define lookup/enrichment processor baseline
- [ ] Document transformation maturity levels and non-goals
- [ ] Add guardrails against ambiguous generic value rewriting across future source and processor layers

### Done criteria
- transformation support extends beyond direct `from` → `to` mapping
- shipped validation and cleaner slices are explicit, configurable, and testable
- derived-field and conditional behavior are defined and testable before broader expansion
- transformation and reject behavior are operator-visible and documented

---

## Epic C — Run summary, audit, and reconciliation

### Goal
Make each ETL run auditable beyond raw logs.

### Backlog
- [x] Emit a run summary with start/end time, scenario, status, and duration
- [ ] Complete run-level source / written / rejected count rollup
- [ ] Define a reconciliation model for input vs output records
- [ ] Persist or export run summary metadata
- [ ] Document operational evidence expectations

### Done criteria
- every run emits a machine-readable summary
- operators can reconstruct run outcomes without reading the full log
- run-level rollup and reconciliation expectations are defined and documented

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
- operational investigation does not rely on stack traces alone
- run evidence and job history are correlatable by run ID and scenario
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
- run expectations are documented for IDE, Maven, and packaged jar modes
- demo and external-runtime behavior are clearly separated
- scenario execution instructions are portable

---

## Epic X — Transport-oriented file acquisition and delivery

### Goal
Add a controlled near-term transport capability for repeated file pickup and delivery scenarios, starting with staged inbound SFTP behavior while keeping partner-facing transport and security concerns separable from the ETL core.

### Backlog
- [ ] Define the SFTP transport contract and deployment boundary
- [ ] Support both external-MFT-managed mode and optional native product SFTP mode
- [ ] Add the first inbound SFTP staged pull slice to land files in a controlled local folder
- [ ] Emit operator-visible transfer evidence with counts, failure categories, and correlation-friendly logs
- [ ] Add remote post-success handling such as move/rename/archive after the first pull slice is stable
- [ ] Document when partner-facing transport should remain isolated behind MFT or a separate worker boundary

### Done criteria
- SFTP is treated as transport and staging, not transformation logic
- the first inbound transfer slice is narrow, explicit, and operationally observable
- native product SFTP remains optional where external MFT is client-mandated
- partner-facing deployment boundaries are documented for production planning

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
- pause/resume, overlap, and missed-run behavior are documented and observable
- scheduler scope stays aligned with the main ETL roadmap

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
- rerun behavior is predictable, documented, and test-covered
- target duplication risk is controlled
- restart expectations are explicit to operators

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
- version-controlled config never carries expected credentials
- logs are safe by default for operational sharing
- secure runtime configuration and redaction expectations are documented

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
- runs can be traced back to config state and business scenario
- retained evidence supports audit review
- governance boundaries and responsibilities are documented

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
- large-volume behavior is measured rather than assumed
- memory and throughput risks are documented
- connector tuning guidance is available to operators and implementers

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
- [ ] Add internal technical reference and developer learning material once runtime behavior and operator guidance are more stable

### Done criteria
- a new operator can run and diagnose a scenario without deep code knowledge
- operator guidance and onboarding material are versioned in-repo
- maintainers and new developers can learn the supported runtime behavior from current reference material

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
- verification evidence is organized by clear report categories
- one shared evidence model drives both Markdown and HTML outputs
- release stakeholders can distinguish change-focused proof from broader regression evidence
- reports retain provenance and documented retention and gating rules

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
- the first M1 hardening follow-up is now also shipped across file-backed scenarios through field validation rules, duplicate handling, rejected-record output, processed-file archiving, and step-level reject/archive evidence, with the strongest preserved proof still centered on CSV
- remaining M1-adjacent work is now mostly packaged-run guidance, run-level count/reconciliation evidence, and broader fault-tolerance behavior

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

## Priority Snapshot

Use this as the condensed near-term priority order:

1. `T3` — conditional transformation rules
2. `B1` / `B2` / `C2` / `D1` — fault tolerance and run-level evidence
3. `E2` — packaged-run guidance
4. `X1` / `X2` — SFTP contract and first inbound slice
5. `F1` / `S1` / `S2` — restartability and scheduler baseline
6. `V3` / `V4` / `G1` — reporting, release gating, and secure config

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
- if priorities change, update both the execution board and `Priority Snapshot`

---

## Recommended Usage Pattern

For each meaningful feature or milestone change:

1. update this backlog
2. update the relevant architecture note if design changed
3. update the ADR if a decision changed
4. update tests and changelog with the same PR

This keeps the long-term goal visible while still allowing step-by-step delivery.


