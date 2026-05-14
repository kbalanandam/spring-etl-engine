# xml-to-csv-events

Business scenario for converting one realistic flat XML event feed into CSV output.

## Purpose

Use this bundle as the baseline XML-to-CSV example when you want:

- one flat XML source
- one flat CSV target
- one simple processor mapping with no transforms or validation rules

## Bundle map

- `job-config.yaml` - selects the runnable source, target, and processor files
- `source-config.yaml` - declares the XML source contract
- `target-config.yaml` - declares the CSV output artifact
- `processor-config.yaml` - maps XML source fields directly to CSV target fields

## `job-config.yaml`

```yaml
name: xml-to-csv-events
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
steps:
  - name: events-xml-to-csv-step
    source: Events
    target: EventsCsv
```

### What each field means

- `name` identifies the selected scenario in logs and metadata.
- `sourceConfigPath`, `targetConfigPath`, and `processorConfigPath` point to the sibling config files in this bundle.
- `steps` contains one explicit XML-to-CSV step.
- `source: Events` must match the XML `sourceName`.
- `target: EventsCsv` must match the CSV `targetName`.

## `source-config.yaml`

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

### What each field means

- `format: xml` selects the XML reader path.
- `sourceName: Events` is the logical source identity used by the job step and processor mapping.
- This bundle intentionally omits `packageName`; the runtime derives the XML source package from `job-config.yaml -> name`.
- `filePath` points to the committed XML sample input.
- `rootElement` is the XML document envelope.
- `recordElement` is the repeating XML fragment streamed as one runtime record.
- `fields` lists the flat event properties exposed to the processor.

## `target-config.yaml`

```yaml
targets:
  - format: csv
    targetName: EventsCsv
    filePath: output/events-output.csv
    delimiter: ","
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

### What each field means

- `format: csv` selects the CSV writer path.
- `targetName: EventsCsv` is the logical target identity used by the job step and processor mapping.
- This bundle intentionally omits `packageName`; the runtime derives the CSV target package from `job-config.yaml -> name`.
- `filePath` is the output CSV artifact path.
- `delimiter` controls the CSV separator and defaults to `,` when omitted.
- `fields` lists the output columns in write order.

## `processor-config.yaml`

```yaml
type: default
mappings:
  - source: Events
    target: EventsCsv
    fields:
      - from: eventCode
        to: eventCode
      - from: eventTime
        to: eventTime
      - from: description
        to: description
      - from: sourceSystem
        to: sourceSystem
```

### What each field means

- `type: default` selects the shipped processor implementation.
- The single mapping converts `Events` XML records into `EventsCsv` rows.
- Each `from -> to` pair is a direct copy with no validation, transforms, reject handling, or archive behavior enabled in this bundle.

## Notes

- The XML sample lives at `src/main/resources/demo-input/Events.xml`.
- The CSV artifact is written to `output/events-output.csv` inside this scenario bundle.
- This scenario remains a runnable baseline XML-to-CSV flow.
- The shared validation/reject/archive seams now exist for file-backed scenarios such as CSV and XML, but this preserved XML scenario intentionally does not enable those optional config fields and still maps/writes records as-is.

## Run example

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/xml-to-csv-events/job-config.yaml" spring-boot:run
```
