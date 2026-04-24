# ETL Product Evolution Roadmap

## Purpose

This document captures the intended product direction for `spring-etl-engine` so future design and implementation decisions can be evaluated in the right context.

The product is currently in an ETL-first phase. The near-term goal is to make each supported source and target type operational, reliable, and consistent. The longer-term goal is to evolve the product toward a secure enterprise integration mediation platform.

This note exists to prevent two common problems:

- over-engineering the current phase with future-platform abstractions too early
- making short-term connector decisions that block future enterprise evolution

## Scope

This document covers:

- the current product phase and its priorities
- the future direction toward enterprise integration mediation
- architecture guardrails for current decisions
- a phased roadmap for future capability growth
- a checklist for evaluating future changes against this direction

This document does not define implementation details for any one connector or protocol. Those should be captured in focused design notes such as:

- `docs/architecture/relational-db-support.md`
- future Kafka/API/SFTP notes

## Context

The current architecture is a config-driven ETL engine with:

- polymorphic source and target configurations
- dynamic reader, processor, and writer factories
- Spring Batch-based execution
- generated model contracts
- batch-oriented runtime flow

Today, the product is primarily focused on:

- adding source and target types
- making each connector path operational
- ensuring transformations are reliable, configurable, and able to mature beyond simple field mapping
- keeping the architecture extensible while avoiding unnecessary complexity

The broader product vision is larger than ETL alone. Over time, the product may evolve into a controlled integration abstraction layer between enterprise systems and external third parties.

## Flow

```mermaid
flowchart LR
    A[Current phase: ETL-first] --> B[Operational connectors]
    B --> C[Stable config-driven runtime]
    C --> D[Broader integration capabilities]
    D --> E[Enterprise mediation platform]

    B --> F[CSV / XML / RDBMS / Kafka target]
    C --> G[Docs / ADRs / tests / guardrails]
    D --> H[Canonical models / routing / security]
    E --> I[Partner isolation / governance / observability]
    I --> J[AI-assisted job history and log intelligence]
```

## Current phase: ETL-first product

The current phase should optimize for:

- connector completeness
- correctness
- configuration clarity
- testability
- operational reliability
- extension-friendly design

### What belongs in the current phase

- CSV/XML source and target maturity
- RDBMS source and target support
- Kafka as target
- validation improvements
- stronger transformation capability beyond direct field mapping, introduced in measured steps
- execution-mode controls where justified
- stable reader/processor/writer extension patterns
- stronger tests and documentation

### What should not dominate the current phase

- full enterprise middleware orchestration
- broad policy/routing engines
- multi-tenant integration administration
- full streaming runtime redesign
- partner onboarding workflows and control planes

These are valid future directions, but they should not disrupt the current connector-operational roadmap.

## Future direction: enterprise integration mediation

The longer-term direction is for the product to act as a medium/catalyst between enterprise systems and third parties.

That future direction includes capabilities such as:

- protocol and transport abstraction
- canonicalization between external and internal formats
- richer transformation behavior including rule-based, enrichment-oriented, and governed transformation flows
- secure connector isolation
- controlled exposure of enterprise systems
- routing and orchestration across multiple external parties
- replay, retry, and audit visibility
- stronger operational governance

In that future state, the product becomes more than an ETL tool. It becomes an integration layer that protects core systems from direct and fragmented integration responsibilities.

## Roadmap phases

## Phase 1: Operational ETL foundation

Focus on making the core product reliable and extensible.

### Priorities
- source/target connectors work consistently
- config model stays coherent
- factories remain the primary extension point
- tests and architecture docs grow with the product
- batch execution remains the main runtime mode

### Typical features in this phase
- CSV, XML, relational support
- Kafka as target
- improved validation and execution configuration
- stronger structural transformation, explicit mapping behavior, and transformation-safe orchestration
- better observability of batch flows
- scenario/job-run oriented logging and diagnostic evidence

## Phase 2: Integration maturity

Expand beyond connector completeness into stronger integration capability.

### Priorities
- canonical model discussions begin
- reusable connection and partner configuration patterns emerge
- richer target behaviors and orchestration rules are introduced
- security and audit capabilities become more explicit

### Typical features in this phase
- API/SFTP connectors
- micro-batch Kafka source
- richer database write semantics
- expressions, conditional transformations, validation/reject handling, and lookup/enrichment patterns
- routing and transformation enhancements
- first in-product scheduling/orchestration controls built on explicit run-state, audit, and operator visibility

## Phase 3: Enterprise mediation platform

Move from ETL-first execution toward broader enterprise integration mediation.

### Priorities
- secure boundary between core systems and third parties
- stronger governance, audit, and replay controls
- partner-specific isolation and routing
- possibly both batch and streaming execution modes
- operator-ready observability that supports retained evidence and later AI assistance

