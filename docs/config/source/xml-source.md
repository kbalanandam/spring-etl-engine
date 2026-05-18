# XML Source Config

## Purpose

`XmlSourceConfig` defines a file-based XML source selected by a `job-config.yaml` step.

It covers three runtime shapes behind one YAML contract:

- `DirectXml` for simple repeating-record XML
- `NestedXml` for nested XML that must be flattened before processor mapping
- `JobSpecificXml` for job-owned custom flattening logic

The repo standard is to keep the same top-level YAML shape for both simple and nested XML, and let `flatteningStrategy` describe runtime behavior.

## Java contract

Backed by:
- `src/main/java/com/etl/config/source/XmlSourceConfig.java`
- `src/main/java/com/etl/config/source/validation/XmlSourceValidator.java`
- `src/main/java/com/etl/reader/impl/XmlDynamicReader.java`
- `src/main/java/com/etl/common/util/JobScopedPackageNameResolver.java`

## Supported fields today

| Field | Required | Type | Description |
|---|---|---|---|
| `format` | yes | string | Must be `xml` |
| `sourceName` | yes | string | Logical source name matched by `processor.mappings[].source` |
| `filePath` | yes | string | XML input file path, or a ZIP file path containing the readable XML artifact |
| `unzip` | no | object | Optional advanced ZIP override block; most ZIP-backed XML sources only need `filePath: ...zip` |
| `unzip.enabled` | no | boolean | Optional compatibility flag; ZIP-backed `filePath` values are prepared automatically even when this is omitted, and authored `true` now requires `filePath` to reference a `.zip` artifact |
| `unzip.extractDir` | no | string | Optional override for where the extracted XML file is staged; when omitted the runtime uses a runtime-owned JVM temp working directory |
| `unzip.entryName` | no | string | Optional entry selector for multi-file ZIPs; only needed when the ZIP contains more than one file entry |
| `archive` | no | object | Optional archive-on-success settings for file-based XML sources |
| `archive.enabled` | yes, when `archive` is present | boolean | Enables moving the original file after successful step completion |
| `archive.successPath` | yes, when `archive.enabled=true` | string | Destination directory for the archived source file |
| `archive.namePattern` | no | string | Archive name pattern supporting `{originalName}` and `{timestamp}` |
| `archive.packageAsZip` | no | boolean | When `true`, the runtime packages the archived XML artifact into one ZIP file instead of moving the plain file directly |
| `rootElement` | yes | string | Expected document root element |
| `recordElement` | yes | string | Repeating fragment element treated as one runtime record |
| `flatteningStrategy` | no | string | `DirectXml`, `NestedXml`, or `JobSpecificXml`. Defaults to `DirectXml` |
| `jobSpecificStrategyBean` | no | string | Required only when `flatteningStrategy: JobSpecificXml` |
| `modelDefinitionPath` | no | string | External XML model-definition YAML used as the structural contract, especially for nested XML |
| `validation` | no | object | Optional file-level XML validation block |
| `validation.fileNamePattern` | no | string | Regex applied to the file name only |
| `validation.schemaPath` | no | string | Optional XSD path for strict schema validation before normal read/flatten/write processing |
| `validation.onFailure` | no | string | `failStep` or `rejectFile` |
| `validation.rejectPath` | yes, when `validation.onFailure=rejectFile` | string | Directory where an invalid XML file is moved |
| `fields` | no | list | Inline flat field definition block. Use this mainly for simple/compatibility XML when you are not using `modelDefinitionPath` |
| `fields[].name` | yes, when `fields` is present | string | Property name exposed on the generated/read record model |
| `fields[].type` | yes, when `fields` is present | string | Logical type stored in the generated model contract |

## Authoring standard for this repo

Prefer one consistent XML source shape for both simple and nested XML:

