# Architecture & Design Docs

<img src="assets/github-social-preview-tagline.svg" alt="OneFlow social preview" width="1100" />

This folder is the architecture backbone for `spring-etl-engine`, presented as part of the **OneFlow** product-facing documentation experience.

For GitHub-facing product language, **OneFlow** should be presented as a focused, config-driven runtime for repeatable file-based and integration-oriented flows, not as a traditional ETL suite in the style of Informatica or SSIS.

As features grow, the goal is to keep architectural intent in the repository instead of in chat threads, memory, or slide decks.

## What belongs here

- `architecture/` — feature and system-level design notes
- `adr/` — Architecture Decision Records (why a decision was made)
- `config/` — field-level config references and preserved scenario examples
- `product/` — product vision, backlog, milestones, and execution tracking

## Core terms

Use these short definitions as the shared vocabulary for the rest of the docs:

- **scenario bundle** — one runnable config folder for one use case. The checked-in bundle root is `src/main/resources/config-jobs/`, while legacy `config-scenarios/...` paths remain backward-compatible aliases.
- **`job-config.yaml`** — the run entry point that selects config files and ordered steps for one scenario; see [`config/job-config.md`](config/job-config.md)
- **main flow** — the top-level reusable business flow executed inside one selected scenario; see [`architecture/hierarchical-flow-composition.md`](architecture/hierarchical-flow-composition.md)
- **subflow** — a reusable grouped phase inside one main flow, containing one or more ordered steps; see [`architecture/hierarchical-flow-composition.md`](architecture/hierarchical-flow-composition.md)
- **step** — one ordered `source -> processor -> target` unit inside a selected run
- **source config** — the YAML contract that describes where records come from; see [`config/README.md`](config/README.md)
- **target config** — the YAML contract that describes where records go; see [`config/README.md`](config/README.md)
- **processor mapping** — the field-level contract that maps data from the selected source to the selected target; see [`config/processor/default-processor.md`](config/processor/default-processor.md)
- **format** — the connector type selected in config, such as `csv`, `xml`, or `relational`
- **factory** — a runtime component that creates the correct reader, writer, or processor implementation from the selected config type
- **resolver** — a runtime component that selects the correct metadata or implementation for the current step, such as model classes or relational vendor behavior
- **database dialect** — the relational abstraction that keeps vendor-specific SQL behavior behind one `relational` format instead of creating vendor-per-format modeling

## How to navigate these docs

Start with the path that matches your goal:

| If you want to... | Start here | Then go to |
|---|---|---|
| run or configure one scenario | [`config/README.md`](config/README.md) | [`config/job-config.md`](config/job-config.md) and one preserved job/scenario bundle |
| understand the shipped runtime flow | [`architecture/runtime-flow.md`](architecture/runtime-flow.md) | [`architecture/runtime-flow-walkthrough.html`](architecture/runtime-flow-walkthrough.html) for the hierarchy-aware product-flow walkthrough, and [`architecture/overview.md`](architecture/overview.md) |
| understand the next runtime architecture target | [`architecture/scenario-driven-runtime-direction.md`](architecture/scenario-driven-runtime-direction.md) | [`architecture/1-4-to-next-architecture-classification.md`](architecture/1-4-to-next-architecture-classification.md) |
| understand main flow / subflow composition | [`architecture/hierarchical-flow-composition.md`](architecture/hierarchical-flow-composition.md) | [`architecture/scenario-driven-runtime-direction.md`](architecture/scenario-driven-runtime-direction.md) and [`config/job-config.md`](config/job-config.md) |
| understand how simple and complex flows normalize into one model | [`architecture/flow-normalization-rules.md`](architecture/flow-normalization-rules.md) | [`architecture/hierarchical-flow-composition.md`](architecture/hierarchical-flow-composition.md) and [`config/job-config.md`](config/job-config.md) |
| assess the gap from shipped runtime to the reusable scenario model | [`architecture/runtime-to-scenario-gap-assessment.md`](architecture/runtime-to-scenario-gap-assessment.md) | [`architecture/scenario-driven-runtime-direction.md`](architecture/scenario-driven-runtime-direction.md) and [`architecture/hierarchical-flow-composition.md`](architecture/hierarchical-flow-composition.md) |
| understand validation, transforms, or extension seams | [`architecture/extension-points.md`](architecture/extension-points.md) | [`config/processor/default-processor.md`](config/processor/default-processor.md) |
| understand relational support | [`architecture/relational-db-support.md`](architecture/relational-db-support.md) | [`config/source/relational-source.md`](config/source/relational-source.md) and [`config/target/relational-target.md`](config/target/relational-target.md) |
| see what is planned next | [`product/product-backlog.md`](product/product-backlog.md) | [`architecture/etl-product-evolution-roadmap.md`](architecture/etl-product-evolution-roadmap.md) |

## Status legend

Use these labels while reading:

