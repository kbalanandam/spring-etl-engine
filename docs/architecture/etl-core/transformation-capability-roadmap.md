# Transformation Capability Roadmap

## Purpose

This document defines what "transformation" should mean in `spring-etl-engine` as the product evolves from a config-driven ETL foundation into an enterprise-grade ETL product.

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
- schema reshaping through `from` -> `to` field mapping
- generated-model-aware transformation flow
- typed ETL contracts that align with source and target definitions
- first shipped rule-based validation/reject behavior for file-backed scenarios through processor rules, controlled rejected-record output, and adjacent archive-on-success behavior, with the strongest preserved proof still centered on CSV

This is best described as:

> config-driven structural transformation with the first shipped rule-based validation slice

That is valid ETL transformation, but it is not yet equal to the breadth of traditional ETL tools.

---

## Transformation Maturity Levels

## Level 1 - Structural transformation foundation

This remains the baseline every supported scenario still builds on.

### Capabilities

- field mapping
- field rename / schema alignment
- source-target-specific mapping blocks
- type-aware source and target models

### Example outcomes

- `Customers.id` -> `CustomersSql.id`
- `Department.name` -> `Departments.name`

### What this level does not yet provide

- expressions
- conditions
- lookups / enrichment
- joins / aggregation
- richer processor-side transform chains for cleanup / normalization

---

## Level 2 - Rule-based transformation

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

## Level 3 - Enrichment and multi-source transformation

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

### Current design vs future refactor (Level 3+)

Use this matrix to decide when the current processor-scoped transform design is still sufficient and when a dedicated transformation layer refactor should be activated.

| Capability area | Current design (processor-scoped transforms) | Future refactor target (separate transformation layer) | Trigger to move now |
|---|---|---|---|
| Field normalization and derived values | Strong fit today through `transforms[]` + `expression` and ordered field resolution | Keep as baseline behavior inside the new layer | No trigger; keep current path |
| Conditional field transformation | Moderate fit; can grow with additional transform types | First-class rule/branching model with reusable transform profiles | Repeated YAML duplication or transform-type sprawl |
| Reuse across scenarios | Limited; reuse is mostly copy/paste across mapping blocks | Shared transform profiles/libraries referenced by many mappings | Same transform chain repeated across many bundles |
| Record-level transformation (multi-field orchestration) | Limited; design is field-centric and depends on field order | Explicit record-level transform stage and context contract | More transforms need whole-record context than single-field context |
| Enrichment and lookup-driven transformation | Early baseline only; currently better aligned to processor rules and targeted extensions | Dedicated enrichment stage with cache/retry/operational controls | Lookup usage grows beyond simple allow-list style checks |
| Source-native adaptation before runtime records exist | Not the default home; intentionally deferred | Separate source-native transform seam before reader output normalization | XPath/namespace/token/header shaping becomes frequent |
| Transformation observability and governance | Basic through existing run/step logs, not transform-stage specific | Stage-specific metrics/evidence, versioned transform policy, lineage-friendly metadata | Operators need transform-stage evidence separate from validation evidence |
| Cross-record semantics (aggregation/window-like behavior) | Weak fit in current per-record mapping path | Dedicated stage/tasklet-style contracts for stateful transformation | Business logic depends on record groups/windows, not single records |

### Refactor guardrails

If a separate transformation layer is introduced, preserve these runtime invariants:

- keep one selected scenario per run and preserve explicit `job-config.yaml` ordered `steps[]` orchestration
- keep transform vs validation ownership clear (`transform` rewrites values; processor `rules` decide accept/reject)
- keep the active execution precedence explicit: source validation -> optional source-native adaptation -> reader -> transformation layer -> processor rules -> write
- avoid introducing scenario auto-discovery or hidden step insertion during migration

### Development comparison examples (current vs future)

Use these examples during implementation planning to decide whether to stay on the current processor-scoped transform path or activate a separate transformation-layer refactor.

#### Example A: value normalization + duplicate reject (stay on current model)

Current model (shipped, preferred for Level 2/early Level 3):

```yaml
# processor-config.yaml
type: default
rejectHandling:
  enabled: true
  outputPath: target/rejects/
mappings:
  - source: Events
    target: EventsCsv
    fields:
      - from: countryCode
        to: countryCode
        transforms:
          - type: valueMap
            mappings:
              USA: US
              IND: IN
            defaultValue: UNKNOWN
      - from: id
        to: id
        rules:
          - type: duplicate
            onFailure: rejectRecord
            keyFields: [id]
```

Why current model is enough here:

- all logic remains field-scoped and record-local
- no reusable cross-scenario transform library is required
- no source-native pre-reader adaptation is required

