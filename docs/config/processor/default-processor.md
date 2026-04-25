# Default Processor Config

## Purpose

The default processor defines field-to-field mappings between a configured source name and target name.

It is backed by the existing config-driven processor path and currently powers the normal ETL mapping flow.

## Java contract

Backed by:
- `src/main/java/com/etl/config/processor/ProcessorConfig.java`
- `src/main/java/com/etl/processor/impl/DefaultDynamicProcessor.java`
- `src/main/java/com/etl/mapping/DynamicMapping.java`
- `src/main/java/com/etl/mapping/ValidationAwareDynamicMapping.java`

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
| `mappings[].fields[].rules` | no | list | Optional field-level validation rules |
| `mappings[].fields[].rules[].type` | yes, when a rule is present | string | First shipped rule types are `notNull` and `timeFormat` |
| `mappings[].fields[].rules[].pattern` | yes for `timeFormat` | string | Required time pattern such as `HH:mm:ss` |

## Example

This mirrors the first shipped validation-aware processor config under `src/main/resources/config-scenarios/csv-validation-reject-archive/processor-config.yaml`.

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
      - from: eventTime
        to: eventTime
        rules:
          - type: notNull
          - type: timeFormat
            pattern: HH:mm:ss
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
- The first shipped validation rule types are `notNull` and `timeFormat`.
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
- Validation rules are currently limited to the first CSV-focused slice (`notNull`, `timeFormat`)
- Reject handling is currently proven only for the first CSV-focused slice
- No nested field alias or database-column alias support yet
- No per-target write behavior inside the processor config

## Related design note

The broader file-ingestion hardening direction, including future expansion beyond the current CSV slice, is documented in [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md).

## Related docs

- [`../source/csv-source.md`](../source/csv-source.md)
- [`../target/relational-target.md`](../target/relational-target.md)

