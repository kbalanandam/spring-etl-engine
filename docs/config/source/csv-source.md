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
| `fields` | yes | list | Ordered list of CSV columns |
| `fields[].name` | yes | string | Field/property name |
| `fields[].type` | yes | string | Logical type used in generated model contract |

## Example

```yaml
sources:
  - format: csv
    sourceName: Customers
    packageName: com.etl.model.source
    filePath: src/main/resources/demo-input/Customers.csv
    delimiter: ","
    fields:
      - name: id
        type: int
      - name: name
        type: String
      - name: email
        type: String
```

## Usage notes

- `sourceName` must match the `processor.mappings[].source` value used by the selected processor.
- The order of `fields` should match the order of columns in the CSV file.
- The current CSV reader skips the first line as a header row.
- The current record count implementation counts file rows and subtracts one for the header.

## Current limitations

- No custom quote/escape configuration yet
- No per-column source alias support yet
- No schema inference; fields must be declared explicitly
- No alternate header mapping rules yet
- No archive/lifecycle handling fields in the shipped CSV source contract yet

## Proposed next-slice design note

The proposed design for processed-file archiving and the broader file-ingestion hardening slice is documented in [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md). That note is forward-looking and is not yet part of the current shipped CSV config contract.

## Related docs

- [`../processor/default-processor.md`](../processor/default-processor.md)
- [`../target/relational-target.md`](../target/relational-target.md)

