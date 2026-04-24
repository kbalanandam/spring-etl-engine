# Architecture & Design Docs

This folder is the architecture backbone for `spring-etl-engine`.

As features grow, the goal is to keep architectural intent in the repository instead of in chat threads, memory, or slide decks.

## What belongs here

- `architecture/` — feature and system-level design notes
- `adr/` — Architecture Decision Records (why a decision was made)
- `config/` — field-level config references and preserved scenario examples
- `product/` — product vision, backlog, milestones, and execution tracking

## Documentation standard

For every significant enhancement, add or update:

1. one architecture note
2. at least one Mermaid diagram if the runtime/config flow changes
3. one ADR if the change introduces a meaningful design decision or tradeoff

## Current baseline docs

### Architecture
- [`architecture/overview.md`](architecture/overview.md) — current high-level system architecture
- [`architecture/runtime-flow.md`](architecture/runtime-flow.md) — end-to-end ETL runtime flow
- [`architecture/extension-points.md`](architecture/extension-points.md) — where new formats, processors, and future capabilities plug in
- [`architecture/architectural-risks-and-watchpoints.md`](architecture/architectural-risks-and-watchpoints.md) — top architectural risks to watch during roadmap execution
- [`architecture/etl-product-evolution-roadmap.md`](architecture/etl-product-evolution-roadmap.md) — current ETL-first phase and future enterprise integration direction
- [`architecture/relational-db-support.md`](architecture/relational-db-support.md) — proposed architecture for relational database support before implementation
- [`architecture/transformation-capability-roadmap.md`](architecture/transformation-capability-roadmap.md) — phased transformation maturity roadmap from structural mapping toward enterprise-grade ETL behavior
- [`architecture/job-history-and-operational-observability.md`](architecture/job-history-and-operational-observability.md) — future baseline for retained run history, scenario/job-run logging, structured operational events, and diagnostics
- [`architecture/ai-assisted-operations-intelligence.md`](architecture/ai-assisted-operations-intelligence.md) — future operator-assist roadmap for AI-grounded search and summarization over job history and logs
- [`architecture/TEMPLATE.md`](architecture/TEMPLATE.md) — template for future design notes

### ADRs
- [`adr/0001-use-architecture-docs-and-adrs.md`](adr/0001-use-architecture-docs-and-adrs.md)
- [`adr/0002-config-driven-etl-pipeline.md`](adr/0002-config-driven-etl-pipeline.md)
- [`adr/0003-adaptive-step-selection-and-generated-model-contract.md`](adr/0003-adaptive-step-selection-and-generated-model-contract.md)
- [`adr/0004-use-explicit-job-config-for-business-scenario-selection.md`](adr/0004-use-explicit-job-config-for-business-scenario-selection.md)
- [`adr/TEMPLATE.md`](adr/TEMPLATE.md) — template for future ADRs

### Configuration references
- [`config/README.md`](config/README.md) — config documentation strategy, support matrix, and scenario usage
- [`config/source/csv-source.md`](config/source/csv-source.md) — CSV source fields supported today
- [`config/source/relational-source.md`](config/source/relational-source.md) — relational source fields and current phase-1 limitations
- [`config/target/relational-target.md`](config/target/relational-target.md) — relational target fields and current SQL Server phase-1 limitations
- [`config/processor/default-processor.md`](config/processor/default-processor.md) — default processor mapping contract

### Product tracking
- [`product/product-backlog.md`](product/product-backlog.md) — step-by-step product backlog plus execution-ready board-style tracking from current state to enterprise-grade target

### Scenario examples
- `src/main/resources/config-scenarios/csv-to-sqlserver/` — preserved example for CSV source to SQL Server target without changing the default resource YAMLs
- `src/main/resources/config-scenarios/customer-load/` — business scenario for customer-only ETL
- `src/main/resources/config-scenarios/department-load/` — business scenario for department-only ETL
- `src/main/resources/config-scenarios/cust-dept-load/` — business scenario for multi-step customer + department ETL

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

The product-direction baseline is now captured in [`architecture/etl-product-evolution-roadmap.md`](architecture/etl-product-evolution-roadmap.md).

The transformation maturity direction that supports enterprise-grade ETL evolution is now captured in [`architecture/transformation-capability-roadmap.md`](architecture/transformation-capability-roadmap.md).

The execution-facing backlog that translates that direction into concrete steps is now captured in [`product/product-backlog.md`](product/product-backlog.md), including a lightweight board-style view for active priorities, status, and milestone alignment.

The main architectural watchpoints are captured in [`architecture/architectural-risks-and-watchpoints.md`](architecture/architectural-risks-and-watchpoints.md).

The future observability and logging baseline is now captured in [`architecture/job-history-and-operational-observability.md`](architecture/job-history-and-operational-observability.md). The first runtime slice is now implemented with scenario/job-run MDC fields and daily scenario Logback files in the form `logs/<yyyy-MM-dd>/<scenario>.log`.

The future AI-assisted operations direction is now captured in [`architecture/ai-assisted-operations-intelligence.md`](architecture/ai-assisted-operations-intelligence.md).