- **Shipped** — part of the active runtime path today
- **Current baseline + future evolution** — starts from the shipped baseline and also preserves the intended next direction
- **Future direction** — design guidance, not a shipped runtime path today
- **Deprecated** — retained temporarily for cleanup or migration and not part of the active contract

## Documentation standard

For every significant enhancement, add or update:

1. one architecture note
2. at least one Mermaid diagram if the runtime/config flow changes
3. one ADR if the change introduces a meaningful design decision or tradeoff

## Current baseline docs

### Architecture
- [`architecture/overview.md`](architecture/overview.md) — current high-level system architecture
- [`architecture/scenario-driven-runtime-direction.md`](architecture/scenario-driven-runtime-direction.md) — target next-direction runtime contract for strict scenario-driven execution without compromising future scale, UI views, or richer transformations
- [`architecture/hierarchical-flow-composition.md`](architecture/hierarchical-flow-composition.md) — frozen working direction for reusable main flows composed from reusable subflows and executable steps under one selected scenario
- [`architecture/flow-normalization-rules.md`](architecture/flow-normalization-rules.md) — normalization rules for simple and complex flows, including optional or implicit subflow handling in the first slice
- [`architecture/runtime-to-scenario-gap-assessment.md`](architecture/runtime-to-scenario-gap-assessment.md) — current-state versus target-state gap assessment for reusable components evolving into scenario-driven execution
- [`architecture/1-4-to-next-architecture-classification.md`](architecture/1-4-to-next-architecture-classification.md) — transition map for classifying current 1.4.x code into reuse, bridge, legacy, and remove buckets during the next architecture shift
- [`architecture/runtime-flow.md`](architecture/runtime-flow.md) — end-to-end ETL runtime flow
- [`architecture/runtime-flow-walkthrough.html`](architecture/runtime-flow-walkthrough.html) — lightweight animated HTML walkthrough of the shipped runtime path with `MainFlow -> SubFlow -> Step` product-flow context
- [`architecture/extension-points.md`](architecture/extension-points.md) — where new formats, processors, and future capabilities plug in
- [`architecture/architectural-risks-and-watchpoints.md`](architecture/architectural-risks-and-watchpoints.md) — top architectural risks to watch during roadmap execution
- [`architecture/etl-product-evolution-roadmap.md`](architecture/etl-product-evolution-roadmap.md) — current ETL-first phase, future enterprise integration direction, and the high-level guide for what belongs now vs later
- [`architecture/file-ingestion-hardening.md`](architecture/file-ingestion-hardening.md) — first-slice file-ingestion hardening status plus remaining design direction for validation rules, rejected-record output, and processed-file archiving
- [`architecture/file-ingestion-hardening-checklist.md`](architecture/file-ingestion-hardening-checklist.md) — execution checklist and remaining follow-on considerations around the first file-ingestion hardening slice
- [`architecture/hardening-documentation-sync-checklist.md`](architecture/hardening-documentation-sync-checklist.md) — implementation-to-documentation sync note for the explicit-scenario hardening changes
- [`architecture/sftp-transport-capability.md`](architecture/sftp-transport-capability.md) — near-term SFTP transport direction, deployment boundary, security-layer guidance, and first staged inbound scope
- [`architecture/validation-extension-architecture.md`](architecture/validation-extension-architecture.md) — future extension architecture for source-level validation and processor-rule validation without reviving the deprecated legacy validation framework
- [`architecture/relational-db-support.md`](architecture/relational-db-support.md) — current relational support baseline, phase-1 implementation status, and future hardening direction
- [`architecture/transformation-capability-roadmap.md`](architecture/transformation-capability-roadmap.md) — phased transformation maturity roadmap and the high-level guide for what transformation behavior belongs now vs later
- [`architecture/job-history-and-operational-observability.md`](architecture/job-history-and-operational-observability.md) — current observability baseline plus future direction for retained run history, structured operational events, and diagnostics
- [`architecture/ai-assisted-operations-intelligence.md`](architecture/ai-assisted-operations-intelligence.md) — future operator-assist roadmap for AI-grounded search and summarization over job history and logs
- [`architecture/TEMPLATE.md`](architecture/TEMPLATE.md) — template for future design notes

### ADRs
- [`adr/0001-use-architecture-docs-and-adrs.md`](adr/0001-use-architecture-docs-and-adrs.md)
- [`adr/0002-config-driven-etl-pipeline.md`](adr/0002-config-driven-etl-pipeline.md)
- [`adr/0003-adaptive-step-selection-and-generated-model-contract.md`](adr/0003-adaptive-step-selection-and-generated-model-contract.md)
- [`adr/0004-use-explicit-job-config-for-business-scenario-selection.md`](adr/0004-use-explicit-job-config-for-business-scenario-selection.md)
- [`adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md`](adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md)
- [`adr/0006-separate-source-validation-and-processor-rule-spis.md`](adr/0006-separate-source-validation-and-processor-rule-spis.md)
- [`adr/TEMPLATE.md`](adr/TEMPLATE.md) — template for future ADRs

