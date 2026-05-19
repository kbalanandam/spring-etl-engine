# Transformation Capability Catalog

## Purpose

This catalog lists practical transformation families for `spring-etl-engine`, grouped by maturity:

- what is available now on the shipped path
- what should come next without a major runtime refactor
- what requires a future separate transformation-layer expansion

Use this file as a development companion to:

- [`transformation-capability-roadmap.md`](transformation-capability-roadmap.md)
- [`default-processor.md`](../config/processor/default-processor.md)
- [`product-backlog.md`](../product/product-backlog.md)

## Current runtime contract to preserve

The active contract remains:

1. source validation
2. optional source-native adaptation (future seam)
3. reader emits runtime record
4. processor transforms
5. processor rules
6. write accepted output and rejected-record output

Do not change explicit ordered `job-config.yaml` steps just to model field-level transform chains.

## Status legend

- `Shipped` - available on the active path now
- `Next` - feasible on current design with moderate extension
- `Future` - expected to need broader refactor/new stage semantics

## Transformation families (current and future)

| Family | Status | Preferred layer | Typical examples |
|---|---|---|---|
| Field rename/remap | Shipped | Processor mapping | `from: firstName -> to: givenName` |
| Value mapping (code decode/normalize) | Shipped | Processor transforms | `USA -> US`, `1 -> Success` |
| Expression-derived fields | Shipped | Processor transforms | `fullName = firstName + ' ' + lastName` |
| Ordered multi-transform chain per field | Shipped | Processor transforms | `trim -> upperCase -> valueMap` |
| Required-field validation | Shipped | Processor rules | `notNull` |
| Format validation | Shipped | Processor rules | `timeFormat` |
| Duplicate checks and winner selection | Shipped | Processor rules + duplicate resolver | keep-first or `orderBy` winner selection |
| Reject routing with reason metadata | Shipped | Processor rules + reject handling | `_rejectField`, `_rejectRule`, `_rejectMessage` |
| Conditional transform (`if/else`) | Next | Processor transforms | map values by condition |
| Common string normalization helpers | Next | Processor transforms | `trim`, `collapseSpaces`, `regexReplace` |
| Numeric and date utility transforms | Next | Processor transforms | parse/format, scale/round |
| Shared default/placeholder assignment | Next | Processor transforms | audit defaults, job constants |
| Reusable transform profiles across mappings | Next/Future boundary | Separate transform profile concept | one profile used by many mappings |
| Lookup-based enrichment (reference set) | Next | Processor enrichment seam | code to description from DB/reference table |
| Source-native structural adaptation | Future | Source-native transform seam | XPath/namespace/header/token shaping |
| Record-level orchestration transforms | Future | Separate transformation stage | one transform reads/writes multiple fields coherently |
| Cross-record transforms (group/window/aggregate) | Future | Separate stateful stage/tasklet pattern | aggregate totals, sequence logic |
| Governance/versioned transform packs | Future | Separate transformation layer + governance | versioned profile rollout |
| Transform-stage observability and lineage | Future | Separate transformation layer | stage-level metrics/evidence |
| PII masking/tokenization transforms | Future | Dedicated secure transform policy seam | redact/hash/tokenize fields |

## Example patterns for development comparison

### 1) Shipped pattern: field-level transform + rule evaluation

```yaml
# processor-config.yaml
# Uses active shipped contract: transforms before rules
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
      - from: eventTime
        to: eventTime
        rules:
          - type: timeFormat
            pattern: HH:mm:ss
```

### 2) Next pattern: conditional transform on current design

```yaml
# conceptual extension on current processor transform seam
mappings:
  - source: Orders
    target: OrdersCsv
    fields:
      - from: amount
        to: customerTier
        transforms:
          - type: conditional
            cases:
              - when: "#value >= 10000"
                then: "ENTERPRISE"
              - when: "#value >= 1000"
                then: "MID"
            defaultValue: "SMB"
```

### 3) Future pattern: reusable transformation profile

```yaml
# conceptual future shape for profile reuse
# job-config.yaml
steps:
  - name: normalize-orders
    source: OrdersRaw
    transformProfile: common-order-normalization-v2
    target: OrdersNormalized
```

