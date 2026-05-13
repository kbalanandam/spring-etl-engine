# csv-validation-reject-archive

Preserved first shipped CSV proof for file-ingestion hardening.

## Purpose

This scenario proves three shipped behaviors together:

- field-level validation rules in `processor-config.yaml`
- rejected-record output with reason metadata
- archive-on-success for the original staged CSV file

## Bundle map

- `job-config.yaml` - single-step scenario selection
- `source-config.yaml` - CSV source plus archive-on-success settings
- `target-config.yaml` - accepted-row CSV output
- `processor-config.yaml` - validation rules plus rejected-record output
- `input/` - working input location for the staged CSV copy
- `output/` - accepted output, reject output, and archive output locations

## `job-config.yaml`

```yaml
name: csv-validation-reject-archive
sourceConfigPath: source-config.yaml
targetConfigPath: target-config.yaml
processorConfigPath: processor-config.yaml
steps:
  - name: events-validation-step
    source: Events
    target: EventsCsv
```

### What each field means

- `name` identifies the scenario in logs and evidence.
- `sourceConfigPath`, `targetConfigPath`, and `processorConfigPath` select the sibling config files for this run.
- `steps` contains one explicit step.
- `source: Events` must match the CSV source name.
- `target: EventsCsv` must match the CSV target name.

## `source-config.yaml`

```yaml
sources:
  - format: csv
    sourceName: Events
    filePath: input/events-validation-input.csv
    delimiter: ","
    archive:
      enabled: true
      successPath: output/archive/success/
      namePattern: "{originalName}-{timestamp}"
    fields:
      - name: id
        type: String
      - name: eventTime
        type: String
      - name: description
        type: String
```

### What each field means

- `format: csv` selects the CSV reader path.
- `sourceName: Events` is the logical identity matched by the job step and processor mapping.
- This bundle intentionally omits `packageName`; the runtime derives the CSV source package from `job-config.yaml -> name`.
- `filePath` is the staged working file, not the committed sample fixture.
- `delimiter` declares the CSV separator.
- `archive.enabled: true` turns on archive-on-success after the step finishes successfully.
- `archive.successPath` is where the original source file is moved.
- `archive.namePattern` controls the archived file name.
- `fields` lists the CSV columns expected by the reader.

## `target-config.yaml`

```yaml
targets:
  - format: csv
    targetName: EventsCsv
    filePath: output/events-validation-output.csv
    delimiter: ","
    fields:
      - name: id
        type: String
      - name: eventTime
        type: String
      - name: description
        type: String
```

### What each field means

- `format: csv` selects the CSV writer path for accepted rows.
- `targetName: EventsCsv` is the logical target identity matched by the job step and processor mapping.
- This bundle intentionally omits `packageName`; the runtime derives the CSV target package from `job-config.yaml -> name`.
- `filePath` is the accepted-record output artifact.
- `fields` lists the written columns in output order.

## `processor-config.yaml`

```yaml
type: default
rejectHandling:
  enabled: true
  outputPath: output/rejects/
  includeReasonColumns: true
mappings:
  - source: Events
    target: EventsCsv
    fields:
      - from: id
        to: id
        rules:
          - type: notNull
      - from: eventTime
        to: eventTime
        rules:
          - type: notNull
          - type: timeFormat
            pattern: HH:mm:ss
      - from: description
        to: description
```

### What each field means

- `type: default` selects the shipped processor implementation.
- `rejectHandling.enabled: true` turns on rejected-record output.
- `rejectHandling.outputPath` is where rejected rows are written.
- `rejectHandling.includeReasonColumns: true` appends rejection metadata columns.
- The mapping converts `Events` source rows into `EventsCsv` output rows.
- `id` must be present because of the `notNull` rule.
- `eventTime` must be present and match `HH:mm:ss`.
- `description` is copied through without validation in this preserved example.

## Expected behavior

- accepted rows are written to `output/events-validation-output.csv`
- rejected rows are written under `output/rejects/`
- the original staged source file is moved under `output/archive/success/`

## Input notes

The preserved sample input lives at `src/main/resources/demo-input/EventsValidation.csv`.

Before running this scenario, stage a working copy to `input/events-validation-input.csv` so archive-on-success does not move the committed sample fixture.

It intentionally contains:

- one valid row
- one row with a missing required `id`
- one row with an invalid `eventTime`
- one more valid row
