# Default Processor Config

## Purpose

The default processor defines field-to-field mappings between a configured source name and target name.

It is backed by the existing config-driven processor path and currently powers the normal ETL mapping flow.

This is also the supported runtime path for record-level validation. The older standalone `com.etl.validation.*` package and `validation-config.yaml` resource are deprecated and are not part of the active ETL runtime contract.

## Java contract

Backed by:
- `src/main/java/com/etl/config/processor/ProcessorConfig.java`
- `src/main/java/com/etl/processor/impl/DefaultDynamicProcessor.java`
- `src/main/java/com/etl/mapping/DynamicMapping.java`
- `src/main/java/com/etl/mapping/ValidationAwareDynamicMapping.java`
- `src/main/java/com/etl/processor/validation/ValidationRuleEvaluator.java`
- `src/main/java/com/etl/processor/validation/ProcessorValidationRule.java`

## Start here

Read this page in three passes:

1. start with the smallest valid `from -> to` mapping
2. add `rules` and `rejectHandling` when records may need validation or rejection
3. add `transforms[]` and duplicate handling only when a scenario really needs them

Core mental model:

- one `mappings[]` entry matches one configured `source` name to one configured `target` name
- each `fields[]` entry maps one input field to one output field
- optional `transforms[]` rewrite the value first
- optional `rules[]` validate the rewritten value after transforms run
- explicit `job-config.yaml` runs select the correct mapping by `steps[].source` and `steps[].target`

Smallest valid shape:

```yaml
type: default
mappings:
  - source: Customers
    target: CustomersXml
    fields:
      - from: id
        to: id
      - from: firstName
        to: firstName
      - from: lastName
        to: lastName
```

This is still the best place to begin. Everything else on this page is additive.

## Supported fields today

### Core mapping fields

| Field | Required | Type | Description |
|---|---|---|---|
| `type` | yes | string | Must be `default` |
| `mappings` | yes | list | Mapping entries |
| `mappings[].source` | yes | string | Must match the source config `sourceName` |
| `mappings[].target` | yes | string | Must match the target config `targetName` |
| `mappings[].fields` | yes | list | Field mapping list |
| `mappings[].fields[].from` | yes | string | Source property name |
| `mappings[].fields[].to` | yes | string | Target property name |

### Advanced shipped options

| Field | Required | Type | Description |
|---|---|---|---|
| `rejectHandling` | no | object | Optional rejected-record output settings for validation-aware runs |
| `rejectHandling.enabled` | yes, when `rejectHandling` is present | boolean | Enables rejected-record output |
| `rejectHandling.outputPath` | yes, when `rejectHandling.enabled=true` | string | Reject CSV file or directory path |
| `rejectHandling.includeReasonColumns` | no | boolean | Appends `_rejectField`, `_rejectRule`, and `_rejectMessage` metadata columns when true |
| `mappings[].fields[].transforms` | no | list | Optional ordered field-level transform/cleaner chain. Omit the block when no cleanup/normalization is needed |
| `mappings[].fields[].rules` | no | list | Optional field-level validation rules. If no `duplicate` rule is configured, runtime does not perform duplicate detection for that mapping |
| `mappings[].fields[].rules[].onFailure` | no | string | Optional validation outcome override: `failStep` or `rejectRecord` |
| `mappings[].fields[].transforms[].type` | yes, when a transform is present | string | Shipped transform types are `valueMap` and `expression` |
| `mappings[].fields[].transforms[].expression` | yes for `expression` | string | Spring Expression Language (SpEL) expression used to derive or rewrite the field value |
| `mappings[].fields[].transforms[].mappings` | yes for `valueMap` | object | Source-value to rewritten-value map, such as `"1": Success` or `USA: US` |
| `mappings[].fields[].transforms[].defaultValue` | no, for `valueMap` | any scalar/object | Optional fallback written when no configured mapping matches |
| `mappings[].fields[].transforms[].caseSensitive` | no, for `valueMap` | boolean | Match mode for configured keys; defaults to `true` |
| `mappings[].fields[].rules[].type` | yes, when a rule is present | string | Shipped rule types are `notNull`, `timeFormat`, and first-slice `duplicate` |
| `mappings[].fields[].rules[].pattern` | yes for `timeFormat` | string | Required time pattern such as `HH:mm:ss` |
| `mappings[].fields[].rules[].keyFields` | no, for `duplicate` | list of strings | Optional duplicate-key field list. When omitted, duplicate detection uses the mapped field itself as the duplicate key |
| `mappings[].fields[].rules[].orderBy` | no, for `duplicate` | list | Optional winner-selection order. When omitted, duplicate handling stays in keep-first mode and the first encountered record is retained for a duplicate key |
| `mappings[].fields[].rules[].orderBy[].field` | yes, when `orderBy` is present | string | Field used to rank duplicate candidates; each configured field should appear only once per `orderBy` list |
| `mappings[].fields[].rules[].orderBy[].direction` | yes, when `orderBy` is present | string | Winner-selection direction: `ASC` or `DESC` |

## Progressive examples

### 1. Basic field mapping

