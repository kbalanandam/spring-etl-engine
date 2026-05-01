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
| `packageName` | yes | string | Package used for generated source model naming; runtime validates that the generated XML record class exists in this package during startup, and non-`NestedXml` source paths also require the generated XML root class |
| `filePath` | yes | string | XML file path |
| `rootElement` | yes | string | Expected top-level XML container element for the file |
| `recordElement` | yes | string | Repeating XML element name used for record counting and streaming reads |
| `flatteningStrategy` | no | string | XML source flattening strategy. Supported values today: `DirectXml`, `NestedXml`, `JobSpecificXml`. Defaults to `DirectXml`. |
| `jobSpecificStrategyBean` | no | string | Spring bean name used when `flatteningStrategy` is `JobSpecificXml` |
| `modelDefinitionPath` | no | string | External structural XML model definition YAML used by the build-time generator, especially for nested XML contracts |
| `validation` | no | object | Optional XML file-level validation extensions |
| `validation.fileNamePattern` | no | string | Optional regex the source file name must match |
| `validation.onFailure` | no | string | Optional file-level failure behavior: `failStep` or `rejectFile` |
| `validation.rejectPath` | yes, when `validation.onFailure=rejectFile` | string | Directory where an invalid XML source file is moved before the run fails |
| `fields` | yes | list | Ordered list of record properties expected on the generated source model |
| `fields[].name` | yes | string | XML-backed property name expected on each record object |
| `fields[].type` | yes | string | Logical type used in the generated model contract |

## Example

This mirrors `src/main/resources/config-scenarios/xml-to-csv-events/source-config.yaml`.

```yaml
sources:
  - format: xml
    sourceName: Events
    packageName: com.etl.model.source.xml
    filePath: src/main/resources/demo-input/Events.xml
    rootElement: Events
    recordElement: Event
    validation:
      fileNamePattern: '^Events-\d{8}\.xml$'
      onFailure: rejectFile
      rejectPath: target/rejected-files/
    flatteningStrategy: DirectXml
    modelDefinitionPath: definitions/events-source-model.yaml
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
- `DirectXml` preserves the current streaming reader path and emits record objects backed by the generated XML record class.
- `NestedXml` and `JobSpecificXml` switch the XML reader to a source-flattening path that unmarshals the XML root, applies the configured XML source strategy, and emits flattened row maps for downstream processor mapping.
- `modelDefinitionPath` is optional and is used by the build-time XML generation slice when the XML structure needs an external structural contract, especially for nested XML.
- For explicit job execution, startup now fails fast if the generated XML record class is missing from the configured `packageName`.
- Non-`NestedXml` XML source paths still require the generated XML root class during startup.
- `NestedXml` source paths do not require the XML source root wrapper class during generated-model validation because the active runtime path flattens from the generated record model.
- `getRecordCount()` counts XML start elements matching `recordElement`.
- `rootElement` is preserved as part of the config contract and should match the real XML envelope even though the current reader path streams individual `recordElement` fragments.
- `validation.fileNamePattern` checks only the file name portion, not the full path.
- If `validation.onFailure=rejectFile`, an XML file that fails file-level validation is moved to `validation.rejectPath` and the run still surfaces a validation error with the rejected-file location in the message/logs.
- Property names in `fields` must align with the generated/read XML record model used by the reader and processor. For flattened nested XML, processor mappings may also use flattened keys such as `TVLPlateDetails.PlateCountry`.

## Validation / usage notes

- `sourceName` must match the selected `processor.mappings[].source` value.
- `packageName`, `rootElement`, and `recordElement` must line up with the generated XML classes that Maven compiled for the selected job.
- For `NestedXml`, the generated record class is still required even when the XML source root wrapper class is not.
- Keep `rootElement` and `recordElement` aligned with the actual XML structure; mismatches typically surface as empty reads, count mismatches, or unmarshalling failures.
- Duplicate handling for XML should currently be configured through the shared processor-level `duplicate` rule after XML records are read into flat runtime objects; there is no separate XML-source duplicate block in the shipped config contract.
- Use `DirectXml` for flat repeating-record XML feeds first. Use `NestedXml` only when a shared nested flattening rule is sufficient; otherwise use `JobSpecificXml` with an explicit strategy bean.
- Relative file paths are resolved by the surrounding runtime/config selection path, just like other source config files.

## Current limitations

- No XML-specific file-level validation block yet like the CSV `validation` block
- No XML archive-on-success support yet
- No first-class nested field alias block yet; nested flattening currently relies on processor mappings against emitted flat keys or strategy-provided field mappings
- No XML-native duplicate config yet for XPath selectors, namespace-aware key extraction, or source-level duplicate checks before record mapping
- No alternate XPath-based record selection or namespace-aware config fields yet
- Current XML support still defaults to flat repeated record elements; nested support is opt-in through the XML source flattening strategy seam

## Build-time generation command

The first job-scoped build-time XML generation slice can be invoked through the opt-in Maven profile:

```powershell
mvn --no-transfer-progress -Pxml-generation -Detl.xml.generation.jobConfig=src/test/resources/config-scenarios/xml-build-generation-it/job-config.yaml clean test
```

When that profile is enabled:

- Maven adds `target/generated-sources/etl/source` and `target/generated-sources/etl/target` as source roots
- the build-time XML generation entrypoint runs after main classes compile
- generated XML source and target model classes are written into those generated roots
- Maven compiles those generated classes before test execution and packaging

## Preserved examples

- `src/main/resources/config-scenarios/xml-to-csv-events/source-config.yaml`
- `src/main/resources/config-scenarios/xml-nested-to-csv-tag-validation/source-config.yaml`
- `src/main/resources/config-scenarios/xml-nested-tag-validation/source-config.yaml`

## Related docs

- [`csv-source.md`](csv-source.md)
- [`../processor/default-processor.md`](../processor/default-processor.md)
- [`../../architecture/file-ingestion-hardening.md`](../../architecture/file-ingestion-hardening.md)



