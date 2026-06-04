# Changelog
All notable changes to this project will be documented in this file.

The format is based on **Keep a Changelog**  
and this project adheres to **Semantic Versioning**.

## [Unreleased]

### Added
- N/A

### Changed
- Started the scheduler `S3` phase-1 governance baseline by introducing explicit `controlplane.scheduler.overlap-policy` (`ALLOW` default, optional `SERIALIZE`) while preserving existing missed-run defaults (`SKIP`) and documenting the current due-instant governance boundary in scheduler architecture/backlog notes.
- Extended control-plane scheduler visibility by exposing scheduler governance metadata (`schedulerEnabled`, `schedulerMissedRunPolicy`, `schedulerOverlapPolicy`) on `GET /api/v1/system/info`, exposing schedule watermark state (`lastAcceptedDueAt`) on schedule responses, and making `controlplane.scheduler.overlap-policy=ALLOW` explicit in control-plane profile defaults.
- Started `F1` phase-1 contract baseline by documenting explicit restart semantics for shipped execution modes (`runMode` + `recoveryPolicy`) and surfacing `runMode` in `RUN_SUMMARY` run-level evidence while keeping resume/checkpoint behavior deferred.
- Continued `F1` phase-1 baseline by projecting `runMode` and `recoveryPolicy` through run read models and control-plane APIs (`/api/v1/runs*` and job-detail recent runs), with JDBC projection persistence/backfill support for the additive fields.
- Added `F1` phase-1.3 runtime guardrail so `JobRuntimeDescriptor` fails fast when `recoveryPolicy=resume-from-checkpoint` is selected, preserving the shipped `rerun-from-start` execution behavior.

### Fixed
- N/A

### Security
- N/A

## [1.8.0] - 2026-06-03

### Added
- Added the first scheduler/control-plane launch-contract baseline so schedule-triggered runs follow the same selected-job boundary as direct ETL execution.
- Added the first time-based scheduling slice, including pause/resume controls on the same selected-job launch boundary.
- Added retained-history schema foundations with relational surrogate keys for schedule, trigger, and run records (`schedule_pk`, `trigger_event_pk`, `run_record_pk`) while keeping stable external business IDs.
- Added durable retained step/artifact history persistence (`step_record`, `artifact_record`) and persisted run subresource APIs for step/artifact lookup.
- Added first attempt-lineage/checkpoint-anchor persistence baseline through best-effort optional writes to `controlplane_attempt_link` and `controlplane_checkpoint_anchor`.

### Changed
- Updated scheduler persistence migration behavior to preserve legacy external identifiers while moving internal joins to PK-backed linkage, including startup compatibility backfills.
- Updated schedule-origin trigger matching to normalize `schedule_id` inputs (case/whitespace tolerant) so schedule lookups stay consistent during phased migration.
- Updated retained run-history projection behavior to populate/backfill `selected_job_key`, prefer deterministic PK-based trigger/run correlation, and include transitional lookup indexing for mixed-phase data.
- Updated local control-plane storage to use `.etl-dev/etl-dev.db`, colocating Spring Batch metadata and retained-history tables in one developer SQLite file.
- Updated control-plane surrogate/linkage column typing to `bigint` for new-schema consistency.
- Updated run-detail read-model projection to prefer persisted step/artifact records, with compatibility fallback to prior detail projections when needed.

### Fixed
- N/A

### Security
- N/A

## [1.7.10] - 2026-05-31

### Changed
- Added a monitoring-first Operator UI shell under `/operator` in the optional control-plane launcher, backed by existing Jobs/Runs read-model APIs and explicit empty/error states.
- Added U1 deep-link placeholders for run/job drill-down routes (`#/runs/{jobExecutionId}`, `#/jobs/{jobKey}`), with server-side redirects for `/operator/runs/{jobExecutionId}` and `/operator/jobs/{jobKey}` to keep route contracts stable for U2/U3.
- Added lightweight client-side Jobs/Runs filtering and sorting with hash-route query persistence so operator list context survives refresh/back navigation without changing backend API contracts.
- Completed U3 guarded trigger-now delivery in Operator UI job details: confirmation-gated `POST /api/v1/jobs/{jobKey}:trigger-now`, traceable decision/`triggerEventId` success feedback, categorized failure feedback (`validation`/`config`/`runtime`), and explicit wording that this remains ad hoc convenience rather than scheduler-management scope.
- Added control-plane operator log-file serving at `/operator/logs/**` (backed by `etl.logging.base-dir`) so run-detail evidence links can render scenario logs in-browser, including path traversal guardrails.
- Extended U2 run-detail diagnosis with run-scoped log viewing: added `GET /api/v1/runs/{jobExecutionId}/log` for selected-instance log extraction and upgraded the Operator UI run-detail screen to render structured in-page log lines (level/record metadata + bounded list) instead of relying only on raw full-file links.
- Polished the U2 run-scoped log viewer with client-side search/filter controls (`message`, `event`, `recordType`, `level`) and compact/expanded line rendering for faster in-page triage.
- Extended the Runs list UX with selected-job scoping so operators can choose one job, view recent runs for that job, and then drill into one run instance for detail diagnosis.
- Extended the Runs list UX and `/api/v1/runs` query contract with independent/combined `job` + `startDate` filters plus optional `timezone`; Operator UI now defaults start-date and timezone from the browser and keeps both in route hash state for shareable diagnosis links.
- Shifted the shipped `controlplane` profile to SQLite-first local JDBC persistence under `.controlplane/controlplane.db`, disabled unused Spring Batch auto-configuration for the control-plane process, and aligned the control-plane JDBC tests with the SQLite-first local persistence contract.
- Hardened control-plane profile/test startup boundaries so SQLite-backed control-plane runs no longer inherit `dev` SQL-init behavior (`schema-h2.sql`) through forced profile activation; the base profile now defaults to `dev` only when no profile is selected, while control-plane profile and integration tests explicitly disable Spring SQL and Spring Batch schema initialization.