### Configuration references
- [`config/README.md`](config/README.md) — config documentation strategy, support matrix, and scenario usage
- [`config/source/csv-source.md`](config/source/csv-source.md) — CSV source fields supported today
- [`config/source/xml-source.md`](config/source/xml-source.md) — XML source fields supported today
- [`config/source/relational-source.md`](config/source/relational-source.md) — relational source fields and current phase-1 limitations
- [`config/target/csv-target.md`](config/target/csv-target.md) — CSV target fields and current runtime limitations
- [`config/target/xml-target.md`](config/target/xml-target.md) — XML target fields and current runtime behavior
- [`config/target/relational-target.md`](config/target/relational-target.md) — relational target fields and current SQL Server phase-1 limitations
- [`config/processor/default-processor.md`](config/processor/default-processor.md) — default processor mapping contract

### Product tracking
- [`product/product-backlog.md`](product/product-backlog.md) — step-by-step product backlog plus execution-ready board-style tracking from current state to enterprise-grade target, including scheduler/orchestration work inside the same single product roadmap
- [`product/github-promotion.md`](product/github-promotion.md) — approved GitHub-facing About text, tagline, topic guidance, and positioning guardrails for OneFlow

### Scenario examples
- `src/main/resources/config-jobs/csv-validation-reject-archive/` — preferred entry path for the preserved CSV validation, rejected-record output, and archive-on-success slice
- `src/main/resources/config-jobs/csv-to-nested-xml/` — preferred entry path for the preserved explicit job proving CSV source mapping into a nested XML target through a generated target model definition
- `src/main/resources/config-jobs/csv-to-sqlserver/` — preferred entry path for the preserved CSV source to SQL Server target example without changing the default resource YAMLs
- `src/main/resources/config-jobs/xml-to-csv-events/` — preferred entry path for the realistic flat XML source to CSV target baseline run
- `src/main/resources/config-jobs/xml-nested-to-csv-tag-validation/` — preferred entry path for the nested XML source flattening example using the shared nested XML path
- `src/main/resources/config-jobs/xml-nested-tag-validation/` — preferred entry path for the nested XML source to generated XML target example
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/` — preferred entry path for the preserved explicit multi-step roundtrip example
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml-archive-e2e/` — preferred entry path for the preserved roundtrip example with XML archive-on-success
- `src/main/resources/config-jobs/customer-load/` — preferred entry path for the customer-only ETL example
- `src/main/resources/config-jobs/department-load/` — preferred entry path for the department-only ETL example
- `src/main/resources/config-jobs/cust-dept-load/` — preferred entry path for the multi-step customer + department ETL example

Those `config-jobs` paths now point at the checked-in preserved bundles directly. Older `config-scenarios/...` references remain supported as compatibility aliases.

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
- adds a new reader, writer, or processor type
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

The relational database support topic is now started in [`architecture/relational-db-support.md`](architecture/relational-db-support.md).

The product-direction baseline is now captured in [`architecture/etl-product-evolution-roadmap.md`](architecture/etl-product-evolution-roadmap.md). Use it to judge whether a proposed change fits the current ETL-first phase or should wait for a later platform phase.

The first shipped CSV file-ingestion hardening slice is now captured in [`architecture/file-ingestion-hardening.md`](architecture/file-ingestion-hardening.md), with broader future expansion still preserved there as architecture guidance.

The near-term SFTP transport direction is now captured in [`architecture/sftp-transport-capability.md`](architecture/sftp-transport-capability.md). Use it to keep staged inbound/outbound file transport aligned with the ETL-first phase while preserving optional external-MFT and isolated-worker boundaries where clients require them.

The transformation maturity direction that supports enterprise-grade ETL evolution is now captured in [`architecture/transformation-capability-roadmap.md`](architecture/transformation-capability-roadmap.md). Use it to judge whether a proposed mapping, validation, expression, or enrichment feature fits the current transformation slice or should wait for a later maturity level.

The execution-facing backlog that translates that direction into concrete steps is now captured in [`product/product-backlog.md`](product/product-backlog.md), including a lightweight board-style view for active priorities, status, and milestone alignment.

GitHub-facing product wording is now centralized in [`product/github-promotion.md`](product/github-promotion.md) so About text, tagline, and topic guidance stay aligned with the current OneFlow scope.

The main architectural watchpoints are captured in [`architecture/architectural-risks-and-watchpoints.md`](architecture/architectural-risks-and-watchpoints.md).

The future observability and logging baseline is now captured in [`architecture/job-history-and-operational-observability.md`](architecture/job-history-and-operational-observability.md). The first runtime slice is now implemented with scenario/job-run MDC fields and daily scenario Logback files in the form `logs/<yyyy-MM-dd>/<scenario>.log`.

Enterprise verification reporting direction is now captured in [`adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md`](adr/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md), and the current phase-1 implementation already includes a shared evidence model plus categorized Markdown verification reports.

The future AI-assisted operations direction is now captured in [`architecture/ai-assisted-operations-intelligence.md`](architecture/ai-assisted-operations-intelligence.md).

