# Architecture Overview

## Purpose

This document captures the current architectural baseline of `spring-etl-engine` so new features can evolve from a shared understanding.

## Current architectural style

The engine is a **config-driven Spring Batch ETL runtime** with these core ideas:

- source, target, and processor behavior is loaded from YAML
- one ETL run may be selected through a single business-scenario `job-config.yaml`
- supported formats are represented as config subtypes
- readers, processors, and writers are selected through factories
- model classes are generated and resolved dynamically at runtime
- the batch layer chooses between chunk and tasklet execution based on source size

## High-level view

```mermaid
flowchart TD
    A[Spring Boot startup] --> B[ConfigLoader]
    B --> C{etl.config.job set?}
    C -- Yes --> D[selected job-config.yaml]
    D --> E[source/target/processor config trio]
    C -- No --> F[direct source/target/processor paths]

    E --> G[BatchConfig]
    F --> G

    G --> H[GeneratedModelClassResolver]
    G --> I[DynamicReaderFactory]
    G --> J[DynamicProcessorFactory]
    G --> K[DynamicWriterFactory]

    I --> L[Reader implementation]
    J --> M[Processor implementation]
    K --> N[Writer implementation]

    L --> O[Spring Batch Step]
    M --> O
    N --> O

    O --> P[ETL Job]
    P --> Q[Output files / targets]
```

## Main runtime components

### Application bootstrap
- `src/main/java/com/etl/ETLEngineApplication.java`
- `src/main/java/com/etl/runner/EtlJobRunner.java`

Spring Boot starts the app, builds the context, and launches the ETL job through `EtlJobRunner`.

### Config loading
- `src/main/java/com/etl/config/ConfigLoader.java`

`ConfigLoader` selects one effective config set for the run. The preferred product-facing mode is `etl.config.job`, where one selected `job-config.yaml` points to the exact source, target, and processor YAML to load. If `etl.config.job` is not set, the loader uses the direct config-path properties and may fall back to bundled classpath resources.

### Batch orchestration
- `src/main/java/com/etl/config/BatchConfig.java`

`BatchConfig` constructs the job from the selected config set and dynamically builds steps by pairing sources and targets. It also chooses chunk or tasklet execution depending on the record count threshold.

### Dynamic extension points
- `src/main/java/com/etl/reader/DynamicReaderFactory.java`
- `src/main/java/com/etl/processor/DynamicProcessorFactory.java`
- `src/main/java/com/etl/writer/DynamicWriterFactory.java`

These factories isolate format-specific behavior and make the runtime extensible.

### Dynamic model contract
- `src/main/java/com/etl/common/util/GeneratedModelClassResolver.java`

This is the central contract between configuration, generated model classes, processors, and writers.

## Current strengths

- clear separation between config loading and runtime execution
- explicit business-scenario selection without scenario auto-discovery
- pluggable reader/processor/writer model
- dynamic support for multiple source and target formats
- adaptive execution model for smaller vs larger workloads
- good base for future relational, API, or procedure-based extensions

## Current architectural constraints

- `BatchConfig` currently pairs sources and targets by index
- `etl.config.job` currently resolves a selected scenario by file path, not by a short scenario-name registry
- orchestration is step-based but still centered on `source -> processor -> target`
- stored procedures and richer multi-job flows will require a higher-level step operation model
- generated models currently remain an important runtime dependency and contract surface

## Near-term evolution points

The next architecture topics that should extend this baseline rather than bypass it are:

- relational-source and relational-target hardening for larger data volumes
- stored procedure execution as a first-class step type
- multi-step and multi-job orchestration
- vendor/dialect abstraction for relational platforms