## [1.7.9] - 2026-05-29

### Changed
- Pinned embedded Tomcat to `10.1.55` through the managed `tomcat.version` property to remediate `tomcat-embed-core@10.1.54` CVEs flagged by dependency-check in the control-plane web path.
- Completed E2 packaged-run documentation across `README.md`, `docs/config/README.md`, and preserved XML scenario READMEs with version-agnostic jar selection, plus explicit `-Dstart-class=com.etl.ETLEngineApplication` guidance so `mvn ... package` remains deterministic with both ETL and control-plane launchers present.
- Completed the B2 first runtime slice by closing retry-policy tracking/docs to `Done`, keeping retry step-scoped and evidence-first (`retry_attempt` / `retry_summary`), and preserving fail-fast boundaries between retry, skip, and deterministic data/config failures.
- Started centralized exception package migration for config failures by introducing canonical classes under `com.etl.exception.config` and retaining deprecated `com.etl.config.exception.*` wrappers for compatibility.
- Continued centralized exception package migration for reader failures by introducing canonical classes under `com.etl.exception.reader` and retaining deprecated `com.etl.reader.exception.*` wrappers for compatibility.
- Continued centralized exception package migration for writer failures by introducing canonical classes under `com.etl.exception.writer` and retaining deprecated `com.etl.writer.exception.*` wrappers for compatibility.
- Continued centralized exception package migration for processor failures by introducing canonical classes under `com.etl.exception.processor` and retaining deprecated `com.etl.processor.exception.*` wrappers for compatibility.
- Started `D1` first slice by stabilizing runtime error-taxonomy token normalization (`config`, `validation`, `transformation`, `source-read`, `target-write`, `runtime`) with compatibility aliases, and by promoting uncategorized reader failures to `source-read` evidence.
- Completed the follow-on `D1` reader-boundary alignment by documenting and testing the distinction between factory-time reader selection failures (`ReaderException` -> `factory`) and runtime read/open failures (`SourceReadException` -> `source-read`), including skip/retry alias normalization coverage for `read` and `source_read`.

## [1.7.8-rc1] - 2026-05-28

### Added
- Added control-plane API integration coverage that boots `ControlPlaneApiApplication` end-to-end, asserts ETL worker launcher beans stay out of the control-plane runtime, and verifies Jobs/Runs/Schedules endpoint flows under configured JDBC persistence.
- Added trigger persistence startup guardrails through `TriggerEventPersistenceModeGuard`, including marker-based detection for `controlplane.triggers.persistence.mode` switches (`jdbc` <-> `memory`) and explicit override support via `controlplane.triggers.persistence.allow-mode-switch=true`.
- Added scheduler dedup race coverage proving near-simultaneous pollers only claim one due instant per schedule.

### Changed
- Run-detail evidence links now document and emit explicit line anchors in `logs/<yyyy-MM-dd>/<scenario>.log#L<lineNumber>` format, and gracefully surface `log-file-missing` evidence when referenced log files are unavailable.
- Control-plane fallback docs now describe safe trigger-event persistence mode transitions and the fail-fast/explicit-override startup behavior.

## [1.7.8] - 2026-05-27

### Added
- Added post-merge hardening coverage for `zoneConvert`, including deterministic DST edge-case unit tests (spring-forward gap and fall-back overlap) and selected-job startup fail-fast assertions for invalid `toZone`, `inputPattern`, and `outputPattern` transform config values.
- Added first-pass architecture folder taxonomy scaffolding under `docs/architecture/` with new layer indexes for `foundations/`, `etl-core/`, `control-plane/`, and `operator-ui/` to keep ETL, control-plane, and UI direction easier to navigate.
- Added a new scheduler design note at `docs/architecture/control-plane/scheduler-architecture-direction.md` defining scheduler-as-control-plane boundaries, MVP scope, and selected-job launch-contract guardrails.
- Added a new operator UI design note at `docs/architecture/operator-ui/operator-ui-architecture-direction.md` covering admin, monitoring, schedule management, and UI-authored bundle guardrails.
- Added a follow-on Angular UI MVP note at `docs/architecture/operator-ui/angular-ui-mvp-structure.md` describing a practical monitoring-first Angular route map, component structure, phased rollout, and control-plane-facing API client boundary.
- Added a follow-on wireframes note at `docs/architecture/operator-ui/angular-ui-mvp-wireframes.md` capturing low-fidelity Jobs, Runs, Run detail, Schedules, and System screens for the Angular MVP.
- Added a first control-plane API contract note at `docs/architecture/control-plane/operator-ui-mvp-api-surface.md` defining MVP endpoints and view-model-aligned payloads for Jobs, Runs, Run detail, Schedules, and System screens.
- Added a machine-readable OpenAPI 3.1 draft at `docs/architecture/control-plane/operator-ui-mvp-openapi.yaml` for the same operator-UI MVP control-plane API surface.