```yaml
# conceptual future shape for transform profiles
profiles:
  - name: common-order-normalization-v2
    rules:
      - field: orderDate
        transforms:
          - type: parseDateTime
            inputPattern: "MM/dd/yyyy"
            outputPattern: "yyyy-MM-dd"
      - field: currency
        transforms:
          - type: trim
          - type: upperCase
```

### 4) Future pattern: source-native adaptation before processor transforms

```yaml
# conceptual future source-native transform seam
sources:
  - format: xml
    sourceName: PartnerFeed
    filePath: input/partner.xml
    sourceTransforms:
      - type: xpathNormalize
        rules:
          - xpath: "/Envelope/Body/Order/Code"
            valueMap:
              "01": "NEW"
              "02": "UPDATE"
```

## Backlog seeding map (major transformation developments)

This map helps convert transformation growth into backlog items. Existing `T*` links are current anchors. `Candidate` IDs are proposed placeholders for future backlog authoring.

| Major development | Current anchor | Candidate next item | Why it matters |
|---|---|---|---|
| Baseline rule/reject behavior | [`T1`](../product/backlog-items/T1-field-level-validation-and-first-reject-handling-slice.md) | n/a | Shipped baseline for validation and reject path |
| Transform SPI and normalization chain | [`T1a`](../product/backlog-items/T1a-processor-transform-spi-and-first-cleaner-normalization-slice.md) | n/a | Shipped transform extension seam |
| Expression-derived fields | [`T2`](../product/backlog-items/T2-expression-based-derived-field-support.md) | n/a | Shipped derived field capability |
| Conditional transforms/rules | [`T3`](../product/backlog-items/T3-conditional-transformation-rule-support.md) | n/a | Next major transform capability gap |
| Duplicate hardening and scope boundaries | [`T4`](../product/backlog-items/T4-transformation-quarantine-and-duplicate-hardening.md) | n/a | Stabilizes duplicate and quarantine behavior |
| Lookup/enrichment baseline | [`T5`](../product/backlog-items/T5-reference-set-validation-and-enrichment-baseline.md) | n/a | Introduces reference-data driven transformation |
| Shared defaults/placeholders | [`T6`](../product/backlog-items/T6-shared-default-value-and-placeholder-mapping.md) | n/a | Reduces repeated mapping logic |
| Duplicate scalability redesign | [`T7`](../product/backlog-items/T7-duplicate-tracking-scalability-redesign-deferment.md) | n/a | Keeps large-state dedupe work explicit |
| Reusable transform profiles | [`T8`](../product/backlog-items/T8-reusable-transform-profiles-and-versioning.md) | n/a | Reuse and versioning across scenarios |
| Source-native transform seam | [`T9`](../product/backlog-items/T9-source-native-transformation-seam.md) | n/a | Needed for XPath/namespace/token adaptation |
| Record-level transformation stage | [`T10`](../product/backlog-items/T10-record-level-transformation-stage.md) | n/a | Needed when field-centric model is insufficient |
| Cross-record/window transformation | [`T11`](../product/backlog-items/T11-cross-record-window-and-aggregation-transforms.md) | n/a | Needed for aggregation/group semantics |
| Transformation governance and lineage | [`T12`](../product/backlog-items/T12-transformation-governance-and-lineage.md) | n/a | Enterprise audit and policy requirements |
| Transform-stage observability metrics | [`T13`](../product/backlog-items/T13-transform-stage-observability-metrics.md) | n/a | Operator visibility at transform-stage granularity |
| Secure data-shaping transforms | [`T14`](../product/backlog-items/T14-secure-data-shaping-transforms.md) | n/a | Masking/tokenization for sensitive fields |

## Practical planning checklist

Keep current design when most answers are yes:

- Is the feature field-scoped and record-local?
- Can it be expressed as one more `transforms[].type` on existing mapping blocks?
- Can observability remain at run/step/rule level?
- Is source-native adaptation not required?

Start separate-layer design when two or more answers are yes:

- Multiple scenarios need the same transform profile with version control.
- Operators need transform-stage evidence independent of processor-rule evidence.
- Features need whole-record orchestration or cross-record state.
- Source-native adaptation needs are becoming frequent.

## Related docs

- [`transformation-capability-roadmap.md`](transformation-capability-roadmap.md)
- [`extension-points.md`](extension-points.md)
- [`runtime-flow.md`](runtime-flow.md)
- [`default-processor.md`](../config/processor/default-processor.md)
- [`product-backlog.md`](../product/product-backlog.md)


