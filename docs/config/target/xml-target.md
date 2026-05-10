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
| `packageName` | no in explicit job mode; otherwise yes | string | Package used for generated target model naming; runtime now validates that the configured XML root and record classes exist in this package during startup. When omitted for an explicit `job-config.yaml` run, the runtime and build-time generation path derive `com.etl.generated.job.<normalized-job-name>.target` |
| `filePath` | yes | string | XML output file path or output directory |
| `rootElement` | yes | string | Root container element for the generated XML document |
| `recordElement` | yes | string | Record element name used for repeated XML items |
| `fields` | yes | list | Ordered list of target properties expected on the generated target model |
| `modelDefinitionPath` | no | string | Optional structural XML target model definition used when the target shape must be generated as nested XML rather than only from the flat `fields` list |
| `fields[].name` | yes | string | Property name written into the generated XML record/wrapper |
| `fields[].type` | yes | string | Logical type used in the generated target model contract |

## Recommended standard template

For new XML target scenarios in this repo, prefer one shared authoring pattern for both simple and nested XML targets:

- keep the top-level XML target fields the same: `format`, `targetName`, `packageName`, `filePath`, `rootElement`, `recordElement`, `fields`
- use `modelDefinitionPath` when the target shape is nested or should come from a structural XML definition
- keep `fields` on the active target contract today; for simple XML they remain the main flat target shape, and for nested XML they remain part of the current generated target config even when the structural shape is defined externally

Preferred baseline template:

```yaml
targets:
  - format: xml
    targetName: YourTargetName
    packageName: com.etl.generated.job.yourscenario.target
    filePath: output/your-output.xml
    rootElement: RootElementName
    recordElement: RecordElementName
    fields:
      - name: id
        type: String
```

Use this simple/flat XML variant when one runtime target record maps directly to one repeated XML output element:

```yaml
targets:
  - format: xml
    targetName: Customers
    packageName: com.etl.generated.job.customerload.target
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
    packageName: com.etl.generated.job.csvtonestedxml.target
    filePath: output/customers-nested.xml
    rootElement: Customers
    recordElement: CustomerRecord
    fields:
      - name: id
        type: String
    modelDefinitionPath: definitions/nested-target-model.yaml
```

Treat that nested form as the preferred pattern when the XML output contains structure such as `profile.email` or `address.city`. The structural target contract belongs in the referenced `modelDefinitionPath`, while the top-level target YAML stays aligned with the same authoring shape used for simpler XML targets.

## Example

This mirrors `src/main/resources/config-jobs/customer-load/target-config.yaml`.

```yaml
targets:
  - format: xml
    targetName: Customers
    packageName: com.etl.generated.job.customerload.target
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

## Example walkthrough

Read the example in output-contract order:

- `targets:` is the required root for target config files.
- `format: xml` selects the XML writer path.
- `targetName` is the logical target identity referenced by processor mappings and job steps.
- `packageName` is the generated target package validated during startup; in explicit job mode it may be omitted to use the job-scoped default package.
- `filePath` is the XML output artifact path or output directory.
- `rootElement` is the document envelope element written around the output.
- `recordElement` is the repeated XML item element name expected by the writer path.
- `fields` lists the target object properties that the writer marshals.
- `fields[].name` is the generated target property name used during XML marshalling.
- `fields[].type` is the logical type recorded in the generated target model contract.

This preserved example shows the simpler flat XML path. Add `modelDefinitionPath` when the output contract is nested and the target structure should be generated from a structural definition instead of only the flat `fields` list.

The important normalization point is that both simple and nested XML targets now share the same top-level authoring shape. The main difference is whether the target structure is described directly through flat `fields` or externally through `modelDefinitionPath`.

## Runtime behavior today

- The target config file root is `targets:`.
- The same top-level XML target contract is used for both simple and nested XML outputs: `format`, `targetName`, `packageName`, `filePath`, `rootElement`, `recordElement`, `fields`, plus optional `modelDefinitionPath`.
- If `filePath` ends with `/` or points to an existing directory, the writer appends `<targetName>.xml` using a lowercase target name.
- When the generated target class simple name matches `recordElement`, the runtime streams individual XML record elements under the configured `rootElement`.
- Otherwise the writer falls back to wrapper/single-object XML output.
- When `modelDefinitionPath` is provided, job-scoped XML generation uses that structural definition to build nested target model classes for the selected scenario.
- For explicit job execution, startup now fails fast if the generated XML root or record classes are missing from the configured `packageName`.
- `fields[].name` values must align with the generated target object properties used during marshalling.

## Validation / usage notes

- `targetName` must match the selected `processor.mappings[].target` value.
- `packageName`, `rootElement`, and `recordElement` must line up with the generated XML classes that Maven compiled for the selected job.
- In explicit job mode, `packageName` may be omitted and defaults to `com.etl.generated.job.<normalized-job-name>.target`.
- Use `modelDefinitionPath` when the XML target must contain nested object structure such as `profile.email` or `address.city` rather than only flat record fields.
- Keep the top-level target YAML consistent between simple and nested XML targets so scenario authors only switch the structural source of truth, not the overall target config shape.
- For nested XML targets, keep the structural shape in the referenced model definition rather than trying to express the full nested contract only through the flat `fields` list.
- Keep `rootElement` and `recordElement` aligned with the XML structure expected by downstream consumers.
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