### Changed
- Updated architecture and product index/navigation docs (`README.md`, `docs/README.md`, `docs/architecture/README.md`, and related Epic S / S1 / S3 / S4 references) to surface the new layer-oriented architecture map and UI/scheduler design anchors.
- Moved existing root-level architecture notes into their layer folders under `docs/architecture/foundations/`, `docs/architecture/etl-core/`, `docs/architecture/control-plane/`, and `docs/architecture/operator-ui/`, and completed cleanup by removing temporary root-level compatibility stubs after link updates.

## [1.7.7] - 2026-05-27

### Added
- Added T16 phase-1 processor transform extensibility support on the active `type: default` path through additive `mappings[].fields[].transforms[].config` provider-owned payload support.
- Added a built-in `zoneConvert` processor transform for timezone conversion (`fromZone`, `toZone`, `inputPattern`, optional `outputPattern`) with optional `fallbackValue`, including `fallbackValue: systemTime` support for conversion-failure fallback.
- Added a shipped ServiceLoader showcase extension provider (`ShowcaseProcessorExtensionProvider`) contributing `partnerStatusTranslate` as a custom transform example on top of built-in transforms.
- Added a preserved runnable showcase bundle at `src/main/resources/config-jobs/xml-to-csv-events-transform-showcase/` demonstrating chained built-ins (`expression`, `zoneConvert`, `valueMap`) plus one ServiceLoader extension transform (`partnerStatusTranslate`).
- Added focused transform/provider coverage for `zoneConvert` and `partnerStatusTranslate`, plus startup/config binding verification for provider-owned transform config envelopes.

### Changed
- Updated processor/config/architecture/product docs to reflect the shipped T16 seam baseline, including built-in vs ServiceLoader extension behavior and class-level call flow guidance.

## [1.7.6] - 2026-05-24

### Changed
- Improved the processor `conditional` transform runtime path with a bounded LRU SpEL expression cache (size `1024`) to reduce repeated parse overhead under high-cardinality workloads.
- Added structured conditional-transform observability (`PROCESSOR_TRANSFORM` cache hit/miss/evict, case match/default, and evaluation failure events) with expression-length truncation for safer logs.

### Added
- Added `ProcessorTransformEvaluationException` to classify transform-evaluation runtime failures without changing existing fail-fast semantics.

### Fixed
- Hardened embedded ordered-duplicate staging to use bounded `VARCHAR` payload/key/issue columns instead of H2 `CLOB`, reducing the risk of long-run LOB-reference timeout failures during winner-selection resolution.

## [1.7.5] - 2026-05-23

### Changed
- Started the T15 `S6` cutover by enforcing the shared default processor contract on the active selected-job path: selected `processor-config.yaml` files must now declare `type: default`, and non-default/legacy processor types fail fast during startup and direct runtime factory usage.
- Continued the T15 `S6` cutover by simplifying active processor runtime dispatch to the single shared default processor path, removing legacy type-map processor selection branches from `DynamicProcessorFactory`.
- Migrated preserved `config-jobs/**/processor-config*.yaml` bundles to the shared default processor contract and added regression coverage that enforces `type: default` across preserved bundles.
- Added a consolidated OneFlow runtime fallback reference with code-anchored decision matrices and linked it from runtime/config docs used for architecture and operator decision-making.
- Finalized S6 documentation alignment across architecture and ADR guidance so the selected-job runtime contract remains explicit (`type: default`, explicit steps, and fail-fast startup guardrails).

## [1.7.4] - 2026-05-22

### Added
- Added additive XML duplicate identity support through `duplicateIdentityMode: xmlNative` for nested XML scenarios, including ordered-resolver parity so the same identity semantics apply to keep-first duplicate checks and `duplicate + orderBy` winner selection.
- Added preserved runnable proof configs under `config-jobs/xml-nested-to-csv-tag-validation` showing the false-merge difference between `flatMapped` and `xmlNative` duplicate identity.
- Added focused XML duplicate guardrails and operator evidence, including startup rejection for selector-shaped flat-mapped XML keys, startup rejection for unsupported repeating-selector syntax, controlled fail-fast handling for repeating-node/list traversal, and advisory warning evidence (`PROCESSOR_GUARDRAIL event=xml_duplicate_flatmapped_advisory`) for nested XML mappings that still use simple flat duplicate keys.

### Changed
- Closed the T15 compatibility path through slices `S1`-`S5` on the active runtime contract, with parity evidence now recorded for the processor pipeline seam, deterministic rule-dispatch registry behavior, duplicate resolver parity, preserved bundle proof, and synced product/backlog tracking.
- Refreshed configuration, architecture, product-tracking, and release metadata so the shipped T15-compatible XML duplicate identity boundary is documented consistently while the intentional non-compatible `S6` cutover remains deferred.

## [1.7.3] - 2026-05-21

