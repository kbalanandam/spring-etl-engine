# File Ingestion Hardening

## Purpose

This note preserves the first implemented file-based ingestion hardening slice in `spring-etl-engine` and the remaining follow-on direction that is still deferred.

Use it to answer four questions when extending this area further:

1. where archive behavior should be configured
2. where rejected-record behavior should be configured
3. where field-level validation rules should be configured
4. where future cleaner / normalization behavior should be configured and what the first supported file-ingestion slice should and should not do

The first CSV-focused slice described here is now part of the shipped config contract. Broader expansion beyond that slice remains forward-looking architecture guidance.

For the longer-term extension model that separates future source-validation and processor-rule SPIs, see [`validation-extension-architecture.md`](validation-extension-architecture.md).

## Why this note exists

The current runtime already has:

- explicit `job-config.yaml` driven scenario selection
- explicit `steps`-driven orchestration
- current file readers and writers
- machine-readable step/run evidence
- a stronger documentation and backlog discipline

The next practical product slice is not more connector breadth first.

It is safer file ingestion behavior for real scenarios:

- configurable field validation
- controlled rejected-record output
- archive handling for processed source files
- operator-visible evidence about what was accepted, rejected, and archived

## Current implemented state

Today, the shipped config contract supports:

- `job-config.yaml` for selected scenario execution and explicit step order
- file-based source selection such as CSV and XML through source config files
- default processor field mapping through `processor-config.yaml`
- target writing through the selected target config

Today, the shipped config contract now supports a first CSV-focused slice for:

- per-field validation rules in processor mappings (`notNull`, `timeFormat`)
- duplicate handling for keep-first/reject-later semantics plus ordered winner selection across single-field and composite-key matching
- explicit rejected-record output configuration in processor config
- processed-source-file archive configuration in CSV source config
- accepted vs rejected record artifact semantics for the preserved CSV proof scenario

The remaining gaps are now the broader follow-on work beyond that first slice.

For duplicate handling specifically, the shipped runtime currently uses:

- optional duplicate checking only when a `duplicate` processor rule is configured for the mapping; when no such rule is present, runtime does not apply duplicate-based filtering for that mapping
- keep-first duplicate handling when `duplicate` is configured with the mapped field alone or with `keyFields` but without `orderBy`
- ordered winner selection when `duplicate` is configured with `orderBy`, so the best record per duplicate key is retained before final write
- a shared processor-level duplicate contract intended for CSV, flat XML, relational, and other future record-oriented sources once records are available as normal runtime objects
- in-memory duplicate tracking for simpler and faster moderate-volume runs
- embedded-DB staging for ordered duplicate winner selection when larger-volume runs would otherwise put too much pressure on heap memory

The product direction should still preserve a future client-selectable tracking strategy so operators can explicitly choose the storage mode when needed.

The main deferred exception to preserve is source-native duplicate identity that cannot be expressed cleanly through flat mapped fields. If a future XML scenario needs duplicate keys based on XPath, namespaces, nested collections, or other pre-flattening structure details, that should be treated as separate XML/source-level duplicate scope rather than stretching the current processor rule beyond its intended contract.

For ordered duplicate winner selection, the current shipped slice resolves the final winner per duplicate key before the write phase and therefore forces tasklet-style final buffering for that mapping.

## Design goals for the next slice

The first implementation slice should:

- stay file-ingestion focused
- stay operator-visible and testable
- preserve explicit config-driven behavior
- avoid introducing a broad rule engine too early
- avoid changing every config type at once

The first implementation slice should prove one preserved realistic file scenario that shows:

- accepted records written to the target
- rejected records written to reject output
- original input file archived after successful processing

## Proposed config placement

### 1. Archive behavior belongs in the file source config

Archive behavior is a file-lifecycle concern.

It should live with the file source definition, not with field mapping rules.

For the first slice, that means archive behavior should be added only to file-based source configs such as:

- `CsvSourceConfig`
- later `XmlSourceConfig`

### 2. Validation rules belong in the processor config

Validation rules are part of transformation acceptance behavior.

They should live next to the source-to-target mapping they affect.

For the first slice, that means validation rules should be attached to mapping fields inside `processor-config.yaml`.

### 2a. Future generic cleaner / normalization transforms also belong in the processor config first

Most upcoming cleanup behavior such as status decoding, null fallback, country-code normalization, trim, or case normalization should live beside the mapping in `processor-config.yaml`, not in source config.

That keeps shared field/value rewriting on the active default-processor path once a normal runtime record exists.

Future source-transform YAML should be added only for true source-native adaptation cases such as XPath-, namespace-, header-, token-, or other pre-flattening/source-shape concerns that cannot be expressed cleanly through processor-side field transforms.

### 3. Rejected-record output belongs in the processor config

Rejected-record behavior is part of validation-aware processing.

It should be configured alongside the mapping and validation rules that determine whether a record is accepted or rejected.

## Proposed YAML shape

## Source config proposal

The first proposed archive shape is intentionally small and file-source specific.