- always start with `format`, `sourceName`, `filePath`, `rootElement`, and `recordElement`
- point `filePath` at either the plain XML file or the ZIP artifact; add `unzip` only when you need an advanced ZIP override such as `entryName` or a custom extract directory
- use `flatteningStrategy` to explain runtime behavior instead of inventing different YAML layouts
- prefer `modelDefinitionPath` as the structural source of truth for new XML scenarios
- keep inline `fields` only for intentionally simple or compatibility-style flat XML configs
- do not author `packageName`; runtime/build-time derive package identity internally and now reject the property when it is present

## Minimum valid shape

At minimum, define:

- `format`
- `sourceName`
- `filePath`
- `rootElement`
- `recordElement`
- one structural contract source: either `fields` or `modelDefinitionPath`

Minimal flat XML example:

```yaml
sources:
  - format: xml
    sourceName: Events
    filePath: input/events.xml
    rootElement: Events
    recordElement: Event
    fields:
      - name: eventCode
        type: String
      - name: eventTime
        type: String
```

Use that shape only when you want the simplest flat XML contract in one file.

## Preferred patterns

### Pattern A — direct/simple XML with derived package name

Use this for one repeating XML fragment = one runtime record.

```yaml
sources:
  - format: xml
    sourceName: Events
    filePath: input/events.zip
    rootElement: Events
    recordElement: Event
    flatteningStrategy: DirectXml
    modelDefinitionPath: definitions/events-source-model.yaml
```

Notes:

- no authored `packageName` appears because package identity is derived internally
- runtime derives `com.etl.generated.job.<normalized-job-name>.source`
- `flatteningStrategy` could also be omitted here because the default is `DirectXml`, but keeping it explicit is clearer

This matches the preserved job style in:

- `src/main/resources/config-jobs/xml-to-json-events/source-config.yaml`

### Pattern B — direct/simple XML with optional validation and archive

Use this when the same simple XML flow also needs file-level checks or source-file archiving.

```yaml
sources:
  - format: xml
    sourceName: Events
    filePath: input/events.xml
    rootElement: Events
    recordElement: Event
    flatteningStrategy: DirectXml
    modelDefinitionPath: definitions/events-source-model.yaml
    archive:
      enabled: true
      successPath: output/archive/success/
      namePattern: "{originalName}-{timestamp}"
    validation:
      fileNamePattern: '^Events-\d{8}\.xml$'
      onFailure: rejectFile
      rejectPath: rejects/
      # schemaPath: schemas/events.xsd
```

Notes:

- `validation` is optional
- `validation.schemaPath` is optional
- if `schemaPath` is not present, the runtime still validates file existence, XML well-formedness, configured `rootElement`, configured `recordElement`, and optional file-name rules
- `archive.successPath` becomes required only when `archive.enabled=true`

### Pattern C — nested XML with shared flattening

Use this when the XML fragment contains nested objects and downstream processor mappings need flattened keys.

```yaml
sources:
  - format: xml
    sourceName: TagValidationSource
    filePath: input/tag-validation-sample.xml
    rootElement: TagValidationList
    recordElement: TVLTagDetails
    flatteningStrategy: NestedXml
    modelDefinitionPath: definitions/nested-source-model.yaml
```

Notes:

- the YAML shape is the same as simple XML
- the only important runtime difference is `flatteningStrategy: NestedXml`
- keep the nested structure in `modelDefinitionPath`; do not duplicate a partial nested structure in inline `fields`
- for selected-job bundles, the external `modelDefinitionPath` YAML should preserve structure only; do not author `packageName` there because job-scoped generation injects the derived package internally

This mirrors preserved bundle patterns such as:

- `src/main/resources/config-jobs/xml-nested-to-csv-tag-validation/source-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/source-config.yaml`

### Pattern D — job-specific XML flattening

Use this only when `NestedXml` is not enough and one job needs custom extraction logic.