#### Example B: same transform chain reused across many scenarios (refactor trigger)

Future model (target direction once reuse pressure is high):

```yaml
# job-config.yaml (conceptual future shape)
steps:
  - name: normalize-events
    source: EventsRaw
    transformProfile: common-event-normalization-v1
    target: EventsNormalized
```

```yaml
# transform-profiles.yaml (conceptual future shape)
profiles:
  - name: common-event-normalization-v1
    rules:
      - field: countryCode
        transforms:
          - type: trim
          - type: upperCase
          - type: valueMap
            mappings:
              USA: US
              IND: IN
            defaultValue: UNKNOWN
      - field: eventTime
        transforms:
          - type: parseDateTime
            inputPattern: "MM/dd/yyyy HH:mm:ss"
            outputPattern: "yyyy-MM-dd'T'HH:mm:ss"
```

Why this suggests refactor:

- same transform chain is repeated across many mappings/jobs
- transformation lifecycle (versioning/approval/reuse) becomes a product concern
- transform evidence must be isolated from processor-rule evidence

#### Example C: source-native adaptation before runtime records exist (refactor trigger)

Current model is not ideal when transformation needs XML/XPath/namespace-aware shaping before normal runtime records are emitted. That is a trigger to introduce a source-native transform seam separate from processor transforms.

### Implementation decision checklist (use during backlog grooming)

Keep the current processor-scoped transform model when most answers are `yes`:

- are transforms field-scoped and record-local?
- is `transforms[]` reuse manageable without profile duplication?
- can observability stay at run/step/rule granularity without transform-stage metrics?
- are source-native pre-reader adaptations rare or absent?

Start the separate transformation-layer refactor when two or more answers are `yes`:

- do multiple scenarios duplicate the same transform chains and require centralized versioning?
- do operators need transform-stage evidence separate from validation/reject evidence?
- do transformations frequently require whole-record orchestration rather than single-field transforms?
- do source-native adaptation requirements (XPath/namespace/token/header shaping) become common?
- do enrichment/cache/retry controls exceed the current processor-rule/transform seam?

For active contracts and shipped processor semantics, continue to anchor implementation on:

- [`Default processor config`](../../config/processor/default-processor.md)
- [`Extension points`](extension-points.md)
- [`Runtime flow`](runtime-flow.md)

---

## Level 4 - Enterprise-grade transformation platform behavior

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

## What "Transformation" Should Mean in This Product

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

### Phase 1 alignment - operational ETL foundation

Focus on:

- strong mapping correctness
- explicit source-target transformation pairing
- config validation
- typed structural transformation

### Phase 2 alignment - integration maturity

Focus on:

- continue from the shipped validation and rejected-record-output baseline
- continue from the first configurable field rules such as `notNull`, time-format, and duplicate checks
- first processor-side field-cleaning / normalization transforms for coded values
- optional-by-omission `transforms[]` chains so customers can have zero, one, or many ordered cleaner steps per field
- source-transform YAML only when source-native adaptation is required, not as a parallel default home for generic cleanup
- expression-based mapping
- conditional rules after the shipped expression contract remains stable in normal scenario use
- deferred processor-side default/placeholder mapping for shared audit and operational fields so jobs do not need to repeat the same constant, job-name, or standard timestamp assignment field by field; track under [`T6 - Shared default-value and placeholder mapping`](../../product/backlog-items/etl-core/T6-shared-default-value-and-placeholder-mapping.md)
- lookup/enrichment patterns, starting with runtime-loaded reference-set validation for reject/accept checks before broader enrichment joins

Adjacent file-ingestion hardening such as archiving processed source files should evolve with this phase, but it should remain a file lifecycle capability rather than being treated as a separate transformation maturity level.

### Phase 3 alignment - enterprise mediation platform

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

- [`ETL product evolution roadmap`](../foundations/etl-product-evolution-roadmap.md)
- [`File ingestion hardening`](file-ingestion-hardening.md)
- [`Runtime flow`](runtime-flow.md)
- [`Transformation capability catalog`](transformation-capability-catalog.md)
- [`ADR 0007 - Add separate processor transform SPI for cleaning and normalization`](../../adr/etl-core/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md)
- [`Default processor config`](../../config/processor/default-processor.md)
- [`Reference-set validation and enrichment`](reference-set-validation-and-enrichment.md)
- [`Product backlog`](../../product/product-backlog.md)
- [`T6 - Shared default-value and placeholder mapping`](../../product/backlog-items/etl-core/T6-shared-default-value-and-placeholder-mapping.md)
- [`T6 shared default-value mapping syntax comparison`](t6-shared-default-value-mapping-syntax-comparison.md)



