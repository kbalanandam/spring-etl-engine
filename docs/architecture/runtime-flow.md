# Runtime Flow

## Purpose

This page explains how one ETL run currently executes from startup to output.

## End-to-end flow

```mermaid
flowchart TD
    A[Application starts] --> B[ConfigLoader resolves selected config set]
    B --> C[EtlJobRunner builds job parameters]
    C --> D[Spring launches etlJob]
    D --> E[BatchConfig iterates configured steps]
    E --> F[Resolve named source and target for current step]
    F --> G[Resolve runtime model metadata]
    G --> H[Count source records]
    H --> I{recordCount > chunkThreshold?}

    I -- Yes --> J[Chunk step]
    I -- No --> K[Tasklet step]

    J --> L[Reader reads record]
    L --> M[Processor maps to target type]
    M --> N[Writer writes chunk]

    K --> O[Tasklet loops through reader]
    O --> P[Processor maps records]
    P --> Q{wrapper required?}
    Q -- Yes --> R[Create wrapper object]
    Q -- No --> S[Write buffered records]
    R --> T[Writer writes single wrapped object]
    S --> T

    N --> U[Next configured step]
    T --> U
    U --> V[Job completes]
```

## Sequence view

```mermaid
sequenceDiagram
    participant App as ETLEngineApplication
    participant Runner as EtlJobRunner
    participant Loader as ConfigLoader
    participant Batch as BatchConfig
    participant Resolver as GeneratedModelClassResolver
    participant ReaderF as DynamicReaderFactory
    participant ProcF as DynamicProcessorFactory
    participant WriterF as DynamicWriterFactory

    App->>Loader: resolve selected business scenario
    Loader->>Loader: read etl.config.job
    Loader->>Loader: fail fast if missing unless demo fallback is enabled
    Loader->>Loader: resolve source/target/processor paths and explicit steps
    Loader->>Loader: validate selected relational configs early and reject placeholder values
    App->>Batch: build etlJob
    Runner->>Batch: run job
    Batch->>Batch: resolve next configured step by source/target name
    Batch->>Resolver: resolveMetadata(source, target)
    Batch->>ReaderF: createReader(source, sourceClass)
    Batch->>ProcF: getProcessor(processorConfig, source, target, metadata)
    Batch->>WriterF: createWriter(target, writeClass)
    Batch->>Batch: choose chunk or tasklet step
    Batch->>Batch: execute Spring Batch step
```

## Important runtime decisions

### 1. Config resolution
`ConfigLoader` resolves configuration in this order:

1. if `etl.config.job` is set, treat it as the selected business-scenario/job config and use its explicit `sourceConfigPath`, `targetConfigPath`, `processorConfigPath`, and `steps`
2. if `etl.config.job` is missing and `etl.config.allow-demo-fallback=false`, fail startup immediately
3. if `etl.config.allow-demo-fallback=true`, use `etl.config.source`, `etl.config.target`, and `etl.config.processor` for local/demo runs
4. in demo fallback mode only, continue into bundled classpath YAML when the configured direct files are missing

`ConfigLoader` does not auto-discover scenario folders. Exactly one config set is selected for a run.

When the selected source or target uses relational configuration, `ConfigLoader` now validates those chosen configs before job execution starts. Placeholder values such as `<SQLSERVER_HOST>` are rejected early with scenario-aware error messages instead of surfacing later as JDBC runtime failures.

### 2. Model resolution
`GeneratedModelClassResolver` translates config into concrete runtime class names and wrapper metadata.

### 3. Step strategy
`BatchConfig` walks the explicit step list from `job-config.yaml`. For each step, it resolves the named source and target, verifies that a matching processor mapping exists, emits machine-readable step-planning logs such as `STEP_PLAN` and `STEP_READY`, and then calls `getRecordCount()` on the selected source to compare it to `etl.chunk.threshold`.

- large source => chunk step
- small source => tasklet step

### 4. XML wrapper handling
For XML targets, processing and writing may use different model classes:

- processing class = record element type
- write class = wrapper/root element type

That contract is centralized in `GeneratedModelClassResolver`.

## Why this matters for future features

This flow shows where future enhancements should plug in:

- relational readers/writers should enter through the same factories
- stored procedures may fit as reader, writer, or tasklet-style step operations
- multi-job orchestration will likely require a higher-level flow model than the current selected job-config plus explicit step list

