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
| `packageName` | no in explicit job mode; otherwise yes | string | Package used for generated target model naming. When omitted for an explicit `job-config.yaml` run, the runtime and build-time generation path derive `com.etl.generated.job.<normalized-job-name>.target` |
| `filePath` | yes | string | CSV output file path or output directory |
| `delimiter` | yes | string | Intended field delimiter for CSV output; the current writer still emits comma-separated output only |
| `includeHeader` | no | boolean | When true, the writer emits one header row using the configured `fields[].name` order before writing data rows |
| `fields` | yes | list | Ordered list of target properties/columns written to the CSV |
| `fields[].name` | yes | string | Property name written in output row order |
| `fields[].type` | yes | string | Logical type used in the generated target model contract |

## Example

This mirrors `src/main/resources/config-jobs/xml-to-csv-events/target-config.yaml`.

```yaml
targets:
  - format: csv
    targetName: EventsCsv
    packageName: com.etl.generated.job.xmltocsvevents.target
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

## Runtime behavior today

- The target config file root is `targets:`.
- If `filePath` ends with `/` or points to an existing directory, the writer appends `<targetName>.csv` using a lowercase target name.
- The current CSV writer writes fields in the configured `fields` order.
- The current implementation always uses `,` as the output delimiter even when a different `delimiter` value is configured.
- When `includeHeader=true`, the writer emits one header row using the configured field names.
- The shipped writer path is flat row output only.

## Validation / usage notes

- `targetName` must match the selected `processor.mappings[].target` value.
- Keep `fields[].name` aligned with the target object property names produced by the processor.
- Nested XML sources can feed CSV targets through `NestedXml` flattening as long as processor mappings point at emitted flattened keys such as `TVLPlateDetails.PlateCountry`.
- Use an explicit file path when you want a fixed artifact name; use a directory path when you want runtime naming from `targetName`.
- Use `includeHeader=true` when a downstream CSV source step in the same scenario needs a standard header row before consuming the file.
- For now, treat `delimiter` as documentation/future-facing config unless it is `,`, because the shipped writer does not yet honor alternate delimiters.

## Current limitations

- No alternate delimiter support at runtime yet despite the config field being present
- No quote/escape/null-formatting controls yet
- No append/rotate/archive target-file policies yet
- No per-field output aliasing beyond the current property-name contract

## Preserved examples

- `src/main/resources/config-jobs/xml-to-csv-events/target-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-tag-validation/target-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/target-config.yaml`

## Related docs

- [`XML source reference`](../source/xml-source.md)
- [`Relational target reference`](relational-target.md)
- [`Default processor reference`](../processor/default-processor.md)