### Added
- Added first-slice processor `conditional` transform support with ordered `cases[].when -> then` evaluation, optional `defaultValue` fallback, startup validation for malformed conditional declarations, and focused transform/config regression coverage on the active transform-before-rules execution path.
- Added ordered-duplicate resolver observability evidence on the T4 follow-on path, including startup/runtime resolver-selection events plus step execution context fields (`orderedDuplicateResolverMode`, `orderedDuplicateResolverReason`) so operators can see which winner-selection storage path was chosen and why.
- Added optional ordered-duplicate `storageMode` override (`auto`, `memory`, `embeddedDb`) for `duplicate` rules with `orderBy`, with fail-fast validation for unsupported values or non-winner-selection usage and preserving `auto` as the default behavior.
- Added additive reject-quarantine publication through `processor-config.yaml -> rejectHandling.quarantinePath`, including focused runtime/config coverage and a preserved `customer-load-reject-quarantine` scenario bundle proving reject artifact publication to both reject and quarantine locations.
- Added cross-platform `scripts/job-runner.ps1` and `scripts/job-runner.sh` helpers plus `scripts/README.md` so teams can prepare generated XML classes and run one selected job bundle with repo-root-relative commands more consistently.

### Changed
- Closed `T4` as shipped hardening work, moved remaining XML-native duplicate identity follow-on to deferred `T15`, and refreshed backlog/checkpoint/architecture docs so the current processor-centered duplicate boundary remains explicit.

## [1.7.2] - 2026-05-19

### Changed
- Expanded the transformation planning docs with a capability catalog, current-vs-future roadmap guidance, Epic T sequencing, dedicated `T7`-`T14` backlog pages, and a new `docs/product/transformation-checkpoint.md` memory-aid so the current shipped processor-centered scope, next item (`T3`), deferred advanced order, and duplicate follow-on checkpoint remain easy to resume.

### Fixed
- Hardened file-ingestion completion/archive guardrails so completion-only archive evidence stays aligned with step status, with refreshed `FileIngestionRuntimeSupport` coverage and an updated nested XML -> CSV flow regression test proving the active behavior.

## [1.7.1] - 2026-05-19

### Changed
- Simplified the runtime logging stack for the `1.7.1` patch line by removing direct Log4j2 runtime dependencies and relying on Spring Boot's managed Logback path, which matches the shipped `logback-spring.xml` contract and avoids mixed-binding upgrade friction.
- Pinned the Spring Boot-managed Log4j bridge line to `2.25.4` so the transitive `log4j-to-slf4j`/`log4j-api` artifacts used for logging interoperability no longer resolve to `2.24.3` during security scans.
- Advanced the patch-line security remediation baseline by keeping Spring Boot `3.5.14`, removing the unused servlet/web starter so the shipped ETL runtime is explicitly non-web, and dropping redundant direct YAML, logging, and test-library declarations that were already covered by active managed dependencies.
- Restored the PR dependency-scan failure threshold to CVSS `7` after trimming the unused direct dependency surface and aligning the shipped runtime more closely with the documented non-web process model.

## [1.7.0] - 2026-05-18

### Added
- Added a shared unzip-before-read preparation slice for ZIP-backed CSV and XML file sources through the shared file-source artifact boundary, so validation, record counting, and readers can consume one extracted readable file while reject/archive disposition still targets the original configured artifact.
- Added a preserved `customer-load-zipped` scenario bundle plus focused ZIP-backed validation, reader, flow, and file-ingestion runtime-support coverage for the first shipped compressed-input proof.
- Added shared zip-on-archive packaging for plain file-backed CSV and XML sources through the same ZIP utility boundary, so archive-on-success can now publish one ZIP artifact containing the original source file as a single entry.
- Added optional ZIP publication for processor-owned reject CSV artifacts through `processor-config.yaml -> rejectHandling.packageAsZip`, so rejected-record evidence can now publish as one ZIP artifact after the reject file is finalized.
- Added optional ZIP publication for CSV, JSON, and XML file targets through target `packageAsZip`, so staged successful output can now publish as one ZIP artifact containing the native target file as a single entry.
- Added a dedicated `docs/architecture/foundations/security-test-strategy.md` note defining the phased security test baseline for selected-job guardrails, ZIP/path hardening, CI security scans, release gates, and verification-evidence expectations.

### Changed
- ZIP-backed file sources now auto-prepare from `filePath: ...zip` by convention, while the optional `unzip` block remains only for advanced overrides such as multi-entry selection or a custom extract directory.
- File-backed CSV/XML sources now fail fast when `unzip.enabled=true` is authored for a non-`.zip` `filePath`, both during source validation and again on the shared runtime preparation path if validation is bypassed, so explicit unzip remains aligned with real ZIP-backed artifacts instead of acting as a plain-file override.
- File-backed CSV/XML archive settings now also support `archive.packageAsZip=true` for plain source artifacts, while already-zipped source files continue to archive as their original ZIP artifact and fail fast if double-zipping is requested.
- Default ZIP extraction now stages prepared readable files under a runtime-owned JVM temp work root instead of beside the ingress/input artifact, while cleanup also prunes now-empty default prepared directories after validation failure or successful step completion.
- File-source config, runtime-support, and preserved-scenario docs now describe the shipped shared ZIP contract more explicitly, including optional `unzip.extractDir` normalization, prepared-file cleanup, and the active zip-on-archive packaging behavior.
- Staged file publication now applies the same success-only packaging rule across reject outputs and CSV/JSON/XML target outputs: writers and reject handling still create native files first, and ZIP artifacts are only promoted at the final publication boundary after successful completion.
- Shared ZIP extraction now detects duplicate matching entries before copy/extraction so a second match cannot write or overwrite prepared artifacts.
- Shared ZIP packaging now validates destination-path shape more defensively (including directory-target and source/target alias checks) before opening ZIP output streams.
- Pull request template checklist guidance now explicitly includes security-impact review and credential/token hygiene checks for each proposed change.
- PR unit-test workflow now includes enforceable security gates in addition to Maven tests: OWASP dependency vulnerability scanning with fail-on-threshold behavior, full-repository-history Gitleaks scanning, and checksum-verified Gitleaks binary installation before secret scanning.