```yaml
sources:
  - format: xml
    sourceName: PartnerOrders
    filePath: input/partner-orders.xml
    rootElement: PartnerOrderEnvelope
    recordElement: PartnerOrder
    flatteningStrategy: JobSpecificXml
    jobSpecificStrategyBean: partnerOrdersXmlStrategy
    modelDefinitionPath: definitions/partner-orders-model.yaml
```

Notes:

- `jobSpecificStrategyBean` matters only for `JobSpecificXml`
- this is still the same XML source contract; only runtime extraction changes
- prefer `DirectXml` or `NestedXml` first, and use `JobSpecificXml` only when a shared strategy cannot represent the source correctly

### Pattern E — flat inline-fields form

This remains supported for older/simple flat XML jobs.

```yaml
sources:
  - format: xml
    sourceName: Events
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

This mirrors:

- `src/main/resources/config-jobs/xml-to-csv-events/source-config.yaml`

## Field-by-field walkthrough

- `sources:` is the required root for the source YAML file.
- `format: xml` selects the XML reader path.
- `sourceName` is the name referenced from processor mappings and `job-config.yaml` steps.
- `filePath` points to the XML file to read, or to a ZIP artifact that contains one readable XML file.
- ZIP-backed `filePath` values are prepared automatically before XML validation, record counting, and reading.
- `unzip` is optional and only needed when the ZIP needs an advanced override such as a custom staging directory or `unzip.entryName`.
- if `unzip.enabled: true` is authored explicitly, `filePath` must still point to a `.zip` artifact; using `unzip.enabled=true` with a plain `.xml` path now fails fast during source validation and again on the shared runtime preparation path if validation is bypassed.
- `unzip.extractDir` overrides where the runtime stages the extracted readable XML file; when omitted the runtime uses a runtime-owned JVM temp working directory.
- if `unzip.entryName` is omitted, the ZIP must contain exactly one file entry; otherwise set `unzip.entryName` to the entry the runtime should extract.
- `rootElement` is the expected XML document root.
- `recordElement` is the fragment counted and streamed as one runtime record.
- `flatteningStrategy` defaults to `DirectXml` when omitted.
- `jobSpecificStrategyBean` is only relevant for `JobSpecificXml`.
- `modelDefinitionPath` is optional but preferred for new XML jobs, especially nested XML jobs.
- `fields` is optional and mainly for simple inline flat contracts.
- `archive` is optional and uses the shared file-source archive behavior.
- `archive.packageAsZip: true` packages the archived XML file into a ZIP artifact through the shared ZIP utility instead of moving the plain file directly.
- when `archive.packageAsZip: true`, the runtime keeps the original XML file name as the single ZIP entry name and appends `.zip` to the outer archive file name when needed.
- `validation` is optional.
- `validation.fileNamePattern` validates only the file name, not the full path.
- `validation.schemaPath` is optional; add it only when the file must pass XSD validation before processing.
- `validation.onFailure: rejectFile` moves the whole file to `validation.rejectPath` and still fails the run with a clear validation error.
- `validation.rejectPath` is required only when `onFailure=rejectFile`.

## Runtime behavior today

All XML source variants start with the same orchestration path:

- `BatchConfig` assembles the selected step
- `GeneratedModelClassResolver` verifies generated classes for the selected job
- `DynamicReaderFactory` picks `XmlDynamicReader`
- `XmlDynamicReader` branches by `flatteningStrategy`

### `DirectXml`

- streams `recordElement` fragments through the normal XML reader path
- emits generated XML record objects directly to the processor
- best fit for simple repeating-record XML

### `NestedXml`

- still streams by `recordElement`
- unmarshals each fragment and passes it through the shared nested XML strategy
- emits flattened `Map<String, Object>` rows for downstream processor mappings
- best fit when processor mappings need flattened dotted access derived from nested XML

### `JobSpecificXml`

- uses the custom strategy-bean seam instead of the shared nested strategy
- keeps custom XML extraction in one job-owned component rather than spreading conditionals across the runtime

## Path and package rules

- In explicit `job-config.yaml` mode, relative paths resolve from the folder containing the referenced config file, not from the repo root.
- That rule applies to `filePath`, `modelDefinitionPath`, `validation.schemaPath`, `validation.rejectPath`, and `archive.successPath`.
- It also applies to `unzip.extractDir`.
- In explicit job mode, the runtime derives a stable package using the selected non-blank job name from `job-config.yaml`.
- The derived default is `com.etl.generated.job.<normalized-job-name>.source`.

## `packageName` direction

XML source YAML is now package-free on every supported runtime path.

Current guidance:

- for explicit job bundles, runtime/build-time derive `com.etl.generated.job.<normalized-job-name>.source` from `job-config.yaml -> name`
- for direct-config/demo fallback, runtime applies internal `com.etl.model.source` only after loading a package-free config
- do not author `packageName`; the runtime now fails fast when the property is present

## Validation and usage notes

- `sourceName` must match the selected `processor.mappings[].source` entry.
- Keep `rootElement` and `recordElement` aligned with the real XML document; mismatches fail during source validation or lead to empty reads.
- When `filePath` ends in `.zip`, XML validation and reading use the extracted XML file automatically, but archive/reject disposition still applies to the original configured source artifact.
- If `unzip.enabled=true` is authored explicitly, `filePath` must end in `.zip`; explicit unzip is not a supported override for plain XML files.
- If `archive.packageAsZip=true`, the archived output is one ZIP file containing the original source file as a single entry.
- `archive.packageAsZip=true` is only supported for plain file-backed sources; if `filePath` already points to a `.zip` source artifact, the runtime fails fast instead of producing a double-zipped archive.
- When `unzip.extractDir` is omitted, the default prepared XML file is staged under a runtime-owned JVM temp work root instead of beside the ingress artifact.
- After successful step completion or reject-file validation cleanup, the runtime deletes the prepared extracted file and prunes any now-empty runtime-owned default prepared directories.
- For `DirectXml`, runtime validation expects generated XML classes for the configured source package.
- For `NestedXml`, the generated record class is still required even though the active runtime path does not depend on a root-wrapper class in the same way.
- Use `modelDefinitionPath` for new nested XML jobs; it is the clearest place to preserve nested structure.
- XML duplicate handling still lives in processor rules, not in a separate XML-source duplicate block.

## Current limitations

- No namespace-aware XML config fields yet
- No XPath-based record selection block yet
- No source-level XML duplicate contract yet
- Inline `fields` works only for flat/simple structures; nested structures should stay in `modelDefinitionPath`
- No multi-entry ZIP extraction policy beyond `unzip.entryName` selecting one file entry

## Related design note

The broader file-ingestion hardening direction, including future XML-oriented validation and malformed-input growth, is documented in [`File ingestion hardening`](../../architecture/file-ingestion-hardening.md).

The parser-boundary rule for what XML parsing should and should not own is documented in [`OneFlow file parser capabilities and boundaries`](../../architecture/oneflow-file-parser-capabilities.md).

## Build-time generation command

Use the XML generation profile when the selected job needs generated XML classes:

```powershell
mvn --no-transfer-progress -Pxml-generation -Detl.xml.generation.jobConfig=src/test/resources/config-jobs/xml-build-generation-it/job-config.yaml clean test
```

When the profile is enabled:

- Maven adds `target/generated-sources/etl/source` and `target/generated-sources/etl/target` as source roots
- generated XML model classes are produced for the selected job
- Maven compiles those generated classes before test execution and packaging

## Preserved examples

- `src/main/resources/config-jobs/xml-to-csv-events/source-config.yaml`
- `src/main/resources/config-jobs/xml-to-json-events/source-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-tag-validation/source-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/source-config.yaml`

## Related docs

- [`CSV source reference`](csv-source.md)
- [`Default processor reference`](../processor/default-processor.md)
- [`File ingestion hardening`](../../architecture/file-ingestion-hardening.md)




