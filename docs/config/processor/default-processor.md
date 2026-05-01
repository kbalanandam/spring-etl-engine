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

## Supported fields today

| Field | Required | Type | Description |
|---|---|---|---|
| `type` | yes | string | Must be `default` |
| `rejectHandling` | no | object | Optional rejected-record output settings for validation-aware runs |
| `rejectHandling.enabled` | yes, when `rejectHandling` is present | boolean | Enables rejected-record output |
| `rejectHandling.outputPath` | yes, when `rejectHandling.enabled=true` | string | Reject CSV file or directory path |
| `rejectHandling.includeReasonColumns` | no | boolean | Appends `_rejectField`, `_rejectRule`, and `_rejectMessage` metadata columns when true |
| `mappings` | yes | list | Mapping entries |
| `mappings[].source` | yes | string | Must match the source config `sourceName` |
| `mappings[].target` | yes | string | Must match the target config `targetName` |
| `mappings[].fields` | yes | list | Field mapping list |
| `mappings[].fields[].from` | yes | string | Source property name |
| `mappings[].fields[].to` | yes | string | Target property name |
| `mappings[].fields[].transforms` | no, future | list | Planned optional field-level transform/cleaner chain. Omit the block when no cleanup/normalization is needed |
| `mappings[].fields[].rules` | no | list | Optional field-level validation rules. If no `duplicate` rule is configured, runtime does not perform duplicate detection for that mapping |
| `mappings[].fields[].rules[].onFailure` | no | string | Optional validation outcome override: `failStep` or `rejectRecord` |
| `mappings[].fields[].transforms[].type` | yes, when a future transform is present | string | Planned first transform type is `valueMap`; future narrow built-ins may include generic cleaners such as trim/case handling/null fallback |
| `mappings[].fields[].rules[].type` | yes, when a rule is present | string | Shipped rule types are `notNull`, `timeFormat`, and first-slice `duplicate` |
| `mappings[].fields[].rules[].pattern` | yes for `timeFormat` | string | Required time pattern such as `HH:mm:ss` |
| `mappings[].fields[].rules[].keyFields` | no, for `duplicate` | list of strings | Optional duplicate-key field list. When omitted, duplicate detection uses the mapped field itself as the duplicate key |
| `mappings[].fields[].rules[].orderBy` | no, for `duplicate` | list | Optional winner-selection order. When omitted, duplicate handling stays in keep-first mode and the first encountered record is retained for a duplicate key |
| `mappings[].fields[].rules[].orderBy[].field` | yes, when `orderBy` is present | string | Field used to rank duplicate candidates; each configured field should appear only once per `orderBy` list |
| `mappings[].fields[].rules[].orderBy[].direction` | yes, when `orderBy` is present | string | Winner-selection direction: `ASC` or `DESC` |

## Example

This example illustrates the current duplicate-aware processor shape, including optional `duplicate`, `keyFields`, and `orderBy` usage. For preserved shipped examples, see the scenario configs listed below.

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

## Usage notes

- Mapping lookup is based on source/target names, not only on format type.
- This allows the same source format to be mapped differently to different targets.
- When a selected `job-config.yaml` defines explicit `steps`, runtime chooses the mapping for each step by `steps[].source` and `steps[].target`, not by source/target list position.
- One processor config file can therefore contain multiple mappings for a multi-step scenario such as `cust-dept-load`.
- Property names must match the generated or resolved model classes used in the step.
- For the current relational target path, `to` values should also align with the target table column names.
- Future cleaning/normalization behavior should not be modeled as validation-only `rules`. Validation answers “accept or reject this record”; cleaning/normalization answers “rewrite this value before it is validated or written”.
- The intended future processor order for configurable field cleanup is: read raw value → apply configured transforms/cleaners → evaluate validation rules on the transformed value → write the target field.
- Transform-then-reject is valid and expected. For example, a future `valueMap` transform may normalize `IND -> IN`, `USA -> US`, and all other codes to `UNKNOWN`, after which a processor rule may reject `UNKNOWN` if that value is not allowed.
- The shipped validation rule types are `notNull`, `timeFormat`, and `duplicate`.
- `mappings[].fields[].rules[].onFailure` is optional. If omitted, runtime keeps the existing behavior: reject the record when reject handling is enabled, otherwise fail the step.
- Use `onFailure: failStep` for business-critical required fields where the scenario should stop immediately and surface a clear exception in the logs.
- Use `onFailure: rejectRecord` when the rule should send bad records to reject output instead of failing the step. This requires `rejectHandling.enabled=true`.
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
- If validation rules reject a record, the default processor returns no accepted item for that row and writes the rejected row to the configured reject output instead.
- If `rejectHandling.enabled=true`, `rejectHandling.outputPath` is required.

