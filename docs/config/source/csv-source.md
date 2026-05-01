# CSV Source Config

## Purpose

`CsvSourceConfig` defines a file-based CSV source.

It is the simplest and most stable source type currently supported by the engine.

## Java contract

Backed by:
- `src/main/java/com/etl/config/source/CsvSourceConfig.java`
- `src/main/java/com/etl/reader/impl/CsvDynamicReader.java`

## Supported fields today

| Field | Required | Type | Description |
|---|---|---|---|
| `format` | yes | string | Must be `csv` |
| `sourceName` | yes | string | Logical source name used in processor mapping lookup |
| `packageName` | yes | string | Package used for generated source model naming |
| `filePath` | yes | string | CSV file path |
| `delimiter` | yes | string | Field delimiter, usually `,` |
| `archive` | no | object | Optional archive-on-success behavior for CSV file sources |
| `archive.enabled` | yes, when `archive` is present | boolean | Enables processed-file archiving after successful step completion |
| `archive.successPath` | yes, when `archive.enabled=true` | string | Directory where the original CSV file is moved after successful processing |
| `archive.namePattern` | no | string | Output file naming pattern supporting `{originalName}` and `{timestamp}` |
| `validation` | no | object | Optional CSV file-level validation rules executed by the CSV source validator |
| `validation.allowEmpty` | no | boolean | When `false`, the CSV must contain at least one data row after the header; default is `true` |
| `validation.requireHeaderMatch` | no | boolean | When `true`, the CSV header must exactly match the configured `fields` order and names |
| `validation.fileNamePattern` | no | string | Optional regex the source file name must match |
| `validation.onFailure` | no | string | Optional file-level failure behavior: `failStep` or `rejectFile` |
| `validation.rejectPath` | yes, when `validation.onFailure=rejectFile` | string | Directory where an invalid source file is moved before the run fails |
| `fields` | yes | list | Ordered list of CSV columns |
| `fields[].name` | yes | string | Field/property name |
| `fields[].type` | yes | string | Logical type used in generated model contract |

## Example

```yaml
sources:
  - format: csv
    sourceName: Events
    packageName: com.etl.model.source
    filePath: target/events-validation-input.csv
    delimiter: ","
    archive:
      enabled: true
      successPath: target/archive/success/
      namePattern: "{originalName}-{timestamp}"
    validation:
      allowEmpty: false
      requireHeaderMatch: true
      fileNamePattern: '^events-\d{8}\.csv$'
      onFailure: rejectFile
      rejectPath: target/rejected-files/
    fields:
      - name: id
        type: String
      - name: eventTime
        type: String
      - name: description
        type: String
```

## Usage notes

- `sourceName` must match the `processor.mappings[].source` value used by the selected processor.
- The order of `fields` should match the order of columns in the CSV file.
- The current CSV reader skips the first line as a header row.
- When `validation` is present, the CSV source validator checks the configured file path before execution and can fail fast for missing/unreadable files, header-only files, or header mismatches.
- `validation.fileNamePattern` checks only the file name portion, not the full path.
- If `validation.onFailure=rejectFile`, a CSV file that fails file-level validation is moved to `validation.rejectPath` and the run still surfaces a validation error with the rejected-file location in the message/logs.
- The current record count implementation counts file rows and subtracts one for the header.
- Archive behavior currently applies only to CSV sources and only after successful step completion.
- If archive is enabled, `archive.successPath` is required.
- The preserved first-slice example is `src/main/resources/config-scenarios/csv-validation-reject-archive/source-config.yaml`.

## Current limitations

- No custom quote/escape configuration yet
- No per-column source alias support yet
- No schema inference; fields must be declared explicitly
- No alternate header mapping rules yet
- No archive support yet for XML or relational sources
- No archive collision policy beyond the configured `namePattern` and replace-existing move behavior

## Related design note

The broader file-ingestion hardening direction, including future expansion beyond the current CSV slice, is documented in [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md).

## Related docs

- [`../processor/default-processor.md`](../processor/default-processor.md)
- [`../target/relational-target.md`](../target/relational-target.md)