Use this shape when you only need field-to-field mapping:

```yaml
type: default
mappings:
  - source: Customers
    target: CustomersXml
    fields:
      - from: id
        to: id
      - from: name
        to: name
      - from: email
        to: email
```

### 2. Validation-aware mapping with rejected-record output

Use this when invalid rows should be rejected instead of always failing the whole step:

```yaml
type: default
rejectHandling:
  enabled: true
  outputPath: target/rejects/
  includeReasonColumns: true
mappings:
  - source: Events
    target: EventsCsv
    fields:
      - from: id
        to: id
        rules:
          - type: notNull
            onFailure: failStep
      - from: eventTime
        to: eventTime
        rules:
          - type: notNull
          - type: timeFormat
            pattern: HH:mm:ss
```

### 3. Transform-aware and duplicate-aware mapping

Use this only when a scenario needs cleanup/normalization before validation, or duplicate handling beyond simple field mapping:

```yaml
type: default
rejectHandling:
  enabled: true
  outputPath: target/rejects/
  includeReasonColumns: true
mappings:
  - source: Events
    target: EventsCsv
    fields:
      - from: id
        to: id
        rules:
          - type: notNull
            onFailure: failStep
          - type: duplicate
            onFailure: rejectRecord
            keyFields:
              - id
            orderBy:
              - field: eventTime
                direction: DESC
              - field: sequenceNo
                direction: ASC
      - from: statusCode
        to: status
        transforms:
          - type: valueMap
            mappings:
              "1": Success
              "2": Fail
            defaultValue: Unknown
      - from: eventTime
        to: eventTime
        rules:
          - type: notNull
          - type: timeFormat
            pattern: HH:mm:ss
      - from: sequenceNo
        to: sequenceNo
      - from: description
        to: description
```

For preserved runnable examples, see the scenario configs listed later in this page.

### 4. Expression-derived field mapping

Use this when a target field should be derived from one or more source properties rather than copied from a single `from` field:

```yaml
type: default
mappings:
  - source: Customers
    target: CustomersCsv
    fields:
      - to: fullName
        transforms:
          - type: expression
            expression: "#input.firstName + ' ' + #input.lastName"
      - from: countryCode
        to: countryCode
        transforms:
          - type: valueMap
            mappings:
              USA: US
              IND: IN
      - to: customerSummary
        transforms:
          - type: expression
            expression: "#resolved['countryCode'] + ':' + #input.customerId"
```

Use the expression transform for derived fields and other processor-side value calculation. When `from` is omitted, the first transform must be `type: expression` so runtime has an explicit way to produce the field value.

## How runtime uses this config

### Mapping selection

- Mapping lookup is based on source/target names, not only on format type.
- This allows the same source format to be mapped differently to different targets.
- When a selected `job-config.yaml` defines explicit `steps`, runtime chooses the mapping for each step by `steps[].source` and `steps[].target`, not by source/target list position.
- One processor config file can therefore contain multiple mappings for a multi-step scenario such as `cust-dept-load`.
- Property names must match the generated or resolved model classes used in the step.
- For the current relational target path, `to` values should also align with the target table column names.

### Transform and rule order

- Cleaning/normalization behavior should not be modeled as validation-only `rules`. Validation answers “accept or reject this record”; cleaning/normalization answers “rewrite this value before it is validated or written”.
- The shipped processor order for configurable field cleanup is: read raw value → apply configured transforms/cleaners → evaluate validation rules on the transformed value → write the target field.
- Transform-then-reject is valid and expected. For example, a `valueMap` transform may normalize `IND -> IN`, `USA -> US`, and all other codes to `UNKNOWN`, after which a processor rule may reject `UNKNOWN` if that value is not allowed.
- Multiple `transforms[]` entries on the same field run in the order configured.
- The shipped transform types are `valueMap` and `expression`.
- `valueMap` supports direct code normalization, optional `defaultValue`, and optional case-insensitive matching through `caseSensitive: false`.
- `expression` uses Spring Expression Language (SpEL) and can reference:
  - `#input` or `#source` — the original runtime record
  - `#value` — the current field value entering that transform step
  - `#resolved` — previously resolved mapping values from earlier `fields[]` entries
- When `from` is omitted for a derived field, the first transform must be `expression`.
- The shipped validation rule types are `notNull`, `timeFormat`, and `duplicate`.

### Failure handling and startup validation

- `mappings[].fields[].rules[].onFailure` is optional. If omitted, runtime keeps the existing behavior: reject the record when reject handling is enabled, otherwise fail the step.
- Use `onFailure: failStep` for business-critical required fields where the scenario should stop immediately and surface a clear exception in the logs.
- Use `onFailure: rejectRecord` when the rule should send bad records to reject output instead of failing the step. This requires `rejectHandling.enabled=true`.
- If validation rules reject a record, the default processor returns no accepted item for that row and writes the rejected row to the configured reject output instead.
- If `rejectHandling.enabled=true`, `rejectHandling.outputPath` is required.
- Explicit scenario runs validate processor mappings, transforms, and rules during startup.
- Processor-config problems are surfaced before unrelated generated-model validation issues, with scenario-aware failure context for the selected run.