### Fixed
- Shared ZIP packaging now removes partially created ZIP files when packaging fails, preventing orphaned artifacts on disk.
- Shared ZIP duplicate-entry diagnostics now avoid collecting every archive entry name in memory and keep constant-size tracking until an error path is needed.
- Shared ZIP utility Javadocs/comments now document basename-only entry behavior and cleanup expectations more clearly for operators and maintainers.

---

## [1.6.1] - 2026-05-16

### Changed
- Maintenance release only: aligned backlog/docs after completing the A4 generated-model naming/package contract, parked A6 as deferred internal cleanup, and refreshed release metadata without introducing a new runtime feature slice.

---

## [1.6.0] - 2026-05-14

### Added
- Added a flat JSON target format plus staged JSON-array writing so preserved and custom scenarios can convert XML sources into JSON output through the existing factory-driven runtime path.
- Added a preserved `xml-to-json-events` scenario bundle and focused JSON writer / XML-to-JSON flow coverage.
- Added a preserved `xml-nested-to-csv-tag-validation` scenario bundle showing nested XML source flattening into a flat CSV target with the shared processor path plus archive-on-success behavior for the sanitized XML input.
- Added focused nested XML -> CSV flow-proof coverage so generated source/target classes, shared processor mapping, CSV writing, and archived-source evidence are exercised together for the preserved tag-validation bundle.
- Added stricter XML source file validation with optional XSD/schema validation, reject-file-on-validation-failure behavior, and focused validation coverage for malformed XML, root/record mismatches, and schema failures.
- Added reader-specific exception types plus a runtime-categorizing item-stream reader wrapper so reader open/read/update/close failures surface through clearer reader-oriented error boundaries.
- Added a generalized `scripts/remove-job-bundle.ps1` cleanup utility so developers can remove a selected job bundle and its generated artifacts through one script instead of the older private-job-specific entry point.
- Added a dedicated `docs/architecture/etl-core/csv-to-xml-runtime-flow.md` operational guide covering the shipped `CSV -> XML` path, including flat vs nested XML targets, duplicate handling, staged publication, reject/archive behavior, and operator-facing evidence.
- Added focused `customer-load` flow-proof coverage through `CsvSourceToXmlTargetFlowTest`, plus refreshed scenario-reference coverage for preserved job bundles and hierarchy-logging coverage for descriptor-backed run evidence.

### Changed
- Explicit job-config startup now honors an optional top-level `job-config.yaml -> isActive` flag and fails fast before downstream config resolution when the selected job is inactive.
- Source and target `packageName` values can now be omitted across the active config-loading path, with job-scoped defaults still derived from the selected job identity for compatibility.
- Explicit `job-config.yaml` runs now require a non-blank `name` on the active generated-model naming path; folder-name fallback is no longer used for runtime/build-time package derivation.
- Explicit job runtime loading and build-time generation now also fail fast on generated-name collisions after logical-name normalization and on cross-step handoff names consumed before an earlier step produces them.
- Explicit job runtime loading and build-time generation now also warn when deprecated authored `com.etl.generated.job...` packageName bridge values drift from the package derived from the selected `job-config.yaml` name, while still honoring the authored value for compatibility.
- CSV targets now default `delimiter` to `,` when omitted or blank, while still honoring user-provided alternate separators at runtime.
- Architecture, config, README, and product-tracking docs now describe the shipped optional-`packageName` bridge baseline and the current JSON/CSV target behavior more consistently.
- CSV reader parsing and mapping are now more defensive, including quote-character parsing support in config, fail-fast field writability checks, and refreshed reader-focused regression coverage.
- Generated-model resolution and explicit job-config startup validation now handle job-scoped defaults, XML source contracts, and selected source/target class availability more consistently across preserved and private job bundles.
- Writer staging lifecycle behavior is now aligned more consistently across flat-file, XML, and JSON target paths, with stronger staged-file promotion/cleanup expectations reflected in tests and docs.
- Config, architecture, backlog, and private-job guidance docs now describe the current XML validation, reader hardening, generated-model naming bridge, and job-bundle cleanup workflow more accurately.
- Runtime, architecture, and config docs now describe the shipped bridge runtime more completely, including hierarchy logging and run evidence, model-resolution and IO seams, processing/file-ingestion support, and the current duplicate winner-selection behavior where runtime chooses in-memory vs embedded-database staging automatically from step volume.
- The preserved `customer-load` scenario README now documents the shipped flat `CSV -> XML` baseline as an explicit-job runtime example, including generation/run commands and tasklet-mode expectations for the sample input.
- `ScenarioConfigReferenceTest` now resolves preserved bundles directly from canonical `config-jobs/...` paths instead of using the deprecated alias bridge in new test coverage.

