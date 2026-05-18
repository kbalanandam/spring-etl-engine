# XML Target Config

## Purpose

`XmlTargetConfig` defines a file-based XML target.

It is the current XML writer path used for XML output scenarios such as `customer-load`, `department-load`, and other file-based runs that emit XML artifacts.

## Java contract

Backed by:
- `src/main/java/com/etl/config/target/XmlTargetConfig.java`
- `src/main/java/com/etl/writer/impl/XmlDynamicWriter.java`

## Supported fields today

| Field | Required | Type | Description |
|---|---|---|---|
| `format` | yes | string | Must be `xml` |
| `targetName` | yes | string | Logical target name used in processor mapping lookup |
| `packageName` | no in explicit job mode; otherwise yes | string | Deprecated bridge field for generated target model naming; runtime now validates that the configured XML root and record classes exist in this package during startup. When omitted for an explicit `job-config.yaml` run, the runtime and build-time generation path derive `com.etl.generated.job.<normalized-job-name>.target` |
| `filePath` | yes | string | XML output file path or output directory |
| `packageAsZip` | no | boolean | When `true`, the runtime packages the successful XML output as one ZIP artifact and appends `.zip` to the published path when needed |
| `rootElement` | yes | string | Root container element for the generated XML document |
| `recordElement` | yes | string | Record element name used for repeated XML items |
| `fields` | yes for flat XML targets; no when `modelDefinitionPath` is the structural source of truth | list | Ordered list of target properties expected on the generated target model |
| `modelDefinitionPath` | no | string | Optional structural XML target model definition used when the target shape must be generated from a nested XML contract rather than only from inline `fields` |
| `fields[].name` | yes, when `fields` is present | string | Property name written into the generated XML record/wrapper |
| `fields[].type` | yes, when `fields` is present | string | Logical type used in the generated target model contract |

## Recommended standard template

For new XML target scenarios in this repo, prefer one shared authoring pattern for both simple and nested XML targets:

- keep the top-level XML target fields the same: `format`, `targetName`, `filePath`, `rootElement`, `recordElement`
- add `packageAsZip` only when the final XML artifact should be published as a ZIP file
- use `modelDefinitionPath` when the target shape is nested or should come from a structural XML definition
- keep `fields` for simple flat XML targets; when `modelDefinitionPath` is present for nested XML, prefer the definition file as the single structural source of truth

### Minimum required shape

At minimum, an XML target must declare:

- `format`
- `targetName`
- `filePath`
- `rootElement`
- `recordElement`
- and one structural contract source: either inline `fields` or `modelDefinitionPath`

Minimal flat XML target example:

```yaml
targets:
  - format: xml
    targetName: Customers
    filePath: output/customers.xml
    rootElement: Customers
    recordElement: Customer
    fields:
      - name: id
        type: String
      - name: name
        type: String
```

Use that shape when you want the simplest valid XML target config and do not need an external nested target definition.

### Preferred explicit-job pattern

For preserved bundles and new explicit `job-config.yaml` scenarios, prefer an explicit runtime shape with a clear structural source of truth:

```yaml
targets:
  - format: xml
    targetName: YourTargetName
    filePath: output/your-output.xml
    rootElement: RootElementName
    recordElement: RecordElementName
    fields:
      - name: id
        type: String
```

`packageName` is optional in explicit job mode and should be treated as a deprecated bridge field. When omitted, the runtime derives `com.etl.generated.job.<normalized-job-name>.target` automatically.

Use this simple/flat XML variant when one runtime target record maps directly to one repeated XML output element:

```yaml
targets:
  - format: xml
    targetName: Customers
    filePath: output/customers.xml
    rootElement: Customers
    recordElement: Customer
    fields:
      - name: id
        type: int
      - name: name
        type: String
      - name: email
        type: String
```

Use this nested XML variant when the output shape should be generated from a structural XML definition rather than only from the flat `fields` list:

```yaml
targets:
  - format: xml
    targetName: CustomersNestedXml
    filePath: output/customers-nested.xml
    rootElement: Customers
    recordElement: CustomerRecord
    modelDefinitionPath: definitions/nested-target-model.yaml
```

Treat that nested form as the preferred pattern when the XML output contains structure such as `profile.email` or `address.city`. The structural target contract belongs in the referenced `modelDefinitionPath`, while the top-level target YAML stays aligned with the same authoring shape used for simpler XML targets.

## Example

### Example A — flat XML target with derived package name

This is the current preserved flat XML target pattern:

```yaml
targets:
  - format: xml
    targetName: Customers
    filePath: output/customers.xml
    rootElement: Customers
    recordElement: Customer
    fields:
      - name: id
        type: int
      - name: name
        type: String
      - name: email
        type: String
```

This mirrors the preserved bundle:

- `src/main/resources/config-jobs/customer-load/target-config.yaml`

### Example B — explicit job mode with derived package name

Use this when the default job-scoped target package is acceptable and you do not want to author `packageName` explicitly:

```yaml
targets:
  - format: xml
    targetName: EventsXml
    filePath: output/events.xml
    packageAsZip: true
    rootElement: Events
    recordElement: Event
    fields:
      - name: eventCode
        type: String
      - name: eventTime
        type: String
```

In that example:

- `packageName` is intentionally omitted
- the runtime derives it automatically in explicit job mode
- the flat target shape still comes from inline `fields`

