# Backlog item index

Use this folder for item-level drill-down pages that sit under the execution board in [`../product-backlog.md`](../product-backlog.md) and beside the shared epic pages in [`../epics/README.md`](../epics/README.md).

## Purpose

These pages exist so every execution-board item can carry its own problem statement, scope boundary, acceptance criteria, and delivery notes without turning the board into a long narrative document.

Use this page as the main browse index when you want to navigate backlog items by epic instead of scanning the full execution board table.

## Start here

- [`../product-backlog.md`](../product-backlog.md) - canonical execution board for `Priority`, `Status`, `Milestone`, and `Dependency`
- [`../epics/README.md`](../epics/README.md) - epic-level grouping for shared intent, boundaries, and related architecture links
- [`TEMPLATE.md`](TEMPLATE.md) - starting point for adding a new backlog item page

## Browse by epic

### Epic A - Runtime correctness and orchestration clarity

- [`A1 - Replace positional source-target pairing with explicit step pairing or step definitions`](A1-explicit-step-pairing-and-step-definitions.md)
- [`A2 - Validate scenario completeness before job start`](A2-validate-scenario-completeness-before-job-start.md)
- [`A3 - Add job-level activation guardrail`](A3-job-level-activation-guardrail.md)
- [`A4 - Standardize generated-model naming and package derivation`](A4-standardize-generated-model-naming-and-package-derivation.md)
- [`A5 - Add relational source column alias contract and reader mapping`](A5-relational-source-column-alias-contract.md)
- [`A6 - Retire remaining internal generated-model package bridge`](A6-retire-internal-generated-model-package-bridge.md)

### Epic B - Runtime hardening and file behavior

- [`B1 - Introduce configurable skip policy support`](B1-configurable-skip-policy-support.md)
- [`B2 - Introduce configurable retry policy support where appropriate`](B2-configurable-retry-policy-support.md)
- [`B3 - Archive processed source files after successful file-based runs`](B3-archive-processed-source-files-after-success.md)
- [`B4 - Strict XML source validation and optional XSD checks`](B4-strict-xml-source-validation-and-optional-xsd.md)
- [`B5 - CSV reader parsing hardening`](B5-csv-reader-parsing-hardening.md)
- [`B6 - Shared zip/unzip service boundary for file-based source preparation and archive packaging`](B6-shared-zip-unzip-service-boundary-for-file-based-source-preparation-and-archive-packaging.md)

### Epic C - Run summary, audit, and reconciliation

- [`C1 - Emit machine-readable run summary with scenario, status, and duration`](C1-machine-readable-run-summary.md)
- [`C2 - Complete run-level source / written / rejected count rollup`](C2-run-level-count-rollup-and-reconciliation.md)

### Epic D - Error taxonomy and failure categorization

- [`D1 - Add stable error taxonomy / error categories`](D1-stable-error-taxonomy-and-categories.md)

### Epic E - Portability and packaged-run guidance

- [`E1 - Finalize cross-platform defaults and path handling rules`](E1-cross-platform-defaults-and-path-handling.md)
- [`E2 - Add packaged-run guidance for jar execution with scenario configs`](E2-packaged-run-guidance-for-jar-execution.md)
- [`E3 - Centralize product-brand naming and doc refresh automation`](E3-centralize-brand-naming-and-doc-refresh.md)

### Epic F - Restartability and recovery semantics

- [`F1 - Define restart semantics per execution mode`](F1-restart-semantics-per-execution-mode.md)

### Epic G - Secret injection and secure configuration

- [`G1 - Support secret injection via environment or secure config source`](G1-secret-injection-via-environment-or-secure-config-source.md)

### Epic P - Source-native parser maturity

- [`P1 - Freeze parser roadmap around CSV and XML source-native maturity`](P1-freeze-parser-roadmap-around-csv-and-xml-maturity.md)
- [`P2 - Expand CSV parser strictness and malformed-row categorization`](P2-expand-csv-parser-strictness-and-malformed-row-categorization.md)
- [`P3 - Expand XML parser maturity for namespace-aware and fragment contracts`](P3-expand-xml-parser-maturity-for-namespace-and-fragment-contracts.md)
- [`P4 - Prove CSV and XML parser maturity through preserved scenarios and verification`](P4-prove-csv-and-xml-parser-maturity-through-preserved-scenarios-and-verification.md)
- [`P5 - Define native parser adoptability and CSV-first sidecar integration readiness`](P5-native-parser-adoptability-and-sidecar-integration-readiness.md)

### Epic S - Scheduling and control plane