### Fixed
- JSON staged-array writing now categorizes open/write/update/close failures as runtime errors and cleans failed staged artifacts without masking the original serialization or stream-state failure.
- Fixed XML source validation gaps so configured schema paths, reject paths, archive paths, and job-relative XML definition paths are normalized and enforced more consistently during explicit job startup.
- Fixed reader factory and mapper failure paths so unsupported readers and invalid CSV field mappings fail earlier with clearer operator-facing errors instead of surfacing as late generic runtime failures.
- Fixed redundant validation-aware mapping logging checks so `ValidationAwareDynamicMapping` no longer carries unreachable null comparisons on its non-null processor input path.

---

## [1.5.0] - 2026-05-10

### Added
- Added processor-side `expression` transforms on the active `transforms[]` seam so mappings can derive target fields from source data, including derived fields that omit `from` when `expression` is the first transform.
- Added focused processor, mapping, validation-aware, and config-loader coverage proving expression-derived fields, resolved-value access, and fail-fast invalid-expression validation.
- Added a preserved `csv-to-nested-xml` scenario bundle proving that a CSV source can map into a generated nested XML target through explicit `job-config.yaml` selection and `modelDefinitionPath`.
- Added focused flow-proof coverage for CSV source to nested XML target execution, including generated nested target classes and final XML structure verification.
- Added optional `includeHeader` support on CSV targets so a CSV artifact produced by one step can be consumed by a downstream CSV source step in the same scenario using the normal header-skipping reader path.
- Added a preserved `xml-nested-to-csv-to-nested-xml` multi-step scenario bundle proving one selected job can execute nested XML -> CSV -> nested XML in one ordered run with an intermediate CSV handoff.
- Added focused roundtrip flow coverage for the new nested XML -> CSV -> nested XML multi-step scenario.
- Added a shared file-backed archive-on-success contract so archive behavior now applies across supported file sources such as CSV and XML, with scenario-relative archive path normalization and step-finished `archivedSourcePath` evidence on the active runtime path.
- Added a preserved `xml-nested-to-csv-to-nested-xml-archive-e2e` scenario bundle proving nested XML -> CSV -> nested XML execution together with XML source archive-on-success behavior.
- Added descriptor-backed `MAIN_FLOW_PLAN`, `SUBFLOW_PLAN`, and `SUBFLOW_SUMMARY` evidence so logs can explain synthesized subflows and report blocked downstream subflows when an upstream step fails.
- Added a refreshed hierarchy-aware `docs/architecture/etl-core/runtime-flow-walkthrough.html` GUI walkthrough so the product flow now shows `MainFlow -> SubFlow -> Step` alongside shipped logging and evidence vocabulary.

### Changed
- Refreshed architecture, config, scenario, and product-tracking documentation so archive-on-success is now described consistently as a shared file-backed source concern instead of CSV-only wording, and preserved XML archive proof guidance is now included in the active docs set.
- Clarified the repository policy that `private-jobs/` is a developer-local git-ignored workspace derived from preserved examples, and aligned contributor-facing guidance in `.gitignore`, `.github/PULL_REQUEST_TEMPLATE.md`, `.github/CODEOWNERS`, `README.md`, and config/docs references so private bundles stay out of GitHub.

### Deprecated
- Deprecated legacy `config-scenarios/...` bundle-path compatibility in favor of canonical `config-jobs/...` paths. The runtime and build-time generation entry points still resolve old paths temporarily through `ConfigBundlePathAliasResolver`, but new commands, docs, and examples should use `config-jobs/...` only.

---

## [1.4.0] - 2026-04-28

### Added
- Added first-slice field-level validation support in the default processor for CSV-backed scenarios, starting with `notNull` and `timeFormat` rules on mapped fields.
- Added first-slice rejected-record output for validation-aware CSV runs, including reject CSV artifacts with `_rejectField`, `_rejectRule`, and `_rejectMessage` metadata.
- Added archive-on-success support for CSV source configs through `archive.enabled`, `archive.successPath`, and `archive.namePattern`.
- Added a preserved `csv-validation-reject-archive` scenario bundle plus a realistic CSV input sample to prove accepted output, rejected output, and archived-source-file behavior together.
- Added step-finished evidence for `rejectedCount`, `rejectOutputPath`, and `archivedSourcePath` in machine-readable step lifecycle logging.
- Added a preserved `xml-to-csv-events` scenario bundle plus a realistic flat XML sample so XML-to-CSV runs can be exercised through explicit `job-config.yaml` selection as a baseline scenario.
- Added a source-validation SPI on the active runtime path through `SourceValidationService` and `SourceValidator`, with the first built-in validators covering CSV archive config and relational source config validation.
- Added an opt-in CSV file-level `validation` block supporting fail-fast file existence/readability checks plus `allowEmpty` and `requireHeaderMatch` policies on CSV sources.
- Added a processor-rule SPI behind `ValidationRuleEvaluator` through `ProcessorValidationRule`, with built-in `notNull`, `timeFormat`, and first-slice `duplicate` rule handlers plus extension-oriented tests.
- Added composite-key duplicate validation for the built-in `duplicate` processor rule through optional `keyFields` configuration, while keeping duplicate handling on the active processor-rule seam.
- Added ordered duplicate winner selection for the built-in `duplicate` rule through structured `orderBy` entries with `field` and `direction`, so users can retain the best record per duplicate key while `keyFields`-only configurations still keep the first encountered record.

