# CSV Target Config

## Purpose

`CsvTargetConfig` defines a file-based CSV target.

It is the current CSV writer path used for flat file output such as XML-to-CSV runs and other scenarios that need simple delimited output artifacts.

## Java contract

Backed by:
- `src/main/java/com/etl/config/target/CsvTargetConfig.java`
- `src/main/java/com/etl/writer/impl/CsvDynamicWriter.java`

## Supported fields today

| Field | Required | Type | Description |
|---|---|---|---|
| `format` | yes | string | Must be `csv` |
| `targetName` | yes | string | Logical target name used in processor mapping lookup |
| `packageName` | no in explicit job mode; otherwise yes | string | Deprecated bridge field for generated target model naming. When omitted for an explicit `job-config.yaml` run, the runtime and build-time generation path derive `com.etl.generated.job.<normalized-job-name>.target` |
| `filePath` | yes | string | CSV output file path or output directory |
| `delimiter` | no | string | Field delimiter for CSV output. When omitted or blank, the target defaults to `,` |
| `includeHeader` | no | boolean | When true, the writer emits one header row using the configured `fields[].name` order before writing data rows |
| `fields` | yes | list | Ordered list of target properties/columns written to the CSV |
| `fields[].name` | yes | string | Property name written in output row order |
| `fields[].type` | yes | string | Logical type used in the generated target model contract |

## Recommended standard template

For new CSV target scenarios in this repo, prefer one shared authoring pattern:

- keep the top-level CSV target fields the same: `format`, `targetName`, `filePath`, `delimiter`, `includeHeader`, `fields`
- keep `fields` as the structural source of truth for flat CSV output
- omit `delimiter` when standard comma-separated output is acceptable
- add `includeHeader` only when a downstream consumer expects a header row

### Minimum required shape

At minimum, a CSV target must declare:

- `format`
- `targetName`
- `filePath`
- `fields`

Minimal CSV target example:

```yaml
targets:
  - format: csv
    targetName: EventsCsv
    filePath: output/events.csv
    fields:
      - name: eventCode
        type: String
      - name: eventTime
        type: String
```

Use that shape when you want the simplest valid CSV target config. The runtime defaults `delimiter` to `,`, and `includeHeader` stays `false` unless explicitly enabled.

### Preferred explicit-job pattern

For preserved bundles and new explicit `job-config.yaml` scenarios, prefer a clear flat target contract and omit optional settings unless the scenario needs them:

```yaml
targets:
  - format: csv
    targetName: YourCsvTarget
    filePath: output/your-output.csv
    fields:
      - name: id
        type: String
```

`packageName` is optional in explicit job mode and should be treated as a deprecated bridge field. When omitted, the runtime derives `com.etl.generated.job.<normalized-job-name>.target` automatically.

## Example

### Example A — flat CSV target with derived package name

This mirrors `src/main/resources/config-jobs/xml-to-csv-events/target-config.yaml`.

```yaml
targets:
  - format: csv
    targetName: EventsCsv
    filePath: output/events-output.csv
    delimiter: ","
    fields:
      - name: eventCode
        type: String
      - name: eventTime
        type: String
      - name: description
        type: String
      - name: sourceSystem
        type: String
```

### Example B — CSV handoff target with header row

Use this when another CSV source step or an external consumer expects a header row in the written file:

```yaml
targets:
  - format: csv
    targetName: TagValidationCsvIntermediate
    filePath: output/intermediate/tag-validation-intermediate.csv
    includeHeader: true
    fields:
      - name: homeAgencyId
        type: String
      - name: tagAgencyId
        type: String
      - name: tagSerialNumber
        type: String
```

In that example:

- `packageName` is omitted intentionally for explicit job mode
- `delimiter` is omitted and still defaults to `,`
- `includeHeader: true` makes the file easier for a downstream CSV reader to consume with `skipHeader: true`

## Example walkthrough

Read the examples in write order:

- `targets:` is the required root for target config files.
- `format: csv` selects the CSV writer path.
- `targetName` is the logical target identity matched by processor mappings and job steps.
- `packageName` is a deprecated bridge field for the generated target package; in explicit job mode prefer omitting it to use the job-scoped default package.
- `filePath` is the output artifact path or output directory.
- `delimiter` controls the CSV separator; when omitted, the target defaults to `,`.
- `includeHeader` is optional; when omitted, it defaults to `false`.
- `fields` lists the target properties in output column order.
- `fields[].name` becomes the property name read from the target object and, when headers are enabled, the header row value.
- `fields[].type` is the logical type stored in the generated target model contract.

The important authoring rule is simple:

- keep `fields` as the structural source of truth for CSV output
- omit `delimiter` when standard comma-separated output is fine
- add `includeHeader: true` only when a downstream consumer expects a header row
- omit `packageName` in explicit job mode when the default job-scoped package is acceptable

## `packageName` deprecation direction

- for new explicit job bundles, omit `packageName`
- treat explicit `packageName` as a compatibility bridge only
- expect a later A4 slice to tighten handling for conflicting authored `packageName` values before the field is removed from the normal target config contract

## Runtime behavior today

- The target config file root is `targets:`.
- If `filePath` ends with `/` or points to an existing directory, the writer appends `<targetName>.csv` using a lowercase target name.
- The current CSV writer writes fields in the configured `fields` order.
- The current implementation uses the configured `delimiter` value and defaults to `,` when the field is omitted.
- When `includeHeader=true`, the writer emits one header row using the configured field names.
- The shipped writer path is flat row output only.

## Validation / usage notes

- `targetName` must match the selected `processor.mappings[].target` value.
- Keep `fields[].name` aligned with the target object property names produced by the processor.
- Nested XML sources can feed CSV targets through `NestedXml` flattening as long as processor mappings point at emitted flattened keys such as `TVLPlateDetails.PlateCountry`.
- Use an explicit file path when you want a fixed artifact name; use a directory path when you want runtime naming from `targetName`.
- Use `includeHeader=true` when a downstream CSV source step in the same scenario needs a standard header row before consuming the file.
- Omit `delimiter` when standard comma-separated output is fine; provide a different separator only when a downstream consumer expects it.

## Current limitations

- No quote/escape/null-formatting controls yet
- No append/rotate/archive target-file policies yet
- No per-field output aliasing beyond the current property-name contract

## Preserved examples

- `src/main/resources/config-jobs/xml-to-csv-events/target-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-tag-validation/target-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/target-config.yaml`

## Related docs

- [`XML source reference`](../source/xml-source.md)
- [`JSON target reference`](json-target.md)
- [`Relational target reference`](relational-target.md)
- [`Default processor reference`](../processor/default-processor.md)



