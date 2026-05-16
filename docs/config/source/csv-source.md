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
| `filePath` | yes | string | CSV file path |
| `delimiter` | yes | string | Field delimiter, usually `,` |
| `skipHeader` | no | boolean | Whether the runtime skips the first CSV line as a header row; defaults to `true` |
| `parser` | no | object | Optional CSV parser settings for quoted-field handling on the active reader path |
| `parser.quoteCharacter` | no | string | Single-character quote marker used for quoted fields and doubled-quote escaping; when omitted the reader keeps Spring Batch's default `"` behavior |
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

## Recommended standard template

For new CSV scenarios in this repo, prefer one shared authoring pattern:

- keep the top-level CSV source fields the same: `format`, `sourceName`, `filePath`, `delimiter`, `fields`
- use `skipHeader` to describe whether the first line is a header row; it defaults to `true`
- add `validation` only when the scenario needs file-level checks before reading starts
- add `archive` only when the original source file should be moved after successful processing
- add `parser.quoteCharacter` only when the source feed uses a non-default quote marker

### Minimum required shape

At minimum, a CSV source must declare:

- `format`
- `sourceName`
- `filePath`
- `delimiter`
- `fields`

Minimal CSV source example:

```yaml
sources:
  - format: csv
    sourceName: Events
    filePath: input/events.csv
    delimiter: ","
    fields:
      - name: id
        type: String
      - name: eventTime
        type: String
```

Use that shape when you want the simplest valid CSV source config and do not need validation, archive behavior, or custom quote-character handling.

### Preferred explicit-job pattern

For preserved bundles and new explicit `job-config.yaml` scenarios, prefer a clear runtime shape and omit optional blocks unless the scenario needs them:

```yaml
sources:
  - format: csv
    sourceName: Events
    filePath: input/events.csv
    delimiter: ","
    skipHeader: true
    fields:
      - name: id
        type: String
      - name: eventTime
        type: String
```

`packageName` is no longer a supported CSV source property. The runtime derives `com.etl.generated.job.<normalized-job-name>.source` for explicit jobs and applies the internal demo fallback source package only in direct-config compatibility mode.

## Example

### Example A — full CSV source shape with archive, validation, and parser options

```yaml
sources:
  - format: csv
    sourceName: Events
    filePath: input/events-validation-input.csv
    delimiter: ","
    skipHeader: true
    parser:
      quoteCharacter: "'"
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

This mirrors the preserved bundle:

- `src/main/resources/config-jobs/csv-validation-reject-archive/source-config.yaml`

### Example B — explicit job mode with derived package name

Use this when the default job-scoped package is acceptable and you do not need optional archive, validation, or parser settings:

```yaml
sources:
  - format: csv
    sourceName: Customers
    filePath: input/customers.csv
    delimiter: ","
    fields:
      - name: id
        type: String
      - name: name
        type: String
```

In that example:

- no authored `packageName` appears because package identity is derived internally
- `skipHeader` is omitted and still defaults to `true`
- `validation`, `archive`, and `parser` are all optional and omitted

## Example walkthrough

Read the examples top to bottom:

- `sources:` is the required file root for source config bundles.
- `format: csv` selects the CSV reader path.
- `sourceName` is the logical name used by `processor.mappings[].source` and by `job-config.yaml` step selection.
- `filePath` is the CSV file to read.
- `delimiter` declares the incoming CSV separator.
- `skipHeader: true` means the reader treats the first line as a header row and skips it before data mapping begins. When omitted, it defaults to `true`.
- `parser.quoteCharacter: "'"` opts this source into single-quote quoted-field handling, so a field like `'Doe, John'` is treated as one value even though it contains the delimiter.
- inside a quoted CSV token, escape the quote character by doubling it, for example `obrien''s@example.com` when `parser.quoteCharacter` is `'`.
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

The important authoring rule is choice, not accumulation:

- keep only the minimum CSV fields when the feed is simple
- add `validation` only when file-level checks are needed
- add `archive` only when the original file should be moved after success
- add `parser.quoteCharacter` only when the incoming CSV contract needs it
- do not author `packageName`; runtime derives package identity internally and now rejects the property when it is present

## Usage notes

- `sourceName` must match the `processor.mappings[].source` value used by the selected processor.
- The order of `fields` should match the order of columns in the CSV file.
- `skipHeader: true` tells the CSV reader to skip the first line as a header row; this is the default for backward compatibility and for intermediate CSV handoff files that are written with `includeHeader: true`.
- Set `skipHeader: false` for headerless CSV sources so the first line is treated as data.
- `parser.quoteCharacter` is optional and must be exactly one character when configured.
- When `parser.quoteCharacter` is omitted, the active reader keeps Spring Batch's default `"`-quoted CSV behavior.
- When `parser.quoteCharacter` is configured, the active reader and CSV header validator both use that quote character for delimiter-safe tokenization and doubled-quote escaping.
- When `validation` is present, the CSV source validator checks the configured file path before execution and can fail fast for missing/unreadable files, header-only files, or header mismatches.
- `validation.requireHeaderMatch=true` is only valid when `skipHeader=true` because header matching assumes the first line is a real header row.
- `validation.fileNamePattern` checks only the file name portion, not the full path.
- If `validation.onFailure=rejectFile`, a CSV file that fails file-level validation is moved to `validation.rejectPath` and the run still surfaces a validation error with the rejected-file location in the message/logs.
- The current record count implementation subtracts one row only when `skipHeader=true`.
- Archive behavior is part of the shared file-source contract and applies after successful step completion when enabled for the active source config.
- If archive is enabled, `archive.successPath` is required.
- The preserved first-slice example is `src/main/resources/config-jobs/csv-validation-reject-archive/source-config.yaml`.
- For explicit job-config runs, the runtime always derives scenario/job-scoped generated classes such as `com.etl.generated.job.<normalized-job-name>.source`.
- Source YAML no longer supports authored `packageName`; direct-config/demo fallback still applies internal `com.etl.model.source` only after loading a package-free config.

## Current limitations

- No generic backslash-style escape contract; the active CSV parser supports quoted fields and doubled quote-character escaping only
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


