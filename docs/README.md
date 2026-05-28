# Architecture & Design Docs

<img src="assets/github-social-preview-tagline.svg" alt="OneFlow social preview" width="1100" />

This folder is the architecture backbone for `spring-etl-engine`, presented as part of the **OneFlow** product-facing documentation experience.

For GitHub-facing product language, **OneFlow** should be presented as a focused, config-driven runtime for repeatable file-based and integration-oriented flows, not as a traditional ETL suite in the style of Informatica or SSIS.

As features grow, the goal is to keep architectural intent in the repository instead of in chat threads, memory, or slide decks.

## What belongs here

- `architecture/` - feature and system-level design notes
- `adr/` - Architecture Decision Records (why a decision was made)
- `config/` - field-level config references and preserved scenario examples
- `product/` - product vision, backlog, milestones, and execution tracking

## Core terms

Use these short definitions as the shared vocabulary for the rest of the docs:

- **job bundle** - one runnable config folder for one use case. The checked-in preserved bundle root is `src/main/resources/config-jobs/`. Baseline YAML files under `src/main/resources/` remain simple demo-fallback defaults, not the primary preserved-scenario authoring path. Developer-local private bundles should be copied from those preserved examples into the git-ignored repo-root [`private-jobs/`](../private-jobs/README.md), preferably grouped as `private-jobs/<collection>/<job-bundle>/config/`. Legacy `config-scenarios/...` paths remain temporarily available as deprecated compatibility aliases.
- **`job-config.yaml`** - the run entry point that selects config files and ordered steps for one scenario; see [`config/job-config.md`](config/job-config.md)
- **main flow** - the top-level reusable business flow executed inside one selected scenario; see [`architecture/etl-core/hierarchical-flow-composition.md`](architecture/etl-core/hierarchical-flow-composition.md)
- **subflow** - a reusable grouped phase inside one main flow, containing one or more ordered steps; see [`architecture/etl-core/hierarchical-flow-composition.md`](architecture/etl-core/hierarchical-flow-composition.md)
- **step** - one ordered `source -> processor -> target` unit inside a selected run
- **source config** - the YAML contract that describes where records come from; see [`config/README.md`](config/README.md)
- **target config** - the YAML contract that describes where records go; see [`config/README.md`](config/README.md)
- **processor mapping** - the field-level contract that maps data from the selected source to the selected target; see [`config/processor/default-processor.md`](config/processor/default-processor.md)
- **format** - the connector type selected in config, such as `csv`, `xml`, or `relational`
- **factory** - a runtime component that creates the correct reader, writer, or processor implementation from the selected config type
- **resolver** - a runtime component that selects the correct metadata or implementation for the current step, such as model classes or relational vendor behavior
- **database dialect** - the relational abstraction that keeps vendor-specific SQL behavior behind one `relational` format instead of creating vendor-per-format modeling
- **control plane** - the optional operational layer that can host plug-and-play OneFlow capabilities (for example scheduler, monitoring history, and future modules such as Hypercare) while still resolving back to the same selected `job-config.yaml` runtime contract; external schedulers/orchestrators should remain equally valid launchers of that same contract

## How to navigate these docs

Start with the path that matches your goal:

| If you want to... | Start here | Then go to |
|---|---|---|
| run or configure one scenario | [`config/README.md`](config/README.md) | [`config/job-config.md`](config/job-config.md) and one preserved or private job bundle |
| browse the architecture folder by topic | [`architecture/README.md`](architecture/README.md) | [`architecture/foundations/README.md`](architecture/foundations/README.md) and [`architecture/etl-core/README.md`](architecture/etl-core/README.md) |
| understand the shipped runtime flow | [`architecture/etl-core/README.md`](architecture/etl-core/README.md) | [`architecture/etl-core/runtime-flow.md`](architecture/etl-core/runtime-flow.md), [`architecture/etl-core/runtime-flow-walkthrough.html`](architecture/etl-core/runtime-flow-walkthrough.html) for the hierarchy-aware product-flow walkthrough, and [`architecture/etl-core/csv-to-xml-runtime-flow.md`](architecture/etl-core/csv-to-xml-runtime-flow.md) for a flow-level operational deep dive |
| understand the next runtime architecture target | [`architecture/etl-core/scenario-driven-runtime-direction.md`](architecture/etl-core/scenario-driven-runtime-direction.md) | [`architecture/control-plane/README.md`](architecture/control-plane/README.md) and [`architecture/etl-core/1-4-to-next-architecture-classification.md`](architecture/etl-core/1-4-to-next-architecture-classification.md) |
| understand main flow / subflow composition | [`architecture/etl-core/hierarchical-flow-composition.md`](architecture/etl-core/hierarchical-flow-composition.md) | [`architecture/etl-core/scenario-driven-runtime-direction.md`](architecture/etl-core/scenario-driven-runtime-direction.md) and [`config/job-config.md`](config/job-config.md) |
| understand how simple and complex flows normalize into one model | [`architecture/etl-core/flow-normalization-rules.md`](architecture/etl-core/flow-normalization-rules.md) | [`architecture/etl-core/hierarchical-flow-composition.md`](architecture/etl-core/hierarchical-flow-composition.md) and [`config/job-config.md`](config/job-config.md) |
| assess the gap from shipped runtime to the reusable scenario model | [`architecture/etl-core/runtime-to-scenario-gap-assessment.md`](architecture/etl-core/runtime-to-scenario-gap-assessment.md) | [`architecture/etl-core/scenario-driven-runtime-direction.md`](architecture/etl-core/scenario-driven-runtime-direction.md) and [`architecture/etl-core/hierarchical-flow-composition.md`](architecture/etl-core/hierarchical-flow-composition.md) |
| understand validation, transforms, or extension seams | [`architecture/etl-core/extension-points.md`](architecture/etl-core/extension-points.md) | [`config/processor/default-processor.md`](config/processor/default-processor.md) |
| understand future scheduler/backend direction | [`architecture/control-plane/scheduler-architecture-direction.md`](architecture/control-plane/scheduler-architecture-direction.md) | [`architecture/control-plane/README.md`](architecture/control-plane/README.md) and [`product/backlog-items/S1-schedule-model-and-trigger-contract.md`](product/backlog-items/S1-schedule-model-and-trigger-contract.md) |
| understand the first control-plane API contract for Angular MVP screens | [`architecture/control-plane/operator-ui-mvp-api-surface.md`](architecture/control-plane/operator-ui-mvp-api-surface.md) | [`architecture/operator-ui/angular-ui-mvp-structure.md`](architecture/operator-ui/angular-ui-mvp-structure.md) and [`architecture/operator-ui/angular-ui-mvp-wireframes.md`](architecture/operator-ui/angular-ui-mvp-wireframes.md) |
| use a machine-readable OpenAPI draft for the control-plane MVP API | [`architecture/control-plane/operator-ui-mvp-openapi.yaml`](architecture/control-plane/operator-ui-mvp-openapi.yaml) | [`architecture/control-plane/operator-ui-mvp-api-surface.md`](architecture/control-plane/operator-ui-mvp-api-surface.md) and [`architecture/operator-ui/angular-ui-mvp-structure.md`](architecture/operator-ui/angular-ui-mvp-structure.md) |
| understand future admin/monitor/job-authoring UI direction | [`architecture/operator-ui/operator-ui-architecture-direction.md`](architecture/operator-ui/operator-ui-architecture-direction.md) | [`architecture/operator-ui/README.md`](architecture/operator-ui/README.md) and [`architecture/control-plane/scheduler-architecture-direction.md`](architecture/control-plane/scheduler-architecture-direction.md) |
| understand a practical Angular MVP structure for the operator UI | [`architecture/operator-ui/angular-ui-mvp-structure.md`](architecture/operator-ui/angular-ui-mvp-structure.md) | [`architecture/operator-ui/operator-ui-architecture-direction.md`](architecture/operator-ui/operator-ui-architecture-direction.md) and [`architecture/control-plane/control-plane-worker-boundary.md`](architecture/control-plane/control-plane-worker-boundary.md) |
| understand the first wireframes for the Angular operator UI MVP | [`architecture/operator-ui/angular-ui-mvp-wireframes.md`](architecture/operator-ui/angular-ui-mvp-wireframes.md) | [`architecture/operator-ui/angular-ui-mvp-structure.md`](architecture/operator-ui/angular-ui-mvp-structure.md) and [`architecture/operator-ui/operator-ui-architecture-direction.md`](architecture/operator-ui/operator-ui-architecture-direction.md) |
| understand parser capability boundaries for file sources | [`architecture/etl-core/oneflow-file-parser-capabilities.md`](architecture/etl-core/oneflow-file-parser-capabilities.md) | [`architecture/etl-core/file-ingestion-hardening.md`](architecture/etl-core/file-ingestion-hardening.md) and [`config/source/csv-source.md`](config/source/csv-source.md) |
| understand how future C/C++ parsers can fit without breaking the ETL core boundary | [`architecture/etl-core/native-parser-adoptability.md`](architecture/etl-core/native-parser-adoptability.md) | [`architecture/etl-core/oneflow-file-parser-capabilities.md`](architecture/etl-core/oneflow-file-parser-capabilities.md) and [`adr/0010-keep-native-parsers-behind-java-reader-boundary.md`](adr/0010-keep-native-parsers-behind-java-reader-boundary.md) |
| understand the first concrete CSV-first sidecar protocol for future native parsing | [`architecture/etl-core/csv-native-parser-sidecar-protocol.md`](architecture/etl-core/csv-native-parser-sidecar-protocol.md) | [`architecture/etl-core/native-parser-adoptability.md`](architecture/etl-core/native-parser-adoptability.md) and [`architecture/etl-core/oneflow-file-parser-capabilities.md`](architecture/etl-core/oneflow-file-parser-capabilities.md) |
| understand the Java `ItemStreamReader` adapter contract for future native parsing | [`architecture/etl-core/java-native-parser-reader-adapter-contract.md`](architecture/etl-core/java-native-parser-reader-adapter-contract.md) | [`architecture/etl-core/csv-native-parser-sidecar-protocol.md`](architecture/etl-core/csv-native-parser-sidecar-protocol.md) and [`architecture/etl-core/native-parser-adoptability.md`](architecture/etl-core/native-parser-adoptability.md) |
| understand parser product planning and the CSV/XML-first freeze | [`product/epics/epic-p-source-native-parser-maturity.md`](product/epics/epic-p-source-native-parser-maturity.md) | [`product/product-backlog.md`](product/product-backlog.md) and [`architecture/etl-core/oneflow-file-parser-capabilities.md`](architecture/etl-core/oneflow-file-parser-capabilities.md) |
| understand security testing scope, release gates, and rollout | [`architecture/foundations/security-test-strategy.md`](architecture/foundations/security-test-strategy.md) | [`architecture/etl-core/file-ingestion-hardening.md`](architecture/etl-core/file-ingestion-hardening.md) and [`architecture/control-plane/job-history-and-operational-observability.md`](architecture/control-plane/job-history-and-operational-observability.md) |
| understand relational support | [`architecture/etl-core/relational-db-support.md`](architecture/etl-core/relational-db-support.md) | [`config/source/relational-source.md`](config/source/relational-source.md) and [`config/target/relational-target.md`](config/target/relational-target.md) |
| see what is planned next | [`product/product-backlog.md`](product/product-backlog.md) | [`architecture/foundations/etl-product-evolution-roadmap.md`](architecture/foundations/etl-product-evolution-roadmap.md) |

