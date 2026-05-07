# csv-validation-reject-archive

Preserved first shipped CSV proof for file-ingestion hardening.

## Purpose

This scenario proves three shipped behaviors together:

- field-level validation rules in processor mappings
- rejected-record output with reason metadata
- source-file archiving after successful step completion

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