### Typical features in this phase
- continuous streaming runtime
- partner/channel policies
- dead-letter and replay support
- security administration and credential governance
- governed transformation definitions, reusable transformation capability, and stronger lineage visibility
- broader northbound/southbound integration design
- AI-assisted search and summarization for job history, run diagnostics, and operational logs
- enterprise-grade scheduling/orchestration controls such as missed-run handling, schedule auditability, and stronger operator-driven trigger policies

### Prerequisites before AI-assisted operations intelligence
- structured job and step history with stable correlation identifiers
- searchable operational events and retained logs with clear error taxonomy
- log redaction, retention controls, and role-based access protections
- operator-facing dashboards and non-AI search/filter workflows already in place
- AI kept as an operator-assist capability, not an autonomous execution or remediation mechanism

## Key Components / Classes

Current architecture anchors that should remain important during roadmap evolution:

- `src/main/java/com/etl/config/ConfigLoader.java`
- `src/main/java/com/etl/config/BatchConfig.java`
- `src/main/java/com/etl/reader/DynamicReaderFactory.java`
- `src/main/java/com/etl/processor/DynamicProcessorFactory.java`
- `src/main/java/com/etl/writer/DynamicWriterFactory.java`
- `src/main/java/com/etl/common/util/GeneratedModelClassResolver.java`

Future phases may add new runtime families or orchestration layers, but they should grow from these extension points rather than bypass them casually.

## Decisions

- The current product phase is ETL-first, not full enterprise middleware yet.
- New features should strengthen the connector/runtime foundation first.
- Future enterprise mediation capabilities are in scope for the product direction, but should be introduced deliberately in later phases.
- Current design decisions should remain future-safe without prematurely forcing middleware-scale abstractions.
- Scheduler/orchestration capability remains part of the main product roadmap at this stage; it should evolve as a focused capability track inside the ETL product rather than through a separate standalone roadmap.

## Tradeoffs

### Benefits
- keeps the near-term roadmap practical and deliverable
- avoids overbuilding before connectors are operational
- still preserves long-term product ambition
- makes future architecture decisions easier to validate retrospectively

### Costs
- some future capabilities will be intentionally deferred
- current architecture may need later expansion for streaming, routing, and security controls
- some “nice future abstractions” should be resisted in the present phase

### Alternatives considered

#### Alternative: build full enterprise mediation platform immediately
Rejected for now because it would likely overload the current phase and slow connector maturity.

#### Alternative: treat the product only as a narrow ETL utility forever
Rejected because the longer-term enterprise integration value is meaningful and should remain visible in architecture planning.

## Architecture guardrails for current decisions

When making current-phase design decisions:

- prefer extension through config subtypes and factories
- keep runtime behavior explicit and testable
- avoid connector-specific shortcuts that leak across the architecture
- avoid introducing broad platform abstractions before there is a real operational need
- document future-facing decisions if they materially affect roadmap direction
- keep batch-first assumptions unless a change explicitly requires a new execution model
- defer AI log intelligence until observability, audit, replay, and security controls are already mature
- prefer structured run metadata and searchable events first so any future semantic search is grounded in evidence

## Impact on Existing Architecture

This roadmap note does not change runtime code directly, but it affects how future changes should be judged.

In particular, it should influence decisions around:

- whether a feature belongs in the current ETL phase or a later platform phase
- whether a design is appropriately scoped for the current product maturity
- whether a new abstraction is justified now or should be deferred
- whether a connector proposal remains aligned with the config-driven extension model

## Testing / Validation Expectations

Future changes should be validated against both technical quality and roadmap fit.

### Technical validation
- tests for new connector/runtime behavior
- config binding tests
- factory routing tests
- integration tests where appropriate
- documentation updates for architecture-impacting changes

### Roadmap-fit validation
When proposing a new feature, ask:

- Does this primarily strengthen connector operability in the ETL-first phase?
- Does it preserve the current architecture’s extension model?
- Is it introducing future platform complexity too early?
- If it is future-facing, does it still avoid blocking current delivery?

## Retrospective evaluation checklist

When reviewing a future design or implementation, use this checklist:

- Is this change aligned with the current ETL-first phase, or is it pulling platform concerns in too early?
- Does the change improve operational connector capability or reliability?
- Does it preserve factory-driven extensibility?
- Does it leave room for future enterprise mediation without forcing it now?
- Does it increase coupling between enterprise systems and partner-specific logic unnecessarily?
- If the change reflects a real shift in product direction, has a design note or ADR been updated?

## Related architecture notes

This roadmap should be read together with:

- `docs/architecture/overview.md`
- `docs/architecture/runtime-flow.md`
- `docs/architecture/extension-points.md`
- `docs/architecture/relational-db-support.md`
- `docs/architecture/transformation-capability-roadmap.md`

## Future Extensions

Likely future notes that should build from this roadmap include:

- Kafka target support
- Kafka source and streaming support
- enterprise mediation architecture
- canonical model strategy
- security boundary and partner isolation
- orchestration and retry/replay model
- job history and operational observability architecture
- AI-assisted log search and diagnostics for operators
- scenario and job-run logging strategy
- transformation capability maturity across mapping, rules, enrichment, and governed enterprise behavior