## Status legend

Use these labels while reading:

- **Shipped** - part of the active runtime path today
- **Current baseline + future evolution** - starts from the shipped baseline and also preserves the intended next direction
- **Future direction** - design guidance, not a shipped runtime path today
- **Deprecated** - retained temporarily for cleanup or migration and not part of the active contract

## Documentation standard

For every significant enhancement, add or update:

1. one architecture note
2. at least one Mermaid diagram if the runtime/config flow changes
3. one ADR if the change introduces a meaningful design decision or tradeoff

## Current baseline docs

### Architecture
- [`architecture/README.md`](architecture/README.md) - folder-level index for architecture notes, grouped by topic so the growing design-note set stays navigable
- [`architecture/foundations/README.md`](architecture/foundations/README.md) - cross-cutting architecture baseline, roadmap, and guardrails grouped as a foundations layer
- [`architecture/etl-core/README.md`](architecture/etl-core/README.md) - shipped ETL worker runtime notes grouped as an ETL-core layer
- [`architecture/control-plane/README.md`](architecture/control-plane/README.md) - optional scheduler, trigger, retained-history, and control-plane backend notes grouped together
- [`architecture/operator-ui/README.md`](architecture/operator-ui/README.md) - future operator UI notes for admin, monitoring, scheduling, and job authoring
- [`architecture/foundations/overview.md`](architecture/foundations/overview.md) - current high-level system architecture
- [`architecture/etl-core/scenario-driven-runtime-direction.md`](architecture/etl-core/scenario-driven-runtime-direction.md) - target next-direction runtime contract for strict scenario-driven execution without compromising future scale, UI views, or richer transformations
- [`architecture/etl-core/hierarchical-flow-composition.md`](architecture/etl-core/hierarchical-flow-composition.md) - frozen working direction for reusable main flows composed from reusable subflows and executable steps under one selected scenario
- [`architecture/etl-core/flow-normalization-rules.md`](architecture/etl-core/flow-normalization-rules.md) - normalization rules for simple and complex flows, including optional or implicit subflow handling in the first slice
- [`architecture/etl-core/runtime-to-scenario-gap-assessment.md`](architecture/etl-core/runtime-to-scenario-gap-assessment.md) - current-state versus target-state gap assessment for reusable components evolving into scenario-driven execution
- [`architecture/etl-core/1-4-to-next-architecture-classification.md`](architecture/etl-core/1-4-to-next-architecture-classification.md) - transition map for classifying current 1.4.x code into reuse, bridge, legacy, and remove buckets during the next architecture shift
- [`architecture/etl-core/runtime-flow.md`](architecture/etl-core/runtime-flow.md) - end-to-end ETL runtime flow
- [`architecture/etl-core/oneflow-runtime-fallback-reference.md`](architecture/etl-core/oneflow-runtime-fallback-reference.md) - consolidated matrix of shipped OneFlow fallback/default decisions for runtime and config behavior
- [`architecture/control-plane/control-plane-worker-boundary.md`](architecture/control-plane/control-plane-worker-boundary.md) - future boundary between the mandatory ETL core worker and optional scheduler, watcher, persistence, and UI layers
- [`architecture/control-plane/control-plane-operational-data-model.md`](architecture/control-plane/control-plane-operational-data-model.md) - conceptual retained data model for optional scheduler, watcher, trigger, run, step, artifact, and recovery-lineage history
- [`architecture/control-plane/control-plane-local-relational-schema.md`](architecture/control-plane/control-plane-local-relational-schema.md) - SQLite-first local relational schema direction for optional control-plane history with later PostgreSQL or SQL Server portability
- [`architecture/control-plane/scheduler-architecture-direction.md`](architecture/control-plane/scheduler-architecture-direction.md) - first scheduler-specific architecture direction under the optional control-plane layer
- [`architecture/control-plane/operator-ui-mvp-api-surface.md`](architecture/control-plane/operator-ui-mvp-api-surface.md) - first control-plane API surface for Angular MVP Jobs, Runs, Run detail, Schedules, and System screens
- [`architecture/control-plane/operator-ui-mvp-openapi.yaml`](architecture/control-plane/operator-ui-mvp-openapi.yaml) - machine-readable OpenAPI 3.1 draft of the same MVP control-plane API surface
- [`architecture/operator-ui/operator-ui-architecture-direction.md`](architecture/operator-ui/operator-ui-architecture-direction.md) - first operator UI architecture direction over the optional control-plane/backend layer
- [`architecture/operator-ui/angular-ui-mvp-structure.md`](architecture/operator-ui/angular-ui-mvp-structure.md) - practical Angular-based MVP structure for monitoring-first UI rollout, route map, and control-plane-facing API client boundaries
- [`architecture/operator-ui/angular-ui-mvp-wireframes.md`](architecture/operator-ui/angular-ui-mvp-wireframes.md) - low-fidelity wireframes for the first five Angular MVP screens and their operator drill-down flows
- [`architecture/etl-core/csv-to-xml-runtime-flow.md`](architecture/etl-core/csv-to-xml-runtime-flow.md) - operational deep dive for the shipped CSV source to XML target runtime path, including nested XML, hardening hooks, staged publication, and evidence
- [`architecture/etl-core/runtime-flow-walkthrough.html`](architecture/etl-core/runtime-flow-walkthrough.html) - lightweight animated HTML walkthrough of the shipped runtime path with `MainFlow -> SubFlow -> Step` product-flow context
- [`architecture/etl-core/job-level-activation-and-startup-guardrails.md`](architecture/etl-core/job-level-activation-and-startup-guardrails.md) - shipped job-level `isActive` contract and fail-fast startup guardrail for inactive selected jobs
- [`architecture/etl-core/generated-model-naming-standard.md`](architecture/etl-core/generated-model-naming-standard.md) - shipped selected-job naming/package contract, centralized package-resolution direction, and the follow-on internal bridge cleanup that remains after A4 completion
- [`architecture/etl-core/extension-points.md`](architecture/etl-core/extension-points.md) - where new formats, processors, and future capabilities plug in
- [`architecture/etl-core/custom-step-pairing-and-context-handoff.md`](architecture/etl-core/custom-step-pairing-and-context-handoff.md) - future custom-step pairing contract with class skeletons, exception categories, and context handoff policy for customer-owned pre/post steps
- [`architecture/etl-core/customer-owned-processor-transform-seam.md`](architecture/etl-core/customer-owned-processor-transform-seam.md) - future customer-owned processor transform contract with one common transform envelope and Epic D-aligned failure-category mapping
- [`architecture/etl-core/a7-t16-extensibility-charter.md`](architecture/etl-core/a7-t16-extensibility-charter.md) - unified extensibility charter for bounded customer customization through A7 custom steps plus T16 custom transforms
- [`architecture/foundations/architectural-risks-and-watchpoints.md`](architecture/foundations/architectural-risks-and-watchpoints.md) - top architectural risks to watch during roadmap execution
- [`architecture/foundations/etl-product-evolution-roadmap.md`](architecture/foundations/etl-product-evolution-roadmap.md) - current ETL-first phase, future enterprise integration direction, and the high-level guide for what belongs now vs later
- [`architecture/etl-core/file-ingestion-hardening.md`](architecture/etl-core/file-ingestion-hardening.md) - first-slice file-ingestion hardening status plus remaining design direction for validation rules, rejected-record output, and processed-file archiving
- [`architecture/etl-core/oneflow-file-parser-capabilities.md`](architecture/etl-core/oneflow-file-parser-capabilities.md) - parser capability boundary for file sources, including what must stay source-native versus what belongs in processor-side ETL behavior
- [`architecture/etl-core/native-parser-adoptability.md`](architecture/etl-core/native-parser-adoptability.md) - future direction for adopting native parser engines, including C/C++, behind the current Java/Spring Batch reader boundary
- [`architecture/etl-core/csv-native-parser-sidecar-protocol.md`](architecture/etl-core/csv-native-parser-sidecar-protocol.md) - concrete CSV-first sidecar protocol sketch for future native parser adoption while preserving the current Java reader/runtime seam
- [`architecture/etl-core/java-native-parser-reader-adapter-contract.md`](architecture/etl-core/java-native-parser-reader-adapter-contract.md) - Java-side contract for future native-parser reader adapters, including lifecycle mapping, checkpoint persistence, and generated-model handoff rules
- [`architecture/etl-core/file-ingestion-hardening-checklist.md`](architecture/etl-core/file-ingestion-hardening-checklist.md) - execution checklist and remaining follow-on considerations around the first file-ingestion hardening slice
- [`architecture/etl-core/hardening-documentation-sync-checklist.md`](architecture/etl-core/hardening-documentation-sync-checklist.md) - implementation-to-documentation sync note for the explicit-scenario hardening changes
- [`architecture/etl-core/sftp-transport-capability.md`](architecture/etl-core/sftp-transport-capability.md) - near-term SFTP transport direction, deployment boundary, security-layer guidance, and first staged inbound scope
- [`architecture/etl-core/validation-extension-architecture.md`](architecture/etl-core/validation-extension-architecture.md) - future extension architecture for source-level validation and processor-rule validation without reviving the deprecated legacy validation framework
- [`architecture/etl-core/relational-db-support.md`](architecture/etl-core/relational-db-support.md) - current relational support baseline, phase-1 implementation status, and future hardening direction
- [`architecture/etl-core/transformation-capability-roadmap.md`](architecture/etl-core/transformation-capability-roadmap.md) - phased transformation maturity roadmap and the high-level guide for what transformation behavior belongs now vs later
- [`architecture/etl-core/transformation-capability-catalog.md`](architecture/etl-core/transformation-capability-catalog.md) - comprehensive transformation family catalog (now/next/future) with practical examples and backlog-seeding guidance
- [`architecture/etl-core/reference-set-validation-and-enrichment.md`](architecture/etl-core/reference-set-validation-and-enrichment.md) - future processor-side direction for validating mapped values against runtime-loaded database reference sets and later enrichment growth
- [`architecture/etl-core/t6-shared-default-value-mapping-syntax-comparison.md`](architecture/etl-core/t6-shared-default-value-mapping-syntax-comparison.md) - future-only comparison of config-shape options for shared audit/default field mapping under `T6`
- [`architecture/control-plane/job-history-and-operational-observability.md`](architecture/control-plane/job-history-and-operational-observability.md) - current observability baseline plus future direction for retained run history, structured operational events, and diagnostics
- [`architecture/foundations/security-test-strategy.md`](architecture/foundations/security-test-strategy.md) - phased security testing strategy for selected-job guardrails, file/zip hardening, CI security checks, and verification evidence
- [AI-assisted operations intelligence](architecture/operator-ui/ai-assisted-operations-intelligence.md) - future operator-assist roadmap for AI-grounded search and summarization over job history and logs
- [`architecture/foundations/TEMPLATE.md`](architecture/foundations/TEMPLATE.md) - template for future design notes

