# Default Processor Config

## Purpose

The default processor defines field-to-field mappings between a configured source name and target name.

It is backed by the existing config-driven processor path and currently powers the normal ETL mapping flow.

## Java contract

Backed by:
- `src/main/java/com/etl/config/processor/ProcessorConfig.java`
- `src/main/java/com/etl/processor/impl/DefaultDynamicProcessor.java`
- `src/main/java/com/etl/mapping/DynamicMapping.java`

## Supported fields today

| Field | Required | Type | Description |
|---|---|---|---|
| `type` | yes | string | Must be `default` |
| `mappings` | yes | list | Mapping entries |
| `mappings[].source` | yes | string | Must match the source config `sourceName` |
| `mappings[].target` | yes | string | Must match the target config `targetName` |
| `mappings[].fields` | yes | list | Field mapping list |
| `mappings[].fields[].from` | yes | string | Source property name |
| `mappings[].fields[].to` | yes | string | Target property name |

## Example

This mirrors the preserved single-step processor config under `src/main/resources/config-scenarios/csv-to-sqlserver/processor-config.yaml`.

```yaml
processor:
  type: default
  mappings:
    - source: Customers
      target: CustomersSql
      fields:
        - from: id
          to: id
        - from: name
          to: name
        - from: email
          to: email
```

## Usage notes

- Mapping lookup is based on source/target names, not only on format type.
- This allows the same source format to be mapped differently to different targets.
- When a selected `job-config.yaml` defines explicit `steps`, runtime chooses the mapping for each step by `steps[].source` and `steps[].target`, not by source/target list position.
- One processor config file can therefore contain multiple mappings for a multi-step scenario such as `cust-dept-load`.
- Property names must match the generated or resolved model classes used in the step.
- For the current relational target path, `to` values should also align with the target table column names.

## Preserved examples

- `src/main/resources/config-scenarios/csv-to-sqlserver/processor-config.yaml`
- `src/main/resources/config-scenarios/relational-to-relational/processor-config.yaml`
- `src/main/resources/config-scenarios/cust-dept-load/processor-config.yaml`

## Current limitations

- No expression language or transformation functions yet
- No conditional mapping rules yet
- No per-field validation rules in the shipped processor config yet
- No rejected-record output contract in the shipped processor config yet
- No nested field alias or database-column alias support yet
- No per-target write behavior inside the processor config

## Proposed next-slice design note

The proposed design for field-level validation rules and rejected-record output is documented in [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md). That note is forward-looking and does not change the current shipped processor config contract yet.

## Related docs

- [`../source/csv-source.md`](../source/csv-source.md)
- [`../target/relational-target.md`](../target/relational-target.md)