```yaml
sources:
  - format: csv
    sourceName: Events
    packageName: com.etl.model.source
    filePath: input/events.csv
    delimiter: ","
    archive:
      enabled: true
      successPath: archive/success/
      namePattern: "{originalName}-{timestamp}"
    fields:
      - name: id
        type: int
      - name: eventTime
        type: String
      - name: description
        type: String
```

### Proposed archive semantics

For the first slice:

- archive only applies to file-based sources
- archive happens only after successful step completion
- if the step fails technically, the original file remains in place
- if records are rejected but the step completes successfully, the original file is still archived

## Processor config proposal

```yaml
processor:
  type: default
  rejectHandling:
    enabled: true
    outputPath: target/rejects/
    includeReasonColumns: true
  mappings:
    - source: Events
      target: EventsCsv
      fields:
        - from: id
          to: id
          rules:
            - type: notNull
            - type: duplicate
              keyFields:
                - id
              orderBy:
                - field: eventTime
                  direction: DESC
                - field: sequenceNo
                  direction: ASC
        - from: eventTime
          to: event_time
          rules:
            - type: notNull
            - type: timeFormat
              pattern: HH:mm:ss
        - from: countryCode
          to: countryCode
          transforms:
            - type: valueMap
              mappings:
                IND: IN
                USA: US
              defaultValue: UNKNOWN
        - from: sequenceNo
          to: sequenceNo
        - from: description
          to: description
```

### Proposed rule semantics

For the current shipped slice, support stays narrow:

- `notNull`
- `timeFormat`
- `duplicate` for single-field or composite-key matching with either keep-first/reject-later behavior or ordered winner selection

Future slices may add:

- ordered optional `transforms[]` chains beside `rules`
- a first processor-side `valueMap` cleaner for coded fields
- expressions
- conditional rules
- regex
- ranges
- lookup/enrichment-driven validation
- client-selectable duplicate tracking storage (`memory` vs future disk-backed mode)

Planned transform semantics should stay explicit:

- omit `transforms` entirely when no cleanup behavior is needed
- allow zero, one, or many ordered transform steps per field
- evaluate processor rules after transforms, not before them
- allow transform-then-reject behavior when a normalized value such as `UNKNOWN` must still be rejected by a business/target rule
- fail fast or at least warn once source transforms exist and equivalent generic value rewriting is configured for the same field in both source and processor layers

## Proposed reject output shape

The first slice should prefer one simple reject output over a broad quarantine model.

### Proposed output columns

Rejected records should preserve the original mapped fields and append reason metadata such as:

- `_rejectField`
- `_rejectRule`
- `_rejectMessage`

Example:

```csv
id,event_time,description,_rejectField,_rejectRule,_rejectMessage
,12:45:00,missing id,id,notNull,"id must not be null"
10,25:99:00,bad time,eventTime,timeFormat,"eventTime must match HH:mm:ss"
```

## Proposed runtime behavior

For the first slice, one record should move through this decision path:

1. reader reads record from file source
2. any future source-native adaptation runs only when the selected source type requires it
3. processor applies field mapping and any configured field transforms
4. processor evaluates configured field rules on the transformed value
5. if ordered duplicate winner selection is configured, the runtime first determines the winning record per duplicate key before final processing/writing
6. if rules pass, record is written to the selected target
7. if rules fail or an older duplicate is discarded, the record is written to reject output with reason metadata when rejected-record output is enabled
8. when the step completes successfully, the original file is archived if archive is enabled

## Proposed operator evidence

The first slice should produce evidence that operators can use without reading full stack traces.

At minimum, one successful run should be able to show:

- records read
- records accepted
- records rejected
- records written
- reject output path
- archive result and archive path

## First supported scope

The first slice should support:

- CSV file source
- default processor mappings
- file-based reject output
- file-source archive behavior
- one preserved realistic file scenario with mixed valid and invalid rows

## Explicitly deferred from the first slice

The first slice should not yet try to solve:

- relational-source archiving
- complex XML nested validation rules
- a broad source-transform YAML model for generic cleanup behavior
- expression-based mapping itself
- conditional transformation rules
- quarantine workflow orchestration
- replay/retry semantics for archived or rejected files
- multi-destination reject routing
- rule severity levels
- explicit operator selection of duplicate storage strategy per mapping or scenario

## Relationship to other docs

- `docs/product/product-backlog.md` records that this is the next planned slice
- `docs/architecture/transformation-capability-roadmap.md` places this work before broader expression-based mapping
- `docs/architecture/runtime-flow.md` shows where this future behavior should plug into the runtime
- `docs/architecture/file-ingestion-hardening-checklist.md` turns this design into an execution-ready checklist of likely code touch points, tests, and the first preserved proof scenario
- `docs/config/*` continues to describe only the currently implemented config contract

## Suggested first proof scenario

A preserved realistic CSV scenario should include at least:

- one valid row
- one row with a missing required field
- one row with an invalid time field
- one additional valid row

That scenario should prove:

- accepted output
- rejected output
- archived-original-file behavior
- visible counts in runtime evidence