### Duplicate handling

- The shipped `duplicate` rule is configured in the processor layer, so the same duplicate contract can be reused for CSV, flat XML, relational, and other future record-oriented sources once they are read into normal runtime records.
- Duplicate checking is optional. If no `duplicate` rule is configured for a mapping, runtime does not perform duplicate detection or duplicate-based rejection for that mapping.
- The `duplicate` rule supports keep-first matching by default when only `keyFields` are configured.
- If a `duplicate` rule is configured without `keyFields`, the mapped field itself becomes the duplicate key.
- If a `duplicate` rule is configured with `keyFields` but without `orderBy`, the first encountered record for that duplicate key is retained and later matching records are treated as duplicates.
- If `orderBy` is present, the retained record per duplicate key is selected using the configured ordered fields such as `eventTime DESC` followed by `sequenceNo ASC`.
- Repeating the same `orderBy[].field` more than once in one `duplicate` rule is invalid and is rejected during processor-config validation.
- When `orderBy` is not present, duplicate handling does not do “best record wins” selection; it stays in simple keep-first mode.
- The current shipped `duplicate` rule uses step-local in-memory tracking for keep-first duplicate elimination.
- When `orderBy` is present, runtime resolves winners through a shared ordered-duplicate abstraction and currently chooses between in-memory and embedded-DB staging based on runtime volume hints before the final write phase.
- Ordered duplicate winner selection still uses tasklet-style final buffering for that mapping so earlier writes do not need to be undone.
- The current duplicate contract expects flat field/property access on the runtime record. XML-native duplicate identity based on XPath, namespaces, or nested structure selectors is not part of the shipped processor config contract yet.
- Those built-in rule types are dispatched through the active processor-rule SPI, so future rule types should be added as `ProcessorValidationRule` implementations rather than through the deprecated `com.etl.validation.*` package.

## Shipped transform support today

The current shipped runtime exposes an optional `transforms[]` list in `processor-config.yaml` through a separate processor transform extension point rather than overloading validation rules.

Current shipped shape:

- optional field-level `transforms` beside `rules`
- omit `transforms` entirely when no cleanup behavior is needed
- first built-in transform type: `valueMap`
- built-in derived-field transform type: `expression`
- optional default fallback such as `Unknown`
- optional case handling for code normalization
- additive support for multiple transform steps on the same field so future scenarios can chain cleaners
- ordered execution so customers can have zero, one, or many transform steps on the same field
- derived fields may omit `from` only when the first transform is `expression`
- expressions can read the original record plus previously resolved field values from earlier mapping entries

The main design rule is:

- use **transforms/cleaners** to normalize or convert values such as `1 -> Success` or `USA -> US`
- use **expression transforms** to derive values such as `fullName = firstName + ' ' + lastName`
- use **rules** to reject invalid records such as null required fields, malformed times, or duplicate keys

That separation keeps shipped behavior readable today and leaves room for future cleaner techniques without turning the validation SPI into a mixed transformation-and-rejection framework.

## Preserved runnable examples

- `src/main/resources/config-scenarios/csv-validation-reject-archive/processor-config.yaml` — first shipped validation, reject-output, and archive-aligned processor example
- `src/main/resources/config-scenarios/csv-to-sqlserver/processor-config.yaml` — basic file-to-relational mapping example
- `src/main/resources/config-scenarios/xml-nested-to-csv-tag-validation/processor-config.yaml` — nested XML flattening scenario proving the shared processor rule/reject contract on a file-backed XML flow
- `src/main/resources/config-scenarios/relational-to-relational/processor-config.yaml` — relational source to relational target mapping example
- `src/main/resources/config-scenarios/cust-dept-load/processor-config.yaml` — multi-step scenario with multiple processor mappings in one file

## Current limitations

- No conditional mapping rules yet
- The shipped validation rule set remains intentionally small (`notNull`, `timeFormat`, and `duplicate` with single-field, composite-key, or ordered winner selection)
- The shipped transform baseline now covers config-driven `valueMap` rewriting plus processor-side expression-derived fields, while conditional rules and lookup/enrichment remain future work
- Client-selectable duplicate storage strategy and target-aware deduplication are still future work
- Reject handling is now exercised by preserved file-backed scenarios, with the strongest first proof still centered on CSV and additional nested XML proof through the same processor contract
- No nested field alias or database-column alias support yet
- No per-target write behavior inside the processor config

## Future direction kept outside this page

This page documents the shipped processor contract. For future-only design direction such as broader transformation maturity, source-transform ownership, and expression/conditional roadmap items, continue in:

- [`../../architecture/transformation-capability-roadmap.md`](../../architecture/transformation-capability-roadmap.md)
- [`../../architecture/extension-points.md`](../../architecture/extension-points.md)

## Related design note

The broader file-ingestion hardening direction, including future expansion beyond the current CSV slice, is documented in [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md).

## Related docs

- [`../source/csv-source.md`](../source/csv-source.md)
- [`../target/relational-target.md`](../target/relational-target.md)

