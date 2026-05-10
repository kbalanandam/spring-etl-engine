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
| `packageName` | no in explicit job mode; otherwise yes | string | Package used for generated source model naming. When omitted for an explicit `job-config.yaml` run, the runtime derives `com.etl.generated.job.<normalized-job-name>.source` |
| `filePath` | yes | string | CSV file path |
| `delimiter` | yes | string | Field delimiter, usually `,` |
| `skipHeader` | no | boolean | Whether the runtime skips the first CSV line as a header row; defaults to `true` |
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
    packageName: com.etl.generated.job.csvvalidationrejectarchive.source
    filePath: input/events-validation-input.csv
    delimiter: ","
    skipHeader: true
    archive:
      enabled: true
      successPath: output/archive/success/
      namePattern: "{originalName}-{timestamp}"
    validation:
      allowEmpty: false
      requireHeaderMatch: true
      fileNamePattern: '^events-\d{8}\.csv$'
      onFailure: rejectFile
      rejectPath: output/rejected-files/
    fields:
      - name: id
        type: String
      - name: eventTime
        type: String
      - name: description
        type: String
```

## Example walkthrough

Read the example top to bottom:

- `sources:` is the required file root for source config bundles.
- `format: csv` selects the CSV reader path.
- `sourceName` is the logical name used by `processor.mappings[].source` and by `job-config.yaml` step selection.
- `packageName` points at the generated source model package; in explicit job mode it may be omitted if you want runtime to derive `com.etl.generated.job.<normalized-job-name>.source`.
- `filePath` is the CSV file to read.
- `delimiter` declares the incoming CSV separator.
- `skipHeader: true` means the reader treats the first line as a header row and skips it before data mapping begins.
- `archive` defines optional archive-on-success behavior for the original source file after the step succeeds.
- `archive.enabled` turns archiving on.
- `archive.successPath` is where the original input file is moved after success.
- `archive.namePattern` controls the archived file name using placeholders such as `{originalName}` and `{timestamp}`.
- `validation` defines file-level checks that run before the step starts reading data rows.
- `validation.allowEmpty: false` means the file must contain at least one data row after the header.
- `validation.requireHeaderMatch: true` means the first line must match the configured `fields[]` names and order; this only makes sense when `skipHeader=true`.
- `validation.fileNamePattern` validates only the file name portion, not the whole path.
- `validation.onFailure: rejectFile` means the file is moved to the reject location before the run fails.
- `validation.rejectPath` is the reject destination used by that failure mode.
- `fields` lists the CSV columns in file order.
- `fields[].name` is the runtime property name expected by the generated source model and downstream processor mapping.
- `fields[].type` is the logical type recorded in the generated model contract.

This example intentionally shows a fuller CSV source shape. Smaller scenarios may omit `archive` or `validation`, but the meaning of the shared top-level fields remains the same.

## Usage notes

- `sourceName` must match the `processor.mappings[].source` value used by the selected processor.
- The order of `fields` should match the order of columns in the CSV file.
- `skipHeader: true` tells the CSV reader to skip the first line as a header row; this is the default for backward compatibility and for intermediate CSV handoff files that are written with `includeHeader: true`.
- Set `skipHeader: false` for headerless CSV sources so the first line is treated as data.
- When `validation` is present, the CSV source validator checks the configured file path before execution and can fail fast for missing/unreadable files, header-only files, or header mismatches.
- `validation.requireHeaderMatch=true` is only valid when `skipHeader=true` because header matching assumes the first line is a real header row.
- `validation.fileNamePattern` checks only the file name portion, not the full path.
- If `validation.onFailure=rejectFile`, a CSV file that fails file-level validation is moved to `validation.rejectPath` and the run still surfaces a validation error with the rejected-file location in the message/logs.
- The current record count implementation subtracts one row only when `skipHeader=true`.
- Archive behavior is part of the shared file-source contract and applies after successful step completion when enabled for the active source config.
- If archive is enabled, `archive.successPath` is required.
- The preserved first-slice example is `src/main/resources/config-jobs/csv-validation-reject-archive/source-config.yaml`.
- For explicit job-config runs, `packageName` may be omitted and defaults to scenario/job-scoped generated classes such as `com.etl.generated.job.<normalized-job-name>.source`.
- If you keep `packageName` explicit, prefer scenario/job-scoped generated classes rather than shared handwritten `com.etl.model.source` packages.

## Current limitations

- No custom quote/escape configuration yet
- No per-column source alias support yet
- No schema inference; fields must be declared explicitly
- No alternate header mapping rules yet
- No archive support for non-file sources such as relational sources
- No archive collision policy beyond the configured `namePattern` and replace-existing move behavior

## Related design note

The broader file-ingestion hardening direction, including future expansion beyond the current CSV slice, is documented in [`File ingestion hardening`](../../architecture/file-ingestion-hardening.md).

## Related docs

- [`Default processor reference`](../processor/default-processor.md)
- [`Relational target reference`](../target/relational-target.md)


