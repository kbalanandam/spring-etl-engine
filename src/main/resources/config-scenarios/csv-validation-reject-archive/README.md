# csv-validation-reject-archive

Preserved first shipped CSV proof for file-ingestion hardening.

## Purpose

This scenario proves three shipped behaviors together:

- field-level validation rules in processor mappings
- rejected-record output with reason metadata
- source-file archiving after successful step completion

## Expected behavior

- accepted rows are written to `target/events-validation-output.csv`
- rejected rows are written under `target/rejects/`
- the original source file is moved under `target/archive/success/`

## Input notes

The preserved sample input lives at `src/main/resources/demo-input/EventsValidation.csv`.

Before running this scenario, stage a working copy to `target/events-validation-input.csv` so archive-on-success does not move the committed sample fixture.

It intentionally contains:

- one valid row
- one row with a missing required `id`
- one row with an invalid `eventTime`
- one more valid row


