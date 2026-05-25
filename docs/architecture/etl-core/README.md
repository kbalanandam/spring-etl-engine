# ETL core architecture notes

Use this folder for architecture notes that describe the shipped ETL worker runtime, its extension seams, and ETL-specific future evolution.

## Purpose

This folder is the main landing zone for architecture readers who want to understand how one selected job bundle becomes a concrete ETL run.

Use it when you want to understand:

- the shipped runtime flow and execution evidence
- ETL-specific extension seams for readers, processors, writers, and validation
- file-ingestion, duplicate-handling, and publication behavior on the active runtime path
- generated-model and naming contracts that keep ETL execution coherent

## Current anchor notes

- [`runtime-flow.md`](runtime-flow.md) - shipped end-to-end ETL runtime flow
- [`csv-to-xml-runtime-flow.md`](csv-to-xml-runtime-flow.md) - operational deep dive for the shipped `CSV -> XML` path
- [`extension-points.md`](extension-points.md) - ETL extension seams and guardrails
- [`oneflow-runtime-fallback-reference.md`](oneflow-runtime-fallback-reference.md) - shipped fallback/default decision matrix
- [`generated-model-naming-standard.md`](generated-model-naming-standard.md) - generated-model naming and package contract
- [`file-ingestion-hardening.md`](file-ingestion-hardening.md) - hardening direction on the active ETL path
- [`validation-extension-architecture.md`](validation-extension-architecture.md) - validation extension boundaries

## Related notes

- [`scenario-driven-runtime-direction.md`](scenario-driven-runtime-direction.md) - future runtime direction that still stays rooted in one selected scenario per run
- [`hierarchical-flow-composition.md`](hierarchical-flow-composition.md) - current descriptor projection and future composition direction
- [`runtime-to-scenario-gap-assessment.md`](runtime-to-scenario-gap-assessment.md) - shipped-to-target gap assessment

## Migration note

This folder now holds the ETL-core notes directly.