### Example C — nested XML target with structural definition

Use this when the output shape should come from an external nested XML model definition instead of inline target `fields`:

```yaml
targets:
  - format: xml
    targetName: CustomersNestedXml
    filePath: output/customers-nested.xml
    rootElement: Customers
    recordElement: CustomerRecord
    modelDefinitionPath: definitions/nested-target-model.yaml
```

This mirrors the preserved bundle:

- `src/main/resources/config-jobs/csv-to-nested-xml/target-config.yaml`

## Example walkthrough

Read the examples in output-contract order:

- `targets:` is the required root for target config files.
- `format: xml` selects the XML writer path.
- `targetName` is the logical target identity referenced by processor mappings and job steps.
- `packageName` is a deprecated bridge field for the generated target package validated during startup; in explicit job mode prefer omitting it to use the job-scoped default package.
- `filePath` is the XML output artifact path or output directory.
- `packageAsZip` is optional; when `true`, the successful XML artifact is published as one ZIP file containing the XML document as a single entry.
- `rootElement` is the document envelope element written around the output.
- `recordElement` is the repeated XML item element name expected by the writer path.
- `fields` lists the target object properties that the writer marshals for the flat XML path.
- `fields[].name` is the generated target property name used during XML marshalling.
- `fields[].type` is the logical type recorded in the generated target model contract.
- `modelDefinitionPath` is optional; use it when an external XML definition should be the structural source of truth for nested XML output.

The important authoring rule is choice, not duplication:

- use inline `fields` when you want the simplest flat XML target contract in one file
- use `modelDefinitionPath` when an external target model definition should be authoritative
- add `packageAsZip: true` only when the successful XML output should be zipped after step completion
- omit `packageName` in explicit job mode when the default job-scoped package is acceptable
- avoid duplicating overlapping structural fields in both places for nested XML targets

## Runtime behavior today

- The target config file root is `targets:`.
- The same top-level XML target contract is used for both simple and nested XML outputs: `format`, `targetName`, `filePath`, `rootElement`, `recordElement`, plus optional deprecated-bridge `packageName`, optional `fields`, and optional `modelDefinitionPath`.
- If `filePath` ends with `/` or points to an existing directory, the writer appends `<targetName>.xml` using a lowercase target name.
- When the generated target class simple name matches `recordElement`, the runtime streams individual XML record elements under the configured `rootElement`.
- Otherwise the writer falls back to wrapper/single-object XML output.
- When `packageAsZip=true`, either XML writer mode packages the successful XML file into one ZIP artifact after step completion instead of leaving the plain XML file as the published result.
- When `modelDefinitionPath` is provided, job-scoped XML generation uses that structural definition to build nested target model classes for the selected scenario.
- For explicit job execution, startup now fails fast if the generated XML root or record classes are missing from the configured `packageName`.
- `fields[].name` values must align with the generated target object properties used during marshalling when the flat `fields` block is present.

## Validation / usage notes

- `targetName` must match the selected `processor.mappings[].target` value.
- `packageName`, `rootElement`, and `recordElement` must line up with the generated XML classes that Maven compiled for the selected job.
- In explicit job mode, `packageName` may be omitted and defaults to `com.etl.generated.job.<normalized-job-name>.target`.
- Treat explicit `packageName` as a deprecated compatibility bridge on the active path, not as the preferred authoring style for new XML targets.
- Use `modelDefinitionPath` when the XML target must contain nested object structure such as `profile.email` or `address.city` rather than only flat record fields.
- Keep the top-level target YAML consistent between simple and nested XML targets so scenario authors only switch the structural source of truth, not the overall target config shape.
- For nested XML targets, keep the structural shape in the referenced model definition rather than duplicating overlapping fields in both places.
- If `modelDefinitionPath` is omitted, provide `fields` so flat XML target classes can still be derived directly from `target-config.yaml`.
- Keep `rootElement` and `recordElement` aligned with the XML structure expected by downstream consumers.
- Use `packageAsZip=true` when downstream publication expects a compressed XML artifact while keeping the same XML payload contract inside the ZIP.
- Use a directory-style `filePath` when you want runtime naming from `targetName`; use a file path when the artifact name must be fixed.
- For chunk-oriented XML output, keep `recordElement` aligned with the generated record class name expected by the writer path.

## Current limitations

- No XML namespace-specific config fields yet
- No pretty-print/indent/output-format toggle yet
- No append/merge behavior for existing XML outputs
- No per-field XML aliasing yet beyond what the generated structural target model exposes
- Current support is strongest for flat repeated-record XML outputs, with preserved nested target support now exercised through scenario-scoped `modelDefinitionPath`

## Preserved examples

- `src/main/resources/config-jobs/customer-load/target-config.yaml`
- `src/main/resources/config-jobs/department-load/target-config.yaml`
- `src/main/resources/config-jobs/cust-dept-load/target-config.yaml`
- `src/main/resources/config-jobs/csv-to-nested-xml/target-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml/target-config.yaml`
- `src/main/resources/config-jobs/xml-nested-to-csv-to-nested-xml-archive-e2e/target-config.yaml`

## Related docs

- [`CSV target reference`](csv-target.md)
- [`JSON target reference`](json-target.md)
- [`Relational target reference`](relational-target.md)
- [`CSV source reference`](../source/csv-source.md)
- [`Default processor reference`](../processor/default-processor.md)


