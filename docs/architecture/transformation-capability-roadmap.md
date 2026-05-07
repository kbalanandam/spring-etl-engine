# Transformation Capability Roadmap

## Purpose

This document defines what “transformation” should mean in `spring-etl-engine` as the product evolves from a config-driven ETL foundation into an enterprise-grade ETL product.

Use this note to answer three questions before expanding transformation behavior: what transformation means at the current product stage, which transformation features belong in the next slice, and which capabilities should wait until the runtime and operator model are stronger. It is a transformation-maturity guide, not the execution backlog and not a field-by-field processor config reference.

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
- first shipped rule-based validation/reject behavior for file-backed scenarios through processor rules, controlled rejected-record output, and adjacent archive-on-success behavior, with the strongest preserved proof still centered on CSV

This is best described as:

> config-driven structural transformation with the first shipped rule-based validation slice

That is valid ETL transformation, but it is not yet equal to the breadth of traditional ETL tools.

---

## Transformation Maturity Levels

## Level 1 — Structural transformation foundation

This remains the baseline every supported scenario still builds on.

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
- lookups / enrichment
- joins / aggregation
- richer processor-side transform chains for cleanup / normalization

---

## Level 2 — Rule-based transformation

This is the active maturity track.

The first implementation slices at this level are already shipped on the active runtime path: file-based validation rules, explicit rejected-record output, processor-side value cleanup through ordered `transforms[]`, and expression-based derived fields through the same processor transform seam. The next work inside this level is conditional transformation behavior on top of that shipped baseline.

That shipped slice is already proven by preserved realistic file scenarios that show accepted records, rejected records, and archived-original-file behavior together.

### Capabilities

- validation with explicit pass/fail behavior
- controlled rejected-record output for invalid records, with broader quarantine workflows deferred
- processor-side config-driven field cleaning / normalization steps as the first transform slice
- future source-native transform/adaptation only when required by source structure or pre-flattening semantics
- value mapping for coded fields
- expression-based mapping for derived fields
- future conditional mapping rules
- normalization / standardization rules

### Example outcomes

- `eventTime` must match `HH:mm:ss` or the record is routed to reject output
- required-field failures such as missing customer identifiers are routed to controlled reject output
- `status = 'Success' when statusCode='1', 'Fail' when statusCode='2', else 'Unknown'`
- `countryCode: USA -> US, IND -> IN`
- `fullName = firstName + ' ' + lastName`
- `customerType = 'ENTERPRISE' when revenue > threshold`
- invalid date or required-field failures routed to controlled reject output

### Product value

This is the level where the product starts to feel more like a traditional ETL tool rather than only a dynamic mapper.

The intended order inside this level is:

1. file-based validation rules plus rejected-record output
2. processed-file archive behavior as adjacent ingestion hardening
3. discrete processor cleaner / normalization transforms such as value maps and standardization rules
4. expression-based mapping for derived fields
5. conditional transformation rules

The intended runtime precedence for that transform slice should stay explicit:

1. source validation
2. optional source-native transforms when a real source-specific need exists
3. reader emits a normal runtime record
4. processor transforms
5. processor rules
6. write accepted output / rejected-record output

That means transform-then-reject is valid by design. A value may be normalized first and then rejected if the transformed result is still not acceptable for the selected target/business contract.

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
- validation and rejected-record output
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

- continue from the shipped validation and rejected-record-output baseline
- continue from the first configurable field rules such as `notNull`, time-format, and duplicate checks
- first processor-side field-cleaning / normalization transforms for coded values
- optional-by-omission `transforms[]` chains so customers can have zero, one, or many ordered cleaner steps per field
- source-transform YAML only when source-native adaptation is required, not as a parallel default home for generic cleanup
- expression-based mapping
- conditional rules after the shipped expression contract remains stable in normal scenario use
- lookup/enrichment patterns

Adjacent file-ingestion hardening such as archiving processed source files should evolve with this phase, but it should remain a file lifecycle capability rather than being treated as a separate transformation maturity level.

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
- treat rejected-record output and run evidence as part of transformation maturity, not as afterthoughts
- default generic business/value cleanup to processor transforms rather than source-specific YAML
- reserve source transforms for source-native concerns such as XPath, namespaces, raw header/token cleanup, or other pre-flattening/source-shape adaptation
- fail fast or at least warn when equivalent generic value rewriting is configured for the same field in both source and processor layers

---

## Near-Term Priorities

The next meaningful transformation priorities are:

1. conditional transformation rules on top of the shipped transform + expression baseline
2. preserved realistic file-scenario proof for accepted, rejected, cleaned, derived-field, and archived-original-file behavior
3. lookup/enrichment design baseline

---

## Related Notes

- `docs/architecture/etl-product-evolution-roadmap.md`
- `docs/architecture/file-ingestion-hardening.md`
- `docs/architecture/runtime-flow.md`
- `docs/adr/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md`
- `docs/config/processor/default-processor.md`
- `docs/product/product-backlog.md`