### ADRs
- [`adr/0001-use-architecture-docs-and-adrs.md`](adr/0001-use-architecture-docs-and-adrs.md)
- [`adr/0002-config-driven-etl-pipeline.md`](adr/0002-config-driven-etl-pipeline.md)
- [`adr/0003-adaptive-step-selection-and-generated-model-contract.md`](adr/0003-adaptive-step-selection-and-generated-model-contract.md)
- [`adr/0004-use-explicit-job-config-for-business-scenario-selection.md`](adr/0004-use-explicit-job-config-for-business-scenario-selection.md)
- [`adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md`](adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md)
- [`adr/0006-separate-source-validation-and-processor-rule-spis.md`](adr/0006-separate-source-validation-and-processor-rule-spis.md)
- [`adr/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md`](adr/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md)
- [`adr/0008-formalize-control-plane-and-etl-worker-boundary.md`](adr/0008-formalize-control-plane-and-etl-worker-boundary.md)
- [`adr/0009-formalize-sqlite-first-local-control-plane-persistence.md`](adr/0009-formalize-sqlite-first-local-control-plane-persistence.md)
- [`adr/0010-keep-native-parsers-behind-java-reader-boundary.md`](adr/0010-keep-native-parsers-behind-java-reader-boundary.md)
- [`adr/0011-enforce-single-default-processor-contract.md`](adr/0011-enforce-single-default-processor-contract.md)
- [`adr/0012-adopt-capability-first-hypercare-evolution.md`](adr/0012-adopt-capability-first-hypercare-evolution.md) - capability-first OneFlow evolution with optional later service extraction
- [`adr/0013-keep-spring-etl-engine-technical-identity-and-oneflow-product-name.md`](adr/0013-keep-spring-etl-engine-technical-identity-and-oneflow-product-name.md) - formal naming split between technical identity and product-facing brand copy
- [`adr/TEMPLATE.md`](adr/TEMPLATE.md) - template for future ADRs