- [`S1 - Define schedule model and trigger contract for scenario-based execution`](S1-schedule-model-and-trigger-contract.md)
- [`S2 - Add time-based schedule definitions with pause/resume controls`](S2-time-based-schedule-definitions-with-pause-resume.md)
- [`S3 - Add overlap policy, missed-run handling, and basic trigger audit trail`](S3-overlap-policy-missed-run-handling-and-trigger-audit-trail.md)
- [`S4 - Define control-plane operational data model`](S4-control-plane-operational-data-model.md)

### Epic T - Transformation capability maturity

- [`T1 - Add field-level validation and first reject-handling slice`](T1-field-level-validation-and-first-reject-handling-slice.md)
- [`T1a - Define processor transform SPI and first cleaner/normalization slice`](T1a-processor-transform-spi-and-first-cleaner-normalization-slice.md)
- [`T2 - Add expression-based derived field support`](T2-expression-based-derived-field-support.md)
- [`T3 - Add conditional transformation rule support`](T3-conditional-transformation-rule-support.md)
- [`T4 - Expand transformation quarantine and duplicate hardening`](T4-transformation-quarantine-and-duplicate-hardening.md)
- [`T5 - Define lookup/enrichment processor baseline`](T5-reference-set-validation-and-enrichment-baseline.md)
- [`T6 - Add shared default-value and placeholder mapping baseline`](T6-shared-default-value-and-placeholder-mapping.md)
- [`T7 - Define duplicate-tracking scalability redesign as a separate deferred track`](T7-duplicate-tracking-scalability-redesign-deferment.md)
- [`T8 - Define reusable transform profiles and versioning contract`](./T8-reusable-transform-profiles-and-versioning.md)
- [`T9 - Define source-native transformation seam before runtime records`](./T9-source-native-transformation-seam.md)
- [`T10 - Define record-level transformation stage beyond field-centric mapping`](./T10-record-level-transformation-stage.md)
- [`T11 - Define cross-record window and aggregation transformation semantics`](./T11-cross-record-window-and-aggregation-transforms.md)
- [`T12 - Define transformation governance and lineage evidence model`](./T12-transformation-governance-and-lineage.md)
- [`T13 - Define transform-stage observability metrics and operational evidence`](./T13-transform-stage-observability-metrics.md)
- [`T14 - Define secure data-shaping transforms for sensitive fields`](./T14-secure-data-shaping-transforms.md)
- [`T15 - Define XML-native duplicate identity for nested XML source scenarios`](./T15-xml-native-duplicate-identity-for-nested-xml-sources.md)

### Epic V - Verification evidence and reporting

- [`V1 - Define enterprise verification evidence model and report categories`](V1-enterprise-verification-evidence-model-and-report-categories.md)
- [`V2 - Generate Markdown verification reports from the shared evidence model`](V2-markdown-verification-reports-from-shared-evidence-model.md)
- [`V3 - Generate HTML verification reports with drill-down enterprise views`](V3-html-verification-reports-with-drill-down-enterprise-views.md)
- [`V4 - Define verification-report retention, provenance, and release gating rules`](V4-verification-report-retention-provenance-and-release-gating.md)

### Epic X - File transport and SFTP boundary

- [`X1 - Define SFTP transport contract and deployment boundary`](X1-sftp-transport-contract-and-deployment-boundary.md)
- [`X2 - Add first inbound SFTP staged pull capability`](X2-first-inbound-sftp-staged-pull-capability.md)
- [`X3 - Add remote post-success file handling and failure categorization for SFTP`](X3-remote-post-success-file-handling-and-failure-categorization.md)
- [`X4 - Define partner-facing transport security and isolated worker boundary`](X4-partner-facing-transport-security-and-isolated-worker-boundary.md)

## Maintenance rule

Keep the responsibilities separated:

- [`../product-backlog.md`](../product-backlog.md) is the canonical place for item-level `Priority`, `Status`, `Milestone`, and `Dependency`
- [`../epics/README.md`](../epics/README.md) and the epic pages hold shared capability intent across several backlog items
- this folder holds item-specific scope, acceptance criteria, and implementation notes
- backlog item pages should link back to their matching epic page so readers can move from one item to the broader capability track
- keep this index in sync when new backlog item pages are added or renamed

## Naming rule

Use one file per backlog item in this format:

- `<ID>-<kebab-case-short-title>.md`

Examples:

- `A1-explicit-step-pairing-and-step-definitions.md`
- `T1a-processor-transform-spi-and-first-cleaner-normalization-slice.md`
- `V4-verification-report-retention-provenance-and-release-gating.md`

