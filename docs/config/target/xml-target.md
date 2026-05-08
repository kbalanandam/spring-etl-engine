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
| `modelDefinitionPath` | no | string | Optional structural XML target model definition used when the target shape must be generated as nested XML rather than only from the flat `fields` list |
| `fields` | yes | list | Ordered list of target properties expected on the generated target model |
| `fields[].name` | yes | string | Property name written into the generated XML record/wrapper |
| `fields[].type` | yes | string | Logical type used in the generated target model contract |

## Example

This mirrors `src/main/resources/config-jobs/customer-load/target-config.yaml`.

```yaml
targets:
  - format: xml
    packageName: com.etl.generated.job.customerload.target
    filePath: output/customers.xml
    targetName: Customers
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

## Runtime behavior today

- The target config file root is `targets:`.
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

## Related docs

- [`CSV target reference`](csv-target.md)
- [`Relational target reference`](relational-target.md)
- [`CSV source reference`](../source/csv-source.md)
- [`Default processor reference`](../processor/default-processor.md)