### Configuration references
- [`config/README.md`](config/README.md) - config documentation strategy, support matrix, and scenario usage
- [`config/source/csv-source.md`](config/source/csv-source.md) - CSV source fields supported today
- [`config/source/xml-source.md`](config/source/xml-source.md) - XML source fields supported today
- [`config/source/relational-source.md`](config/source/relational-source.md) - relational source fields and current phase-1 limitations
- [`config/target/csv-target.md`](config/target/csv-target.md) - CSV target fields and current runtime limitations
- [`config/target/xml-target.md`](config/target/xml-target.md) - XML target fields and current runtime behavior
- [`config/target/relational-target.md`](config/target/relational-target.md) - relational target fields and current SQL Server phase-1 limitations
- [`config/processor/default-processor.md`](config/processor/default-processor.md) - default processor mapping contract

### Product tracking
- [`product/product-backlog.md`](product/product-backlog.md) - step-by-step product backlog plus execution-ready board-style tracking from current state to enterprise-grade target, including optional scheduler/control-plane capabilities that grow around the independently runnable ETL core without becoming mandatory for teams that use external orchestration
- **[OneFlow Executive Dashboard](https://github.com/users/kbalanandam/projects/3/views/1)** - live GitHub Project projection of the `Current Execution Board` table in `product/product-backlog.md`, with optional one-way sync for active execution tracking
- [`product/project-board-sync.md`](product/project-board-sync.md) - setup and operating guide for syncing the `Current Execution Board` Markdown table into the GitHub Project without maintaining both views manually
- [`product/epics/README.md`](product/epics/README.md) - epic-level product pages that group shared intent, boundaries, and related architecture links across multiple backlog items while leaving item status fields in the execution board
- [`product/epics/epic-p-source-native-parser-maturity.md`](product/epics/epic-p-source-native-parser-maturity.md) - parser roadmap freeze for CSV/XML-first source-native maturity, preserved-scenario proof, and explicit JSON-later scope
- [`product/backlog-items/README.md`](product/backlog-items/README.md) - complete index and maintenance guide for the per-item drill-down pages linked from execution-board entries
- [`product/github-promotion.md`](product/github-promotion.md) - approved GitHub-facing About text, tagline, topic guidance, and positioning guardrails for OneFlow

### Scenario examples
Representative preserved bundles live under `src/main/resources/config-jobs/`, including:

- `csv-validation-reject-archive/`
- `csv-to-nested-xml/`
- `csv-to-sqlserver/`
- `xml-to-csv-events/`
- `xml-to-json-events/`
- `xml-nested-to-csv-to-nested-xml/`
- `customer-load/`
- `customer-load-zipped/`
- `cust-dept-load/`

Treat `src/main/resources/config-jobs/` as the canonical checked-in preserved-bundle root. Use repo-root [`private-jobs/`](../private-jobs/README.md) only as a developer-local git-ignored workspace for copied private bundles that must not be committed; in normal repository history, only the guidance file there should be visible.

For the canonical maintained scenario inventory and per-bundle notes, use [`config/README.md#scenario-examples`](config/README.md#scenario-examples).

## Maintenance rules

- Prefer Markdown + Mermaid over binary diagrams.
- Keep diagrams focused on one concern.
- Update docs in the same pull request as the code change.
- If runtime flow, config structure, or extension points change, update the relevant doc before merge.
- If the team chooses one design among meaningful alternatives, record it in an ADR.

## Enforcement

The repository enforces this in three layers:

1. pull request checklist questions in `.github/PULL_REQUEST_TEMPLATE.md`
2. ownership and review rules in `.github/CODEOWNERS`
3. CI guardrails in `.github/workflows/architecture-doc-guard.yml`

In addition, pull requests should run the automated Maven test suite through `.github/workflows/pr-unit-tests.yml`, and that status check should also be required in branch protection once it has executed in GitHub at least once.

If architecture-sensitive code changes without a corresponding documentation update, the workflow is expected to fail until a relevant file under `docs/architecture/` or `docs/adr/` is updated.

## Suggested triggers for a new doc or ADR

Create or update docs when a change:

- adds a new config type
- adds a new reader or writer type, or changes the shared default processor contract
- introduces a new runtime path
- changes job/step orchestration
- changes restartability or transaction behavior
- introduces vendor-specific abstractions such as relational dialects
- changes generated model contracts

## Near-term topics already worth documenting

- relational database configuration model
- stored procedure orchestration
- multi-job / multi-step flow design
- validation and error handling flow
- restartability and idempotency rules

## Future-state topics now preserved

- operational observability baseline and job history design
- scenario/job-run logging strategy for operators and future evidence retrieval
- AI-assisted operator diagnostics grounded in retained evidence

The relational database support topic is now started in [`architecture/etl-core/relational-db-support.md`](architecture/etl-core/relational-db-support.md).

The product-direction baseline is now captured in [`architecture/foundations/etl-product-evolution-roadmap.md`](architecture/foundations/etl-product-evolution-roadmap.md). Use it together with the new layer indexes under [`architecture/foundations/README.md`](architecture/foundations/README.md), [`architecture/control-plane/README.md`](architecture/control-plane/README.md), and [`architecture/operator-ui/README.md`](architecture/operator-ui/README.md) to judge whether a proposed change fits the current ETL-first phase or belongs in later scheduler/control-plane/UI work.

The ETL-core-versus-control-plane boundary is now captured in [`architecture/control-plane/control-plane-worker-boundary.md`](architecture/control-plane/control-plane-worker-boundary.md). Use it together with [`architecture/control-plane/scheduler-architecture-direction.md`](architecture/control-plane/scheduler-architecture-direction.md) and [`architecture/operator-ui/operator-ui-architecture-direction.md`](architecture/operator-ui/operator-ui-architecture-direction.md) to keep optional scheduler, watcher, persistence, and UI work from becoming a mandatory runtime dependency or a second launch contract.

The first shipped CSV file-ingestion hardening slice is now captured in [`architecture/etl-core/file-ingestion-hardening.md`](architecture/etl-core/file-ingestion-hardening.md), with broader future expansion still preserved there as architecture guidance.

The near-term SFTP transport direction is now captured in [`architecture/etl-core/sftp-transport-capability.md`](architecture/etl-core/sftp-transport-capability.md). Use it to keep staged inbound/outbound file transport aligned with the ETL-first phase while preserving optional external-MFT and isolated-worker boundaries where clients require them.

The transformation maturity direction that supports enterprise-grade ETL evolution is now captured in [`architecture/etl-core/transformation-capability-roadmap.md`](architecture/etl-core/transformation-capability-roadmap.md). Use it to judge whether a proposed mapping, validation, expression, or enrichment feature fits the current transformation slice or should wait for a later maturity level.

For implementation-time comparison with concrete examples and backlog-seeding candidates, use [`architecture/etl-core/transformation-capability-catalog.md`](architecture/etl-core/transformation-capability-catalog.md).

The execution-facing backlog that translates that direction into concrete steps is now captured in [`product/product-backlog.md`](product/product-backlog.md), including a lightweight board-style view for active priorities, status, and milestone alignment.

GitHub-facing product wording is now centralized in [`product/github-promotion.md`](product/github-promotion.md) so About text, tagline, and topic guidance stay aligned with the current OneFlow scope.

The main architectural watchpoints are captured in [`architecture/foundations/architectural-risks-and-watchpoints.md`](architecture/foundations/architectural-risks-and-watchpoints.md).

The future observability and logging baseline is now captured in [`architecture/control-plane/job-history-and-operational-observability.md`](architecture/control-plane/job-history-and-operational-observability.md). The first runtime slice is now implemented with scenario/job-run MDC fields and daily scenario Logback files in the form `logs/<yyyy-MM-dd>/<scenario>.log`.

Enterprise verification reporting direction is now captured in [`adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md`](adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md), and the current phase-1 implementation already includes a shared evidence model plus categorized Markdown verification reports.

The future AI-assisted operations direction is now captured in [AI-assisted operations intelligence](architecture/operator-ui/ai-assisted-operations-intelligence.md).

