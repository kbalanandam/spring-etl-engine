# File Ingestion Hardening

## Purpose

This note preserves the first implemented file-based ingestion hardening slice in `spring-etl-engine` and the remaining follow-on direction that is still deferred.

Use it to answer four questions when extending this area further:

1. where archive behavior should be configured
2. where rejected-record behavior should be configured
3. where field-level validation rules should be configured
4. what the first supported file-ingestion slice should and should not do

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
- in-memory duplicate tracking for simpler and faster moderate-volume runs
- embedded-DB staging for ordered duplicate winner selection when larger-volume runs would otherwise put too much pressure on heap memory

The product direction should still preserve a future client-selectable tracking strategy so operators can explicitly choose the storage mode when needed.

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

- expressions
- conditional rules
- regex
- ranges
- lookup/enrichment-driven validation
- client-selectable duplicate tracking storage (`memory` vs future disk-backed mode)

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
2. processor applies field mapping
3. processor evaluates configured field rules
4. if ordered duplicate winner selection is configured, the runtime first determines the winning record per duplicate key before final processing/writing
5. if rules pass, record is written to the selected target
6. if rules fail or an older duplicate is discarded, the record is written to reject output with reason metadata when rejected-record output is enabled
7. when the step completes successfully, the original file is archived if archive is enabled

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


