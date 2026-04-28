# XML Source Config

## Purpose

`XmlSourceConfig` defines a file-based XML source.

It supports the current XML reader path used for staged XML file ingestion, including record counting for chunk/tasklet decisions and flat record extraction through a configured record element.

## Java contract

Backed by:
- `src/main/java/com/etl/config/source/XmlSourceConfig.java`
- `src/main/java/com/etl/reader/impl/XmlDynamicReader.java`

## Supported fields today

| Field | Required | Type | Description |
|---|---|---|---|
| `format` | yes | string | Must be `xml` |
| `sourceName` | yes | string | Logical source name used in processor mapping lookup |
| `packageName` | yes | string | Package used for generated source model naming |
| `filePath` | yes | string | XML file path |
| `rootElement` | yes | string | Expected top-level XML container element for the file |
| `recordElement` | yes | string | Repeating XML element name used for record counting and streaming reads |
| `fields` | yes | list | Ordered list of record properties expected on the generated source model |
| `fields[].name` | yes | string | XML-backed property name expected on each record object |
| `fields[].type` | yes | string | Logical type used in the generated model contract |

## Example

This mirrors `src/main/resources/config-scenarios/xml-to-csv-events/source-config.yaml`.

```yaml
sources:
  - format: xml
    sourceName: Events
    packageName: com.etl.model.source
    filePath: src/main/resources/demo-input/Events.xml
    rootElement: Events
    recordElement: Event
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

- The source config file root is `sources:`.
- `recordElement` is the key runtime field for XML streaming; the XML reader uses it as the fragment root element name.
- `getRecordCount()` counts XML start elements matching `recordElement`.
- `rootElement` is preserved as part of the config contract and should match the real XML envelope even though the current reader path streams individual `recordElement` fragments.
- Property names in `fields` must align with the generated/read XML record model used by the reader and processor.

## Validation / usage notes

- `sourceName` must match the selected `processor.mappings[].source` value.
- Keep `rootElement` and `recordElement` aligned with the actual XML structure; mismatches typically surface as empty reads, count mismatches, or unmarshalling failures.
- Duplicate handling for XML should currently be configured through the shared processor-level `duplicate` rule after XML records are read into flat runtime objects; there is no separate XML-source duplicate block in the shipped config contract.
- Use XML source configs for flat repeating-record XML feeds first; nested/complex XML structures are still more constrained.
- Relative file paths are resolved by the surrounding runtime/config selection path, just like other source config files.

## Current limitations

- No XML-specific file-level validation block yet like the CSV `validation` block
- No XML archive-on-success support yet
- No nested field mapping/alias contract yet
- No XML-native duplicate config yet for XPath selectors, namespace-aware key extraction, or source-level duplicate checks before record mapping
- No alternate XPath-based record selection or namespace-aware config fields yet
- Current XML support is best suited to flat repeated record elements

## Preserved examples

- `src/main/resources/config-scenarios/xml-to-csv-events/source-config.yaml`

## Related docs

- [`csv-source.md`](csv-source.md)
- [`../processor/default-processor.md`](../processor/default-processor.md)
- [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md)