### Changed
- The file-ingestion hardening work is now implemented as a first CSV-focused slice instead of design-only planning, while broader expression, conditional, and richer quarantine behavior remains future work.
- The built-in `duplicate` rule now supports both single-field and composite-key matching with keep-first/reject-later semantics, using step-local in-memory tracking for the simple keep-first path and a shared ordered-duplicate abstraction for winner-selection flows.
- When ordered duplicate winner selection is configured, step execution now resolves the winning record per key before final write, forcing tasklet-style final buffering for that mapping and choosing between in-memory or embedded-DB staging so lower-priority rows can be discarded safely.
- Refreshed processor-config documentation to state explicitly that duplicate checking is optional, that no configured `duplicate` rule means no duplicate-based filtering for that mapping, and that `keyFields`-only duplicate rules remain keep-first unless `orderBy` winner selection is configured.
- Refreshed transform-architecture documentation to state that the first planned YAML transform contract is processor-side and optional by omission, that ordered `transforms[]` chains are the intended shape for zero/one/many cleaner steps, that transform-then-reject flows are valid, and that future source-transform YAML is reserved for source-native adaptation cases rather than generic business/value rewriting.

### Deprecated
- Deprecated the legacy `com.etl.validation.*` package and `src/main/resources/validation-config.yaml` resource because they are not part of the active ETL runtime path. The supported validation path now runs through active source config validation and `processor-config.yaml` field rules.

---

## [1.3.0] - 2026-04-25
### Added
- Added machine-readable ETL lifecycle logging for explicit planning and execution evidence, including `RUN_EVENT`, `RUN_SUMMARY`, `STEP_PLAN`, `STEP_READY`, and `STEP_EVENT` messages.
- Added a local verification workflow with smoke-check scripts and generated markdown reports under `target/`, including retained timestamped report snapshots for recent runs.
- Added orchestration-focused regression coverage for explicit step order, missing processor mappings, scenario config references, and relational placeholder validation.
- Added categorized verification reporting for change-focused verification, regression-suite verification, runtime/smoke verification, and release-readiness interpretation.
- Added a shared in-memory verification evidence model in the report generator as the phase-1 foundation for future multi-format reporting.
- Added backlog and ADR artifacts to track enterprise verification reporting as a first-class product capability.

### Changed
- Explicit `etl.config.job` runs now require a non-empty `steps` list in `job-config.yaml`, and runtime orchestration resolves source/target execution by configured names instead of positional pairing.
- Preserved scenario bundles such as `customer-load`, `department-load`, `cust-dept-load`, `csv-to-sqlserver`, and `relational-to-relational` now declare their ETL execution order through explicit `steps` definitions.
- Startup validation now checks selected relational source/target configs before job execution and rejects placeholder connection values such as `<SQLSERVER_HOST>` with clearer operator-facing configuration errors.
- README guidance now documents the explicit `steps` contract, machine-readable execution logs, and the repeatable verification-report workflow for local change validation.
- The verification report generator now separates evidence collection from Markdown rendering so future HTML and machine-readable outputs can build on the same evidence contract.
- Product backlog and documentation artifacts now reflect the completed 1.3.0 work more accurately, including explicit-step orchestration, fail-fast relational validation, and verification-reporting progress.

### Fixed
- Prevented preserved SQL Server scenarios with placeholder connection values from progressing into late JDBC/runtime failures by failing fast during configuration resolution.

---

## [1.2.0] - 2026-04-24
### Added
- Added `etl.config.allow-demo-fallback` as an explicit local/demo-only switch for legacy direct-path and bundled classpath fallback.
- Added scenario/job-run Logback output with MDC fields for `scenario`, `runCorrelationId`, `jobExecutionId`, and `stepName`, including daily scenario log files.
- Added `docs/product/product-backlog.md` as the execution-facing product backlog, including milestone-aligned board-style tracking from current state to enterprise-grade target.
- Added `docs/architecture/etl-core/transformation-capability-roadmap.md` to make transformation maturity an explicit part of the enterprise-grade ETL product direction.

### Changed
- Configuration loading is now strict by default; startup fails fast when `etl.config.job` is not provided.
- Fallback to direct-path or bundled classpath YAML now happens only when `etl.config.allow-demo-fallback=true` is explicitly enabled.
- `etl.config.job` is now the default enterprise configuration entry point for one ETL run.
- Expanded architecture roadmap documentation for future job-history/operational observability, scenario/job-run logging strategy, and AI-assisted operations intelligence, including new dedicated design notes and refreshed documentation cross-references.
- Expanded the product roadmap and backlog to treat transformation capability as a first-class maturity track, from structural mapping toward rule-based and enterprise-grade transformation behavior.
- Runtime config selection is now cached and also exposes resolved scenario metadata for startup and logging concerns.
- Runtime log routing now writes to daily scenario files in the form `logs/<yyyy-MM-dd>/<scenario>.log` instead of one file per run.
- Default demo/runtime config paths are now repo-relative and portable instead of assuming Windows-specific `C:/ETLDemo/...` locations.

### Fixed
- N/A
---

