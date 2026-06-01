# Product Backlog

## Last decision checkpoint

Quick memory-aid summary for the latest transformation decisions: [`transformation-checkpoint.md`](transformation-checkpoint.md)

The canonical source for backlog `Priority`, `Status`, `Milestone`, and `Dependency` remains this document.

Release planning and version mapping guidance lives in [`release-planning-and-delivery-control.md`](release-planning-and-delivery-control.md).

## Executive Dashboard

The GitHub Project **[OneFlow Executive Dashboard](https://github.com/users/kbalanandam/projects/3/views/1)** is the live projected execution view for this backlog.

This document remains the canonical product backlog, milestone view, and execution-board source of truth.

## Purpose

This document preserves the product goal for `spring-etl-engine` from the starting point to the current state, and from the current state toward an enterprise-ready integration foundation.

It is the single product roadmap and execution backlog for the product at this stage. Capabilities such as scheduling/orchestration should be tracked here as dedicated epics, not maintained in separate standalone roadmaps unless they later become truly independent platform products.
It covers both the independently runnable ETL core and the optional control-plane capabilities that may grow around it.

Anything beyond the ETL core should be treated as additive and optional. If a team prefers to integrate `spring-etl-engine` with an external enterprise scheduler, orchestrator, or control framework, that should remain a supported product direction rather than an exception.

OneFlow is positioned as one unified UI and product surface over a capability-first, plug-and-play architecture: ETL remains the always-runnable core, while Scheduler, Hypercare, and future modules are optional capability bundles on the same runtime contract. Any later extraction of a capability into a separate service should happen only when objective triggers (for example scale, isolation, reliability, or ownership boundaries) make that split necessary.

In the current phase, OneFlow is intentionally project-level in operational scope. Organization-level operation and multi-tenant administration are planned future-direction capabilities and are intentionally deferred until later roadmap phases.

It is intentionally different from the architecture roadmap:

- [`ETL product evolution roadmap`](../architecture/foundations/etl-product-evolution-roadmap.md) explains **direction and phases**
- this backlog explains **what to do next, why it matters, and how to know when it is done**

Use this file to keep the team aligned when implementation pressure, feature requests, or uncertainty create drift.

This file now serves two purposes at the same time:

- **narrative backlog** - preserve product intent, capability gaps, and milestone outcomes
- **execution board** - track what should start next, what is in progress now, and what is blocked

---

## Product Goal

Build a focused, config-driven ETL runtime that can reliably:

1. read data from supported sources
2. transform it through explicit mappings and validation rules
3. write it to supported targets
4. be operated, diagnosed, and extended safely
5. evolve into an enterprise-ready ETL and integration foundation with optional control-plane capabilities

The near-term product mission is to become the default internal runtime for repeatable file-based integration scenarios by reducing repetitive custom ETL code and standardizing scenario orchestration, validation, duplicate handling, reject/archive behavior, and common transport-oriented file flow concerns.

That ETL core should remain directly runnable even before later scheduler, watcher, persisted-history, or integrated-UI work is introduced.

Future built-in scheduling therefore must stay optional: the product should work both with a native control plane and with external trigger/orchestration systems that launch the same explicit selected-job runtime contract.

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
- a clear path to layer optional scheduler/control-plane capabilities around that runtime instead of making them prerequisites for normal ETL execution
- optional monitoring-first control-plane API starter (separate process) with first jobs/runs/system endpoints while the selected-job ETL runtime contract stays unchanged

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

> a serious ETL runtime foundation that is beyond prototype stage, but intentionally still focused rather than a full traditional ETL suite.

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
2. **Near-term backlog** - needed to become a reliable ETL product
3. **Enterprise backlog** - needed to become an enterprise-grade product

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

- **P0** - critical now; should be completed before lower-priority work expands
- **P1** - important next; should follow once P0 items are stable
- **P2** - useful later; valuable but not urgent for the current milestone

### Status legend

- **Ready** - approved to start when capacity is available
- **In Progress** - actively being worked
- **Blocked** - cannot move until a dependency or decision is resolved
- **Done** - completed to the expected operational level
- **Deferred** - intentionally postponed to a later milestone

### Board detail pages

The execution board stays intentionally compact. When an item needs fuller context, scope, acceptance criteria, or implementation notes, add a drill-down page under `docs/product/backlog-items/` and link it from the board.

Use this maintenance rule:

- the execution board is the canonical place for changing `Priority`, `Status`, `Milestone`, and `Dependency`
- the backlog item page is the place for fuller detail, scope, acceptance criteria, and working notes

Backlog item index: [`docs/product/backlog-items/README.md`](backlog-items/README.md)

### Backlog navigation shortcuts

Use these links when you want to browse the backlog by capability area instead of scanning the full execution board table:

- Full item browse index: [`docs/product/backlog-items/README.md`](backlog-items/README.md)
- Epic browse index: [`docs/product/epics/README.md`](epics/README.md)
- Near-term runtime and transformation work: [`Epic A`](epics/etl-core/epic-a-runtime-contract-and-model-governance.md), [`Epic B`](epics/etl-core/epic-b-runtime-hardening-and-file-behavior.md), [`Epic T`](epics/etl-core/epic-t-transformation-capability.md), [`Epic P`](epics/etl-core/epic-p-source-native-parser-maturity.md)
- Portability and transport direction: [`Epic E`](epics/etl-core/epic-e-portability-and-packaged-run-guidance.md), [`Epic X`](epics/etl-core/epic-x-file-transport-and-sftp-boundary.md)
- Scheduling and restart direction: [`Epic S`](epics/scheduler/epic-s-scheduling-and-control-plane.md), [`Epic F`](epics/etl-core/epic-f-restartability-and-recovery-semantics.md)
- Evidence, security, and release readiness: [`Epic C`](epics/etl-core/epic-c-observability-and-run-evidence.md), [`Epic D`](epics/etl-core/epic-d-error-taxonomy-and-failure-categorization.md), [`Epic G`](epics/etl-core/epic-g-secret-injection-and-secure-configuration.md), [`Epic V`](epics/etl-core/epic-v-verification-evidence-and-reporting.md)

### Epic detail pages

When several backlog items belong to the same product capability track, keep the shared product intent, boundary, and related architecture links in a dedicated epic page under `docs/product/epics/`.

Use this maintenance rule:

- the execution board remains the canonical place for changing item-level `Priority`, `Status`, `Milestone`, and `Dependency`
- the epic page is the place for the shared problem statement, scope boundary, related backlog items, and links to architecture notes that span more than one backlog item
- the backlog item page is still the place for item-specific scope, acceptance criteria, and working notes

Epic index: [`docs/product/epics/README.md`](epics/README.md)

---

## Current Execution Board

This table is the day-to-day execution view for the current product stage.

> Live execution view: **[OneFlow Executive Dashboard](https://github.com/users/kbalanandam/projects/3/views/1)**
>
> Sync contract: this Markdown table is the canonical source of truth for execution-board status. The GitHub Project is the synchronized live projection maintained by `scripts/sync_project_board.py` and `.github/workflows/product-backlog-project-sync.yml`. See [`project-board-sync.md`](project-board-sync.md) for setup details. When that sync is enabled, update this table first and avoid manually editing the mirrored Project fields except for emergency cleanup.

| ID | Item | Epic | Priority | Status | Milestone | Dependency | Notes |
|---|---|---|---|---|---|---|---|
| [A1](backlog-items/etl-core/A1-explicit-step-pairing-and-step-definitions.md) | Replace positional source-target pairing with explicit step pairing or step definitions | Epic A | P0 | Done | M1 | none | Explicit `steps` orchestration is now the selected-scenario runtime contract |
| [A2](backlog-items/etl-core/A2-validate-scenario-completeness-before-job-start.md) | Validate scenario completeness before job start | Epic A | P0 | Done | M1 | A1 | Startup now fails fast for missing `steps`, missing referenced files, and unknown named step bindings |
| [A3](backlog-items/etl-core/A3-job-level-activation-guardrail.md) | Add job-level activation guardrail so inactive selected jobs fail before wiring | Epic A | P1 | Done | M1 | A2 | Shipped through optional top-level `job-config.yaml -> isActive`, with fail-fast `ConfigLoader` startup errors before referenced config resolution; see [`Job activation and startup guardrails`](../architecture/etl-core/job-level-activation-and-startup-guardrails.md) |
| [A4](backlog-items/etl-core/A4-standardize-generated-model-naming-and-package-derivation.md) | Standardize generated-model naming and package derivation | Epic A | P1 | Done | M2 | A2 | Shipped selected-job contract: package-free source/target YAML, required non-blank job names, centralized package resolution, collision and handoff guardrails, standardized generated headers, and XML `XmlRecord` / `XmlRoot` class-shape separation on the active path |
| [A5](backlog-items/etl-core/A5-relational-source-column-alias-contract.md) | Add relational source column alias contract and reader mapping | Epic A | P2 | Deferred | M2 | none | Parked for later review so relational reads can support source-column-to-property differences without disturbing the current phase-1 baseline |
| [A6](backlog-items/etl-core/A6-retire-internal-generated-model-package-bridge.md) | Retire remaining internal generated-model package bridge | Epic A | P2 | Deferred | M2 | A4 | Parked for later as optional internal cleanup after higher-priority work; do not reopen authored `packageName` support while it is deferred |
| [A7](backlog-items/etl-core/A7-custom-step-pairing-context-handoff-and-failure-contract.md) | Add custom-step pairing, context handoff, and failure-contract baseline | Epic A | P1 | Blocked | M2 | A1, D1 | Blocked until pre-implementation multi-review artifacts (runtime, scheduler/control-plane, UI/operations) are completed; contract remains non-shipped |
| [T1](backlog-items/etl-core/T1-field-level-validation-and-first-reject-handling-slice.md) | Add field-level validation rules and first reject-handling slice for file scenarios | Epic T | P1 | Done | M1 | A1 | First shipped CSV-focused slice now supports `notNull`, `timeFormat`, duplicate handling, and controlled rejected-record output |
| [T1a](backlog-items/etl-core/T1a-processor-transform-spi-and-first-cleaner-normalization-slice.md) | Define processor transform SPI and first cleaner/normalization slice | Epic T | P1 | Done | M2 | T1 | Ordered `transforms[]` now run before validation, with shipped `valueMap` support for normalization, fallbacks, and case-insensitive matching |
| [T2](backlog-items/etl-core/T2-expression-based-derived-field-support.md) | Add expression-based derived field support | Epic T | P1 | Done | M2 | T1a | Shipped through processor-side `transforms[].type: expression`, including derived fields without a physical `from` property when expression is first |
| [T3](backlog-items/etl-core/T3-conditional-transformation-rule-support.md) | Add conditional transformation rule support | Epic T | P1 | Done | M2 | T2 | Shipped on the processor transform seam with ordered `cases[]` first-match behavior and optional `defaultValue` fallback |
| [T4](backlog-items/etl-core/T4-transformation-quarantine-and-duplicate-hardening.md) | Expand validation and rejected-record/quarantine handling in transformation flow | Epic T | P1 | Done | M2 | T1, T2, T3 | Resolver evidence, optional ordered duplicate `storageMode`, and reject `quarantinePath` are shipped; XML-native duplicate identity follow-on is now tracked under `T15` |
| [T5](backlog-items/etl-core/T5-reference-set-validation-and-enrichment-baseline.md) | Define lookup/enrichment processor baseline | Epic T | P1 | Deferred | M2 | T2 | Frozen first-slice direction: processor-side DB-backed reference-set validation such as agency-code allow-lists before broader enrichment joins |
| [T6](backlog-items/etl-core/T6-shared-default-value-and-placeholder-mapping.md) | Add shared default-value and placeholder mapping baseline | Epic T | P1 | Deferred | M2 | T2 | Capture audit-column defaults, provider-backed system date/date-time filling, job-name/constants, and formula-ready placeholders without repeating the same mapping logic in every job bundle |
| [T7](backlog-items/etl-core/T7-duplicate-tracking-scalability-redesign-deferment.md) | Define duplicate-tracking scalability redesign as a separate deferred track | Epic T | P2 | Deferred | M3 | T4 | Keep T4 focused on quarantine/storage-mode boundary hardening while isolating larger duplicate-state scale redesign choices for a later milestone |
| [T8](backlog-items/etl-core/T8-reusable-transform-profiles-and-versioning.md) | Define reusable transform profiles and versioning contract | Epic T | P2 | Deferred | M3 | T3, T6 | #1 in deferred advanced transform sequence. Introduce one reusable profile model so common transform chains can be shared and versioned across many mappings instead of copy/paste YAML |
| [T9](backlog-items/etl-core/T9-source-native-transformation-seam.md) | Define source-native transformation seam before runtime records | Epic T | P2 | Deferred | M3 | T8, P3 | #5 in deferred advanced transform sequence. Add a dedicated source-native adaptation boundary for XPath/namespace/header/token shaping without overloading processor-level business transforms |
| [T10](backlog-items/etl-core/T10-record-level-transformation-stage.md) | Define record-level transformation stage beyond field-centric mapping | Epic T | P2 | Deferred | M3 | T8 | #2 in deferred advanced transform sequence. Introduce a coherent record-level transform stage for multi-field orchestration scenarios that do not fit single-field transform chains |
| [T11](backlog-items/etl-core/T11-cross-record-window-and-aggregation-transforms.md) | Define cross-record window and aggregation transformation semantics | Epic T | P2 | Deferred | M3 | T10, F1 | #7 in deferred advanced transform sequence. Add explicit semantics for stateful group/window/aggregate transformations without breaking deterministic restart and replay behavior |
| [T12](backlog-items/etl-core/T12-transformation-governance-and-lineage.md) | Define transformation governance and lineage evidence model | Epic T | P2 | Deferred | M3 | T8, C1 | #3 in deferred advanced transform sequence. Provide transform-definition version traceability, approval lifecycle, and lineage-friendly evidence for enterprise-grade change control |
| [T13](backlog-items/etl-core/T13-transform-stage-observability-metrics.md) | Define transform-stage observability metrics and operational evidence | Epic T | P2 | Deferred | M3 | T10, V1 | #4 in deferred advanced transform sequence. Emit transform-stage metrics and outcomes independently from validation-rule evidence so operators can diagnose transform behavior directly |
| [T14](backlog-items/etl-core/T14-secure-data-shaping-transforms.md) | Define secure data-shaping transforms for sensitive fields | Epic T | P2 | Deferred | M3 | T8, G1 | #6 in deferred advanced transform sequence. Add governed masking/tokenization/hash transform patterns so sensitive-field handling is explicit, reusable, and auditable |
| [T15](backlog-items/etl-core/T15-xml-native-duplicate-identity-for-nested-xml-sources.md) | Define XML-native duplicate identity for nested XML source scenarios | Epic T | P2 | Done | M3 | T4, P3 | Advanced follow-on for nested XML duplicate correctness is now complete (`S1`-`S6`), including the intentional non-compatible processor-contract cutover |
| [T16](backlog-items/etl-core/T16-customer-owned-processor-transform-extension-seam.md) | Define customer-owned processor transform extension seam | Epic T | P1 | In Progress | M2 | T3, D1 | Planning and contract-shaping started for additive custom transform extensibility on the active `type: default` processor seam; remains non-shipped |
| [B1](backlog-items/etl-core/B1-configurable-skip-policy-support.md) | Introduce configurable skip policy support | Epic B | P1 | Done | M1 | A1 | First runtime slice is complete: step-scoped skip policy with category-first matching, chunk override guardrails, deterministic skip-limit behavior, tests, and preserved scenario proof (`customer-load-skip-policy-category-unclassified`) |
| [B2](backlog-items/etl-core/B2-configurable-retry-policy-support.md) | Introduce configurable retry policy support where appropriate | Epic B | P1 | Done | M1 | B1 | First runtime slice is complete: step-scoped retry remains narrow, chunk-fault-tolerant, evidence-first, and explicitly separated from reject/skip semantics |
| [B3](backlog-items/etl-core/B3-archive-processed-source-files-after-success.md) | Archive processed source files after successful file-based runs | Epic B | P1 | Done | M1 | A1, T1 | First shipped slice now archives CSV source files only after successful processing |
| [B4](backlog-items/etl-core/B4-strict-xml-source-validation-and-optional-xsd.md) | Add strict XML source validation mode with optional XSD checks | Epic B | P2 | Done | M2 | none | Shipped as an opt-in XML source-validation slice through `validation.schemaPath`, preserving lightweight structural XML checks as the default baseline while enabling fail-fast XSD validation and whole-file reject behavior |
| [B5](backlog-items/etl-core/B5-csv-reader-parsing-hardening.md) | Add CSV parsing hardening with configurable quote/escape behavior | Epic B | P2 | Done | M2 | none | Shipped as a narrow reader hardening slice through optional `parser.quoteCharacter`, preserving the current default CSV behavior while supporting alternate quoted-field contracts |
| [B6](backlog-items/etl-core/B6-shared-zip-unzip-service-boundary-for-file-based-source-preparation-and-archive-packaging.md) | Add a shared zip/unzip service boundary for file-based source preparation and archive packaging | Epic B | P1 | Done | M2 | B3, E1 | Shipped as one reusable ZIP boundary for ZIP-backed CSV/XML source preparation plus optional `archive.packageAsZip` packaging, with the implementation promoted to `com.etl.common.util.ZipFileUtility` for reuse by later staged local artifact flows |
| [P1](backlog-items/etl-core/P1-freeze-parser-roadmap-around-csv-and-xml-maturity.md) | Freeze parser roadmap around CSV and XML source-native maturity | Epic P | P1 | Deferred | M2 | none | Create one explicit parser-capability track for CSV/XML-first growth, keep it source-native, and leave JSON source parsing off the active board until a real contract is justified |
| [P2](backlog-items/etl-core/P2-expand-csv-parser-strictness-and-malformed-row-categorization.md) | Expand CSV parser strictness and malformed-row categorization on the read path | Epic P | P1 | Deferred | M2 | B5, P1 | Future CSV parser growth should stay on tokenization, quoting/escaping strictness, and malformed-row categorization before runtime-record creation |
| [P3](backlog-items/etl-core/P3-expand-xml-parser-maturity-for-namespace-and-fragment-contracts.md) | Expand XML parser maturity for namespace-aware and fragment-contract scenarios | Epic P | P1 | Deferred | M2 | B4, P1 | Future XML parser growth should stay on source-native fragment interpretation needed by preserved scenarios, not processor-side business rules |
| [P4](backlog-items/etl-core/P4-prove-csv-and-xml-parser-maturity-through-preserved-scenarios-and-verification.md) | Prove CSV and XML parser maturity through preserved scenarios and verification | Epic P | P1 | Deferred | M2 | P2, P3 | Use preserved scenario bundles and verification evidence as the freeze gate before opening new parser families; JSON source parsing remains intentionally later |
| [P5](backlog-items/etl-core/P5-native-parser-adoptability-and-sidecar-integration-readiness.md) | Define native parser adoptability and CSV-first sidecar integration readiness | Epic P | P2 | Deferred | M3 | P4, E1 | Preserve a future native-parser path only behind the Java reader seam, with CSV-first sidecar integration preferred over rushed JNI-first coupling |
| [C1](backlog-items/etl-core/C1-machine-readable-run-summary.md) | Emit machine-readable run summary with scenario, status, and duration | Epic C | P1 | Done | M1 | none | `RUN_EVENT` / `RUN_SUMMARY` and step lifecycle evidence are now emitted for selected runs |
| [C2](backlog-items/etl-core/C2-run-level-count-rollup-and-reconciliation.md) | Complete run-level source / written / rejected count rollup | Epic C | P1 | Done | M1 | C1 | `RUN_SUMMARY` now emits operator-oriented run-level `sourceCount` / `writtenCount` / `rejectedCount`, with intermediate handoff counts kept separate for multi-step jobs |
| [D1](backlog-items/etl-core/D1-stable-error-taxonomy-and-categories.md) | Add stable error taxonomy / error categories | Epic D | P1 | Done | M2 | C1 | D1 baseline complete: stable token family, runtime category alignment, and source-read vs factory reader boundary are documented and test-covered |
| [E1](backlog-items/etl-core/E1-cross-platform-defaults-and-path-handling.md) | Finalize cross-platform defaults and path handling rules | Epic E | P0 | Done | M1 | none | Portable defaults and test/runtime path cleanup completed |
| [E2](backlog-items/etl-core/E2-packaged-run-guidance-for-jar-execution.md) | Add packaged-run guidance for jar execution with scenario configs | Epic E | P1 | Done | M1 | E1 | Packaged selected-job run guidance is now documented in core docs and preserved scenario READMEs with version-agnostic jar commands |
| [E3](backlog-items/etl-core/E3-centralize-brand-naming-and-doc-refresh.md) | Centralize product-brand naming and doc refresh automation | Epic E | P2 | Deferred | M2 | none | Define one product-facing brand source of truth and a controlled refresh path for brand-facing docs/copy only, while keeping `spring-etl-engine` as the stable technical identity |
| [F1](backlog-items/etl-core/F1-restart-semantics-per-execution-mode.md) | Define restart semantics per execution mode | Epic F | P1 | Deferred | M2 | A1, C1 | Needs clearer orchestration and run evidence first |
| [X1](backlog-items/etl-core/X1-sftp-transport-contract-and-deployment-boundary.md) | Define SFTP transport contract and deployment boundary | Epic X | P1 | Ready | M2 | E1, C1, G1 | Define staged inbound scope, native-vs-MFT modes, and deployment boundaries before implementation |
| [X2](backlog-items/etl-core/X2-first-inbound-sftp-staged-pull-capability.md) | Add first inbound SFTP staged pull capability | Epic X | P1 | Deferred | M2 | X1, B2, C2 | First slice should stage remote files locally and emit transfer evidence |
| [X3](backlog-items/etl-core/X3-remote-post-success-file-handling-and-failure-categorization.md) | Add remote post-success file handling and failure categorization for SFTP | Epic X | P1 | Deferred | M2 | X2, D1 | Add remote move/rename/archive semantics only after the first inbound pull slice is stable |
| [X4](backlog-items/etl-core/X4-partner-facing-transport-security-and-isolated-worker-boundary.md) | Define partner-facing transport security rules and optional isolated worker deployment | Epic X | P1 | Deferred | M3 | X1, G1 | Preserve optional external MFT or isolated transport-worker deployment for stronger partner-facing isolation |
| [S1](backlog-items/scheduler/S1-schedule-model-and-trigger-contract.md) | Define schedule model and trigger contract for scenario-based execution | Epic S | P1 | Ready | M2 | A1, C1 | Define the optional control-plane contract without changing the independently runnable ETL core or blocking external scheduler/orchestrator integration |
| [S2](backlog-items/scheduler/S2-time-based-schedule-definitions-with-pause-resume.md) | Add time-based schedule definitions with pause/resume controls | Epic S | P1 | Deferred | M2 | S1 | First practical scheduler slice after run-state and audit direction are clearer |
| [S3](backlog-items/scheduler/S3-overlap-policy-missed-run-handling-and-trigger-audit-trail.md) | Add overlap policy, missed-run handling, and basic trigger audit trail | Epic S | P1 | Deferred | M3 | S1, S2, F1 | Enterprise scheduler credibility depends on run control and evidence |
| [S4](backlog-items/scheduler/S4-control-plane-operational-data-model.md) | Define control-plane operational data model for schedules, watchers, trigger events, run and step history, artifact lineage, and restartability anchors | Epic S | P1 | Deferred | M3 | S1, C1, C2 | Persist optional scheduler/control-plane history coherently without making it a prerequisite for direct ETL-core execution |
| [U1](backlog-items/operator-ui/U1-independent-operator-ui-shell-and-monitoring-read-model.md) | Stand up independent monitoring-first Operator UI shell with jobs and runs list views | Epic U | P1 | Done | M2 | C1 | Completed monitoring-first shell on `/operator` with read-only Jobs/Runs list views, placeholder deep links (`#/runs/{jobExecutionId}`, `#/jobs/{jobKey}`), client-side filter/sort controls, and hash-route state persistence; remains independent from ETL worker launch path |
| [U2](backlog-items/operator-ui/U2-run-detail-drilldown-with-step-and-artifact-evidence.md) | Add job run detail drill-down with step outcomes, evidence links, and run-scoped log viewer | Epic U | P1 | Done | M2 | U1, C2 | Completed run-detail drill-down with step/failure/artifact evidence, independent/combined runs-list job + start-date filtering, and richer run-instance-scoped in-page log rendering (without widening scheduler/launch boundaries) |
| [U3](backlog-items/operator-ui/U3-guarded-trigger-now-from-job-details.md) | Add guarded trigger-now action from job details without scheduler coupling | Epic U | P1 | Done | M2 | U1, S1 | Completed guarded trigger-now action on job detail with confirmation, traceable `triggerEventId`/decision feedback, categorized failure messages, and explicit selected-job boundary wording (no scheduler-management controls added) |
| [G1](backlog-items/etl-core/G1-secret-injection-via-environment-or-secure-config-source.md) | Support secret injection via environment or secure config source | Epic G | P1 | Deferred | M3 | C1 | Important for enterprise readiness, but not first delivery blocker |
| [V1](backlog-items/etl-core/V1-enterprise-verification-evidence-model-and-report-categories.md) | Define enterprise verification evidence model and report categories | Epic V | P1 | Done | M3 | C1, C2 | Shared evidence model and phase-1 report categories are defined in the report generator and ADRs |
| [V2](backlog-items/etl-core/V2-markdown-verification-reports-from-shared-evidence-model.md) | Generate Markdown verification reports from the shared evidence model | Epic V | P1 | Done | M3 | V1 | Markdown reporting now renders from the shared evidence model |
| [V3](backlog-items/etl-core/V3-html-verification-reports-with-drill-down-enterprise-views.md) | Generate HTML verification reports with drill-down enterprise views | Epic V | P1 | Deferred | M3 | V1, V2 | Add richer navigation and drill-down from the same evidence model |
| [V4](backlog-items/etl-core/V4-verification-report-retention-provenance-and-release-gating.md) | Define verification-report retention, provenance, and release gating rules | Epic V | P2 | Deferred | M3 | V1, V2 | Make verification evidence auditable and usable for milestone and release decisions |

### Current working focus

Use this section as the near-term sequencing view behind the execution board:

1. Keep `A7` near-term so custom-step pairing, context handoff, and failure finalization can be bounded before ad hoc customer hooks spread.
2. Keep `T16` near-term with `A7` so customer-owned processor transforms and customer-owned job steps evolve as one bounded extension model, now building on the shipped D1 taxonomy baseline.
4. Keep duplicate-handling follow-on split explicitly: `T15` is closed and larger duplicate-state scale redesign remains deferred under `T7`.
5. Prioritize deferred advanced transformation items in this dependency-safe order: `T8` -> `T10` -> `T12` -> `T13` -> `T9` -> `T14` -> `T11`.
6. Before expanding parser scope further, prove the current Java runtime on a small set of real-file business scenarios such as `xml-to-csv-events`, `xml-to-json-events`, `csv-to-sqlserver`, and the preserved multi-step XML roundtrip bundles.
7. Keep parser expansion grouped under `Epic P`, but frozen to CSV/XML source-native maturity and preserved-scenario proof rather than reopening parser scope ad hoc.
8. Treat `P5` as future boundary-readiness work only: native-parser adoptability must stay behind the Java reader seam and start, if ever activated, with a narrow CSV-first sidecar shape rather than a parser-centered redesign.
9. Leave JSON source-parser planning out of the active board until the CSV/XML parser baseline proves enough maturity for more demanding real-world scenarios.
10. Start transport work with `X1`, then `X2` once the contract and boundary are clear.
11. Start Operator UI in parallel as a monitoring-first independent slice (`U1` -> `U2` -> `U3`) so users get centralized job/run visibility without coupling UI to ETL-core launch behavior.
12. Start `S1` in parallel with `U1` and keep `S2`/`S3`/`S4` deferred until the schedule/trigger contract freeze checkpoint is documented (single selected-job launch boundary, trigger-origin evidence shape, and retry/restart separation).
13. Leave `V3` / `V4` and wider scheduler/restart work for the next operational maturity pass.

### Duplicate-handling checkpoint for next session

`T15` is closed; resume duplicate follow-on work from the larger scale redesign track under `T7`.

Current shipped duplicate baseline:

- built-in processor rule type: `duplicate`
- activation: optional and only active for mappings that configure a `duplicate` rule
- scope: shared processor-level duplicate handling for flat record-oriented sources through the built-in `duplicate` rule, including single-field or composite-key matching plus ordered winner selection through structured `orderBy`
- runtime behavior: keep-first by default when the mapped field alone or `keyFields` are used without `orderBy`, or retain the best record per duplicate key using configured `orderBy` field/direction entries when winner selection is configured
- storage mode today: keep-first duplicate elimination stays step-local/in-memory, while ordered duplicate winner selection supports `storageMode: auto|memory|embeddedDb`
- duplicate handling stays in the active processor-rule extension point, not source validation
- current flat-record expectation: the shipped rule works through normal mapped fields after CSV, flat XML, relational, or similar source records are read into runtime objects
- XML-native/source-level duplicate identity based on XPath, namespaces, nested collections, or other pre-flattening structure details is shipped and closed under `T15`

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

- [`Validation extension architecture`](../architecture/etl-core/validation-extension-architecture.md)
- [`File ingestion hardening`](../architecture/etl-core/file-ingestion-hardening.md)
- [`Default processor reference`](../config/processor/default-processor.md)

Latest completed implementation step:

- added ordered-duplicate resolver evidence (`resolverMode`, `resolverReason`) and optional `storageMode: auto|memory|embeddedDb` for `duplicate + orderBy`
- added additive reject-quarantine publication through `rejectHandling.quarantinePath` and preserved-scenario proof, then closed `T4`

Still deferred after that:

- larger duplicate-tracking scalability redesign (separate deferred track: `T7`)
- target-aware duplicate detection
- restart/idempotency semantics for duplicate state

Scheduler/orchestration remains part of this same roadmap as **Epic S**. It should become active only after the product has clearer run-state, audit, and restartability foundations.

Even when that work becomes active, it should stay layered around the ETL core rather than inside its required runtime boundary. Native scheduling is a future optional product capability, not a requirement for clients that already standardize on external orchestration.

Avoid starting new `P1` or `P2` items while `P0` items remain open unless the higher-priority item is genuinely blocked.

---

## Layer 1 - Completed / Established Foundation

These are not "finished forever," but they represent meaningful product progress already made.

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

## Layer 2 - Near-Term Backlog

These items move the product from "strong ETL foundation" to "reliable product for real operational use."

## Epic A - Runtime correctness and orchestration clarity

### Goal
Make each run explicit, predictable, and less fragile.

### Backlog
- [x] Replace positional source-target pairing with explicit step pairing or step definitions
- [x] Validate scenario completeness before job start
- [x] Add job-level activation guardrail so `isActive: false` blocks the selected job before wiring
- [x] Complete the generated-model naming and package-derivation standard for the shipped active selected-job contract
- [ ] Retire the remaining internal generated-model package bridge after the shipped contract is complete
- [ ] Add additive custom-step pairing and context handoff contract for bounded customer-owned pre/post steps without changing explicit ordered runtime semantics
- [ ] Add a relational source column alias contract so selected database column names can differ from generated/runtime property names without ad hoc query-only workarounds
- [ ] Add stronger config validation error messages for operators
- [ ] Make step definitions more business-meaningful and less index-driven
- [ ] Document supported orchestration patterns and limitations

### Done criteria
- source-to-target pairing is unambiguous
- config failures are fast, operator-friendly, and test-covered
- inactive selected jobs fail early and never reach `BatchConfig` step assembly
- generated-model package/class identity is deterministic from the selected job and logical config names
- supported step orchestration patterns are documented
- custom and standard step pairing semantics are explicit, fail-fast, and observable

---

## Epic B - Fault tolerance and data quality behavior

### Goal
Handle bad data and transient failures in a controlled way.

### Backlog
- [ ] Introduce configurable skip policy support
- [x] Introduce configurable retry policy support where appropriate
- [x] Add validation and rejected-record output strategy for file-based ingestion
- [x] Add bad-record reporting through controlled rejected-record output, with broader quarantine workflows deferred
- [x] Add processed-source-file archiving after successful runs
- [x] Add an optional strict XML source-validation mode with XSD/schema checks for scenarios that need stronger source contracts
- [x] Add CSV parsing hardening for quoted/escaped field handling while preserving today's simple default reader contract
- [x] Complete the broader shared zip/unzip service boundary so ZIP-backed file-source preparation and optional zip-on-archive packaging both run through the same reusable utility
- [ ] Define fail-fast vs tolerate-and-report rules per scenario type

### Done criteria
- invalid-row handling is explicit to operators
- processed-source lifecycle behavior is explicit and documented
- shared zip/unzip service behavior is explicit, scoped, and documented, with supported file-based scenarios proving the first shipped slice
- failure-mode choices are scenario-appropriate and testable
- preserved file scenarios prove accepted, rejected, and archived outcomes together

---

## Epic P - Source-native parser maturity

### Goal
Make parser growth explicit, source-native, and disciplined so OneFlow first proves a few real business scenarios on the existing Java runtime and real files, then matures CSV and XML parsing further without turning parsing into a second ETL core.

### Backlog
- [ ] Prove the existing Java runtime on a small set of real-file scenario bundles such as XML -> CSV, XML -> JSON, CSV -> relational target, and preserved multi-step handoff flows
- [ ] Freeze the parser roadmap around CSV/XML-first source-native maturity and explicit non-goals
- [ ] Expand CSV parser strictness only where tokenization, quoting/escaping, or malformed-row categorization need stronger real-world behavior
- [ ] Expand XML parser maturity only where preserved scenarios need namespace-aware or stricter fragment interpretation before normal runtime records exist
- [ ] Prove parser maturity through preserved CSV/XML scenario bundles and verification evidence before opening new parser-family scope
- [ ] Define native-parser adoptability and a CSV-first sidecar integration contract without committing to a shipped native parser path too early
- [ ] Keep JSON source parsing explicitly out of the active parser backlog until a concrete source contract and preserved scenario require it

### Done criteria
- parser work is tracked in one explicit product epic instead of being scattered through unrelated hardening items
- the current Java runtime is proven on a few preserved real-file business scenarios before broader parser-scope pressure is reopened
- CSV/XML parser growth stays clearly limited to source-native parsing and source validation concerns
- preserved scenario bundles and verification evidence show parser maturity on realistic CSV/XML inputs
- any future native parser direction remains behind the Java reader seam and prefers sidecar-style integration over deep runtime coupling by default
- JSON source parsing remains a future candidate rather than an implied current-phase commitment

---

## Epic T - Transformation capability maturity

### Goal
Grow the product from structural field mapping into richer transformation behavior comparable to traditional ETL expectations, but in phased and controlled steps.

### Backlog
- [x] Add field-level validation rule support such as `notNull`, time-format, and first duplicate checks
- [x] Add processor-side field transforms as optional ordered `transforms[]` chains, starting with built-in `valueMap` cleanup before validation
- [x] Add expression-based derived field support
- [x] Add conditional transformation rule support
- [x] Add validation-aware transformation behavior
- [x] Add controlled rejected-record output for invalid records
- [ ] Define customer-owned processor transform extension seam so project-specific field transformations can scale without core forking
- [ ] Define lookup/enrichment processor baseline; frozen first slice is runtime-loaded reference-set validation for reject/accept checks before broader enrichment work
- [ ] Add shared default-value mapping for audit columns, constants, and future formula-ready placeholders
- [ ] Document transformation maturity levels and non-goals
- [ ] Add guardrails against ambiguous generic value rewriting across future source and processor layers

### Done criteria
- transformation support extends beyond direct `from` -> `to` mapping
- shipped validation and cleaner slices are explicit, configurable, and testable
- derived-field and conditional behavior are defined and testable before broader expansion
- transformation and reject behavior are operator-visible and documented

---

## Epic C - Run summary, audit, and reconciliation

### Goal
Make each ETL run auditable beyond raw logs.

### Backlog
- [x] Emit a run summary with start/end time, scenario, status, and duration
- [x] Complete run-level source / written / rejected count rollup
- [ ] Define a reconciliation model for input vs output records
- [ ] Persist or export run summary metadata
- [ ] Document operational evidence expectations

### Done criteria
- every run emits a machine-readable summary
- operators can reconstruct run outcomes without reading the full log
- run-level rollup and reconciliation expectations are defined and documented

---

## Epic D - Observability and operator usability

### Goal
Make operations support practical for production-like usage.

### Backlog
- [ ] Add structured operational event output or summary logs
- [x] Add stable error taxonomy / error categories
- [ ] Add job history retention design and first implementation slice
- [ ] Add operator-friendly log search guidance and examples
- [ ] Add health/readiness guidance for runtime operation

### Done criteria
- operational investigation does not rely on stack traces alone
- run evidence and job history are correlatable by run ID and scenario
- common failure classes are documented and searchable

---

## Epic E - Portability and packaging

### Goal
Make local, CI, and deployment usage more consistent across environments.

### Backlog
- [x] Finalize cross-platform defaults and path handling rules
- [x] Add packaged-run guidance for jar execution with scenario configs
- [ ] Centralize product-brand naming and define a controlled doc refresh path for rebrandable product-facing copy while preserving `spring-etl-engine` as the technical identity
- [ ] Separate repo-demo mode from external-runtime mode more cleanly
- [ ] Document expected directory conventions for local and deployed runs
- [ ] Add smoke checks for packaged runtime paths where practical

### Done criteria
- run expectations are documented for IDE, Maven, and packaged jar modes
- demo and external-runtime behavior are clearly separated
- rebrandable product-facing names are centralized and refreshable without changing technical identifiers accidentally
- scenario execution instructions are portable

---

## Epic X - Transport-oriented file acquisition and delivery

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

## Epic S - Scheduling and control-plane capability

### Goal
Add controlled trigger and operator-control capability around the ETL core while keeping scheduler evolution inside the main roadmap instead of creating a separate scheduler roadmap or pseudo-product too early.

That capability must remain optional from the ETL core point of view and must not make OneFlow-native scheduling mandatory for adopters that already have an external scheduler or orchestrator.

### Backlog
- [ ] Define schedule model and trigger contract for scenario-based execution
- [ ] Add time-based schedule definitions with timezone awareness where needed
- [ ] Add file-watcher trigger management under the same trigger-control contract
- [ ] Add pause/resume and disable controls per schedule
- [ ] Define overlap policy for already-running jobs (skip, defer, reject, or queue)
- [ ] Define missed-run handling policy after downtime or blackout periods
- [ ] Add basic trigger audit trail and schedule-to-run traceability
- [ ] Define the retained control-plane operational data model for schedules, watchers, trigger events, run / step history, artifact lineage, and restartability anchors
- [ ] Persist control-plane history in a local-first relational store for developer/laptop use, with stronger relational deployment targets later
- [ ] Document the boundary between scheduling, orchestration, retry, and restartability
- [ ] Document the external-scheduler interoperability contract so third-party schedulers/orchestrators can launch the same selected-job runtime without feature loss in the core path

### Done criteria
- schedules are explicit, testable, and tied to scenario/job execution contracts
- operators can tell why a scheduled run started, skipped, or was blocked
- pause/resume, overlap, and missed-run behavior are documented and observable
- retained scheduler/control-plane history is defined clearly enough to support later UI, audit, and recovery work without becoming a mandatory ETL-core dependency
- scheduler/control-plane scope stays aligned with the main ETL roadmap and does not replace direct ETL-core execution
- external schedulers/orchestrators remain first-class launch options through the same selected-job contract

---

## Layer 3 - Enterprise Backlog

These items move the product from "reliable ETL engine" to "enterprise-grade ETL product."

## Epic F - Restartability and idempotent re-run model

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

## Epic G - Security and secret handling

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

## Epic H - Governance and compliance readiness

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

## Epic I - Scale and performance maturity

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

## Epic J - Operator product experience

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

## Epic V - Verification reporting and release evidence

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

## Milestone M1 - Reliable ETL Core

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

## Milestone M2 - Operable Product

Focus:
- structured run history
- reconciliation
- stronger diagnostics
- restart/rerun semantics
- first optional scheduler/control-plane controls built on top of explicit run-state and audit foundations

Current state:
- a monitoring-first control-plane API starter is now available as an optional separate launcher for jobs/runs/system projections while preserving the independent selected-job ETL worker contract
- schedule/trigger semantics and retained control-plane operational history remain deferred under `Epic S`

Exit signal:
- operations support can answer what failed, why, and what to do next

## Milestone M3 - Enterprise Readiness Baseline

Focus:
- secret handling
- governance
- idempotency
- scale validation
- enterprise verification reporting and release evidence
- operational controls
- advanced schedule control, watcher governance, missed-run policy, persisted operational evidence, and trigger history

Current state:
- verification reporting direction is now established through `Epic V`, ADR-0005, categorized Markdown reporting, and a shared verification evidence model
- HTML drill-down reporting, provenance hardening, retention rules, and release gating remain future M3 work

Exit signal:
- product can be presented as enterprise-grade ETL foundation with known limits, not just a development framework

---

## Priority Snapshot

Use this as the condensed near-term priority order:

1. `A7` / `T16` - bounded customer extensibility through job-level custom steps plus processor-level custom transforms, now anchored to the shipped D1 taxonomy baseline and shipped `B1`/`B2` fault-tolerance baselines
2. duplicate follow-on - `T7` (larger duplicate-scale redesign)
3. deferred `Epic T` advanced sequence - `T8` -> `T10` -> `T12` -> `T13` -> `T9` -> `T14` -> `T11`
4. `Epic P` - first prove the existing Java runtime on a few real-file business scenarios, then keep parser maturity planning frozen around CSV/XML source-native growth and preserved proof, with JSON source parsing still later and any future native-parser direction constrained to Java-reader-boundary / sidecar-first readiness
5. `X1` / `X2` - SFTP contract and first inbound slice
6. `F1` / `S1` / `S2` - restartability and scheduler baseline
7. `V3` / `V4` / `G1` - reporting, release gating, and secure config

---

## What "Enterprise Grade" Means Here

For this product, "enterprise grade" should mean:

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

- move items to `Done` only when they are genuinely operational
- prefer fewer clear backlog items over a huge wish list
- link major backlog progress to architecture docs, ADRs, and changelog updates

### Execution board working rules

- keep `In Progress` items intentionally limited
- update status in the same PR where the underlying work changes materially
- when an item becomes `Blocked`, add the blocking reason in the notes column
- when an item becomes `Done`, ensure tests/docs/changelog reflect that completion level
- if priorities change, update both the execution board and `Priority Snapshot`
- when Project sync is enabled, treat the GitHub Project as a projection of this table and make execution-status changes here rather than editing mirrored Project fields manually

---

## Recommended Usage Pattern

For each meaningful feature or milestone change:

1. update this backlog
2. update the relevant architecture note if design changed
3. update the ADR if a decision changed
4. update tests and changelog with the same PR

This keeps the long-term goal visible while still allowing step-by-step delivery.



