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
| `mappings[].fields[].rules` | no | list | Optional field-level validation rules. If no `duplicate` rule is configured, runtime does not perform duplicate detection for that mapping |
| `mappings[].fields[].rules[].type` | yes, when a rule is present | string | Shipped rule types are `notNull`, `timeFormat`, and first-slice `duplicate` |
| `mappings[].fields[].rules[].pattern` | yes for `timeFormat` | string | Required time pattern such as `HH:mm:ss` |
| `mappings[].fields[].rules[].keyFields` | no, for `duplicate` | list of strings | Optional duplicate-key field list. When omitted, duplicate detection uses the mapped field itself as the duplicate key |
| `mappings[].fields[].rules[].orderBy` | no, for `duplicate` | list | Optional winner-selection order. When omitted, duplicate handling stays in keep-first mode and the first encountered record is retained for a duplicate key |
| `mappings[].fields[].rules[].orderBy[].field` | yes, when `orderBy` is present | string | Field used to rank duplicate candidates |
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
          - type: duplicate
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
- The shipped validation rule types are `notNull`, `timeFormat`, and `duplicate`.
- Duplicate checking is optional. If no `duplicate` rule is configured for a mapping, runtime does not perform duplicate detection or duplicate-based rejection for that mapping.
- The `duplicate` rule supports keep-first matching by default when only `keyFields` are configured.
- If a `duplicate` rule is configured without `keyFields`, the mapped field itself becomes the duplicate key.
- If a `duplicate` rule is configured with `keyFields` but without `orderBy`, the first encountered record for that duplicate key is retained and later matching records are treated as duplicates.
- If `orderBy` is present, the retained record per duplicate key is selected using the configured ordered fields such as `eventTime DESC` followed by `sequenceNo ASC`.
- When `orderBy` is not present, duplicate handling does not do “best record wins” selection; it stays in simple keep-first mode.
- The current shipped `duplicate` rule uses step-local in-memory tracking for keep-first duplicate elimination.
- When `orderBy` is present, runtime resolves winners through a shared ordered-duplicate abstraction and currently chooses between in-memory and embedded-DB staging based on runtime volume hints before the final write phase.
- Ordered duplicate winner selection still uses tasklet-style final buffering for that mapping so earlier writes do not need to be undone.
- Those built-in rule types are dispatched through the active processor-rule SPI, so future rule types should be added as `ProcessorValidationRule` implementations rather than through the deprecated `com.etl.validation.*` package.
- If validation rules reject a record, the default processor returns no accepted item for that row and writes the rejected row to the configured reject output instead.
- If `rejectHandling.enabled=true`, `rejectHandling.outputPath` is required.

## Preserved examples

- `src/main/resources/config-scenarios/csv-to-sqlserver/processor-config.yaml`
- `src/main/resources/config-scenarios/csv-validation-reject-archive/processor-config.yaml`
- `src/main/resources/config-scenarios/relational-to-relational/processor-config.yaml`
- `src/main/resources/config-scenarios/cust-dept-load/processor-config.yaml`

## Current limitations

- No expression language or transformation functions yet
- No conditional mapping rules yet
- Validation rules are currently limited to the current CSV-focused slice (`notNull`, `timeFormat`, and `duplicate` with single-field, composite-key, or ordered winner selection)
- Client-selectable duplicate storage strategy and target-aware deduplication are still future work
- Reject handling is currently proven only for the first CSV-focused slice
- No nested field alias or database-column alias support yet
- No per-target write behavior inside the processor config

## Related design note

The broader file-ingestion hardening direction, including future expansion beyond the current CSV slice, is documented in [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md).

## Related docs

- [`../source/csv-source.md`](../source/csv-source.md)
- [`../target/relational-target.md`](../target/relational-target.md)