## [1.1.6] - 2026-04-22
### Added
- Added a preserved `relational-to-relational` scenario bundle selected through `etl.config.job`.
- Added H2-backed large-volume relational flow coverage with a 20k-row source-to-target integration test.

### Changed
- Updated relational documentation to reflect phase-1 support, large-volume tuning guidance, and preserved scenario usage.
- Sanitized committed SQL Server scenario YAML to use placeholders instead of live connection values.
- Aligned reader and writer factory dispatch to use `ModelFormat` consistently instead of internal raw string keys.

### Fixed
- Normalized relational writer tests to use the relational target model class consistently.
- Hardened relational config validation so malformed source/target JDBC settings fail fast before runtime reads and writes begin.
---

## [1.1.5] - 2026-04-17
### Changed
- Introduced `ResolvedModelMetadata` to normalize runtime source, processing, and write class resolution.
- Centralized runtime model interpretation in `GeneratedModelClassResolver` and threaded resolved metadata through batch, processor, and writer setup.
- Improved XML runtime handling so tasklet mode continues to use wrapper objects while chunk mode streams XML record classes directly.
- Deprecated the unused `TypeConversionUtils.mapToJavaType` helper for future cleanup.

### Fixed
- Fixed large-file XML chunk processing so record classes marshal correctly during chunk-oriented writes.
- Updated XML record generation to include `@XmlRootElement`, allowing streamed record marshalling for XML chunk mode.
- Verified generated model sources and classes can be deleted and regenerated successfully during application startup.
- Validated ETL execution with 20k and 50k CSV inputs, including correct chunk selection and XML output structure.

---

## [1.1.4] - 2026-02-19
### Added
- Added support for dynamic compilation and class loading of generated model classes
- Enhanced XmlModelGenerator with improved validation and file generation logic
- Updated model generation to support both source and target configs for XML and relational formats
- Added helper methods for directory creation and file writing
- Improved error handling and logging around model generation processes

---

## [1.1.3] - 2025-12-29
### Fixed
- Centralized null and blank validation using `ValidationUtils`
- Improved validation consistency across CSV and XML model generators
- Prevented model generation with empty or invalid field definitions

### Improved
- Cleaner generator implementations with reduced duplication
- More robust error handling using model-specific exceptions
- Increased flexibility using `List<? extends FieldDefinition>`

---

## [1.1.2] - 2025-12-18
### Changed
- Refactored dynamic Source/Target configuration hierarchy using abstract base classes.
- Stabilized YAML polymorphic loading for CSV, XML, and DB sources.
- Improved DynamicReaderFactory and DynamicWriterFactory error handling.
- Unified field abstraction using FieldDefinition across readers, processors, and writers.
- Centralized ETL exception hierarchy for clearer error reporting.

### Fixed
- Resolved null `type` issues during dynamic reader resolution.
- Fixed JAXB marshaller configuration for dynamic XML writers.

---

## [1.1.1] - 2025-12-13
### Added
- Implemented **XmlDynamicWriter**, enabling dynamic XML generation for target models.
- Added unified **FieldDefinition** model for both source and target configurations.
- Enhanced **model generator engine** to support XML targets using dynamic field reflection.
- Added automatic JAXB-annotated model generation for XML target structures.
- Integrated XML writer into the **DynamicWriterFactory** for seamless runtime selection.
- Improved error handling and logging around XML marshaling operations.

### Changed
- Refactored internal writer architecture to support pluggable dynamic writers (CSV, XML, DB, etc.).
- Updated configuration loader to use unified `fields` property for both source and target YML definitions.

### Fixed
- Addressed inconsistencies between ColumnConfig vs. FieldDefinition naming.
- Fixed field-level mapping issues when switching target types.

---

## [1.1.0] - 2025-12-10
### Added
- Introduced **dynamic mapping engine** to support flexible sourceâ€“target transformations.
- Added **TypeConversionUtils** as a centralized utility for field conversion and reflection handling.
- Added **custom exception hierarchy** (`TypeConversionException`, `ReflectionException`) for more meaningful error reporting.
- Added **AOP-based logging** for Reader, Processor, Writer, Step, and Job execution.
- Added **Spring Profiles** to toggle dynamic ETL and environment-specific configurations.
- Added support for **multiple sourceâ€“target pairs** within a single ETL job.
- Added generic **DynamicReader**, **DynamicProcessor**, and **DynamicWriter** infrastructure.
- Added reusable **ReflectionHelper** and **TypeConversionUtils** for field-level mapping.

### Changed
- Refactored FieldSetMapper to use centralized TypeConversionUtils and Reflection utilities.
- Overhauled DynamicProcessor architecture to introduce MappingEngine and improve modularity.
- Refactored ETL pipeline to use **builder-pattern configuration** for dynamic steps.
- Updated BatchConfig to handle dynamic step creation per Sourceâ€“Target pair.

### Fixed
- Fixed class-loading issues when resolving source/target model classes dynamically.
- Fixed mismatched writer invocation due to reflection-based method signatures.

---

## [1.0.0] - 2025-11-01
### Added
- Initial version of Dynamic ETL Engine.
- Basic CSV/XML Reader and Writer infrastructure.
- Config-driven Source, Target, and Processor definitions.
- Job, Step, Reader, Processor, Writer bootstrapping.

---