## Planned future transform / cleaner direction

The current shipped runtime does **not** yet expose a transform list in `processor-config.yaml`, but future cleaner/normalization work should use a separate transform extension point rather than overloading validation rules.

The narrow first slice should stay explicit and config-driven:

- optional field-level `transforms` (future) beside `rules`
- omit `transforms` entirely when no cleanup behavior is needed
- first built-in transform type: `valueMap`
- optional default fallback such as `Unknown`
- optional case handling for code normalization
- additive support for multiple transform steps on the same field so future scenarios can chain cleaners
- ordered execution so customers can have zero, one, or many transform steps on the same field

Illustrative future shape:

```yaml
type: default
mappings:
  - source: Orders
    target: OrdersOut
    fields:
      - from: statusCode
        to: status
        transforms:
          - type: valueMap
            mappings:
              "1": Success
              "2": Fail
            defaultValue: Unknown

      - from: countryCode
        to: countryCode
        transforms:
          - type: valueMap
            mappings:
              USA: US
              IND: IN
            caseSensitive: false
```

The main design rule is:

- use **transforms/cleaners** to normalize or convert values such as `1 -> Success` or `USA -> US`
- use **rules** to reject invalid records such as null required fields, malformed times, or duplicate keys

That separation keeps future cleaner techniques scalable to N transform types without turning the validation SPI into a mixed transformation-and-rejection framework.

Planned ownership/precedence guidance:

- the default home for generic cleanup remains the processor layer because those rewrites work on normal runtime records regardless of source format
- a future source-transform YAML contract should be introduced only for source-native cases such as XPath-, namespace-, header-, token-, or pre-flattening adaptation that cannot be expressed cleanly in the processor layer
- once source transforms exist, runtime/config validation should fail fast or at least warn when equivalent generic value rewriting is configured for the same field in both source and processor layers
- layered behavior is still valid when the concerns are different, for example source-native extraction first, then processor `valueMap`, then processor rejection rules

## Preserved examples

- `src/main/resources/config-scenarios/csv-to-sqlserver/processor-config.yaml`
- `src/main/resources/config-scenarios/csv-validation-reject-archive/processor-config.yaml`
- `src/main/resources/config-scenarios/relational-to-relational/processor-config.yaml`
- `src/main/resources/config-scenarios/cust-dept-load/processor-config.yaml`

## Current limitations

- No shipped field-transform / cleaner SPI yet
- No expression language or derived-field transformation functions yet
- No conditional mapping rules yet
- Validation rules are currently limited to the current CSV-focused slice (`notNull`, `timeFormat`, and `duplicate` with single-field, composite-key, or ordered winner selection)
- The first planned normalization slice is expected to start with config-driven value mapping such as status-code decoding and country-code normalization before a broader expression language is introduced
- Client-selectable duplicate storage strategy and target-aware deduplication are still future work
- Reject handling is currently proven only for the first CSV-focused slice
- No nested field alias or database-column alias support yet
- No per-target write behavior inside the processor config

## Related design note

The broader file-ingestion hardening direction, including future expansion beyond the current CSV slice, is documented in [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md).

## Related docs

- [`../source/csv-source.md`](../source/csv-source.md)
- [`../target/relational-target.md`](../target/relational-target.md)

