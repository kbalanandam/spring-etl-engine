# Transformation Capability Roadmap

## Purpose

This document defines what “transformation” should mean in `spring-etl-engine` as the product evolves from a config-driven ETL foundation into an enterprise-grade ETL product.

It exists to prevent two common mistakes:

- underestimating the current product because transformation is only associated with classic ETL suites
- overpromising enterprise-grade transformation before the runtime, config model, and operator controls are ready

---

## Current State

The product already supports real transformation, but at an early maturity level.

### Supported today

- source-to-target field mapping
- source-specific and target-specific mapping selection
- schema reshaping through `from` → `to` field mapping
- generated-model-aware transformation flow
- typed ETL contracts that align with source and target definitions

This is best described as:

> config-driven structural transformation

That is valid ETL transformation, but it is not yet equal to the breadth of traditional ETL tools.

---

## Transformation Maturity Levels

## Level 1 — Structural transformation foundation

This is the current state.

### Capabilities

- field mapping
- field rename / schema alignment
- source-target-specific mapping blocks
- type-aware source and target models

### Example outcomes

- `Customers.id` → `CustomersSql.id`
- `Department.name` → `Departments.name`

### What this level does not yet provide

- expressions
- conditions
- validation/reject flow
- lookups / enrichment
- joins / aggregation

---

## Level 2 — Rule-based transformation

This is the next practical maturity target.

### Capabilities

- derived fields through expressions
- conditional mapping rules
- normalization / standardization rules
- validation with explicit pass/fail behavior
- reject or quarantine handling for invalid records

### Example outcomes

- `fullName = firstName + ' ' + lastName`
- `customerType = 'ENTERPRISE' when revenue > threshold`
- invalid date or required-field failures routed to controlled reject output

### Product value

This is the level where the product starts to feel more like a traditional ETL tool rather than only a dynamic mapper.

---

## Level 3 — Enrichment and multi-source transformation

This is the medium-term enterprise transformation target.

### Capabilities

- lookup-driven enrichment
- reference-data joins
- transformation reuse across scenarios
- richer multi-step transformation pipelines
- stronger scenario-level validation and quality controls

### Example outcomes

- enrich incoming records using a relational reference table
- resolve code-to-description mappings during processing
- apply one reusable transformation rule-set to multiple scenarios

### Product value

This level enables the product to support more realistic enterprise data movement and business-rule execution.

---

## Level 4 — Enterprise-grade transformation platform behavior

This is the longer-term enterprise-grade target.

### Capabilities

- reusable transformation libraries
- aggregation and summarization support where justified
- advanced lineage and traceability for transformation logic
- governed transformation definitions and version traceability
- operator-visible transformation outcomes and reject evidence

### Example outcomes

- controlled transformation rule versioning per run
- auditable record rejection and remediation workflow
- richer enterprise reporting on what was transformed, rejected, and why

### Product value

This level supports enterprise-grade ETL expectations around control, governance, and operational trust.

---

## What “Transformation” Should Mean in This Product

For `spring-etl-engine`, transformation should eventually include:

- mapping and schema reshaping
- type-aware normalization
- derived and conditional fields
- validation and reject handling
- enrichment from reference data
- auditable and diagnosable transformation outcomes

It should not immediately mean:

- recreating every feature of legacy ETL suites at once
- introducing a broad expression platform before the config model is stable
- adding joins, aggregation, and reusable transformation libraries before basic orchestration and operator visibility are mature

---

## Roadmap Alignment

Transformation capability should evolve with the broader ETL roadmap, not separately from it.

### Phase 1 alignment — operational ETL foundation

Focus on:

- strong mapping correctness
- explicit source-target transformation pairing
- config validation
- typed structural transformation

### Phase 2 alignment — integration maturity

Focus on:

- expressions
- conditions
- validation/reject handling
- lookup/enrichment patterns

### Phase 3 alignment — enterprise mediation platform

Focus on:

- governed transformation definitions
- reusable transformation capabilities
- advanced audit, lineage, and operator support

---

## Architecture Guardrails

When adding transformation features:

- prefer explicit config contracts over hidden code-only behavior
- keep transformation selection aligned with scenario and source-target identity
- avoid bypassing the processor abstraction casually
- do not introduce broad transformation languages without clear operational need
- ensure failures are observable and testable
- treat reject handling and run evidence as part of transformation maturity, not as afterthoughts

---

## Near-Term Priorities

The next meaningful transformation priorities are:

1. explicit transformation orchestration instead of positional assumptions
2. derived fields / expression support
3. conditional transformation rules
4. validation and reject handling model
5. lookup/enrichment design baseline

---

## Related Notes

- `docs/architecture/etl-product-evolution-roadmap.md`
- `docs/architecture/runtime-flow.md`
- `docs/config/processor/default-processor.md`
- `docs/product/product-backlog.md`

