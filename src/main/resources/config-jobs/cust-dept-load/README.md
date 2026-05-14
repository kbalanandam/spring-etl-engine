# cust-dept-load

Business scenario for loading both customer and department data in one explicit run.

## Purpose

Use this bundle when one selected `job-config.yaml` should execute multiple ordered entity steps:

- CSV `Customers` -> XML `Customers`
- CSV `Department` -> XML `Departments`

This preserved scenario is the clearest runnable example of the current multi-step job model.

## Bundle map

- `job-config.yaml` - selects the source, target, and processor bundles and defines the explicit step order
- `source-config.yaml` - declares the two CSV sources
- `target-config.yaml` - declares the two XML outputs
- `processor-config.yaml` - maps customer fields and department fields independently
- `output/` - scenario-local runtime output folder for `customers.xml` and `departments.xml`

## `job-config.yaml`

```yaml
name: cust-dept-load
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
steps:
  - name: customers-step
    source: Customers
    target: Customers
  - name: departments-step
    source: Department
    target: Departments
```

### What each field means

- `name` is the scenario identity used in logs and default generated package derivation.
- `sourceConfigPath`, `targetConfigPath`, and `processorConfigPath` point at the sibling YAML files that make up this runnable bundle.
- `steps` is the ordered execution plan.
- `customers-step` runs first because it appears first in the YAML.
- `departments-step` runs second.
- `source` values must match `sourceName` entries from `source-config.yaml`.
- `target` values must match `targetName` entries from `target-config.yaml`.

## `source-config.yaml`

```yaml
sources:
  - format: csv
    sourceName: Customers
    filePath: src/main/resources/demo-input/Customers.csv
    delimiter: ","
    fields:
      - name: id
        type: int
      - name: name
        type: String
      - name: email
        type: String

  - format: csv
    sourceName: Department
    filePath: src/main/resources/demo-input/Department.csv
    delimiter: ","
    fields:
      - name: id
        type: int
      - name: name
        type: String
```

### What each field means

- `sources:` is the root list for source definitions.
- `format: csv` selects the CSV reader for both entities.
- `sourceName` gives each CSV source a logical identity used by the job step and processor mapping.
- This bundle intentionally omits `packageName`; the runtime derives the shared source-side package from `job-config.yaml -> name`.
- `filePath` points to the committed demo CSV for each entity.
- `delimiter` documents the CSV separator.
- `fields` declares the column order expected by the reader.
- Customer rows expose `id`, `name`, and `email`.
- Department rows expose `id` and `name`.

## `target-config.yaml`

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

  - format: xml
    targetName: Departments
    filePath: output/departments.xml
    rootElement: Departments
    recordElement: Department
    fields:
      - name: id
        type: int
      - name: name
        type: String
```

### What each field means

- `targets:` is the root list for target definitions.
- `format: xml` selects the XML writer path.
- `targetName` is the logical target identity used by the job step and processor mapping.
- This bundle intentionally omits `packageName`; the runtime derives the shared target-side package from `job-config.yaml -> name`.
- `filePath` fixes the output artifact name for each entity.
- `rootElement` is the XML document container.
- `recordElement` is the repeated XML item element.
- `fields` lists the target properties written into each XML record.

## `processor-config.yaml`

```yaml
type: default
mappings:
  - source: Customers
    target: Customers
    fields:
      - from: id
        to: id
      - from: name
        to: name
      - from: email
        to: email

  - source: Department
    target: Departments
    fields:
      - from: id
        to: id
      - from: name
        to: name
```

### What each field means

- `type: default` selects the shipped processor implementation.
- `mappings` contains one mapping contract per source/target pair.
- The first mapping converts `Customers` rows into `Customers` XML records.
- The second mapping converts `Department` rows into `Departments` XML records.
- Each `from -> to` pair is a direct field copy with no validation or transforms in this baseline bundle.

## Runtime notes

- This scenario demonstrates the current runtime model of one selected job bundle with multiple ordered steps.
- Step order is explicit in `job-config.yaml`; it is not inferred from the order of `sources` or `targets`.
- The output files are written under this scenario folder's `output/` directory when you run the bundle.
