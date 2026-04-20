# Architecture & Design Docs

This folder is the architecture backbone for `spring-etl-engine`.

As features grow, the goal is to keep architectural intent in the repository instead of in chat threads, memory, or slide decks.

## What belongs here

- `architecture/` — feature and system-level design notes
- `adr/` — Architecture Decision Records (why a decision was made)

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
- [`architecture/TEMPLATE.md`](architecture/TEMPLATE.md) — template for future design notes

### ADRs
- [`adr/0001-use-architecture-docs-and-adrs.md`](adr/0001-use-architecture-docs-and-adrs.md)
- [`adr/0002-config-driven-etl-pipeline.md`](adr/0002-config-driven-etl-pipeline.md)
- [`adr/0003-adaptive-step-selection-and-generated-model-contract.md`](adr/0003-adaptive-step-selection-and-generated-model-contract.md)
- [`adr/TEMPLATE.md`](adr/TEMPLATE.md) — template for future ADRs

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

