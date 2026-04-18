# Runtime Flow

## Purpose

This page explains how one ETL run currently executes from startup to output.

## End-to-end flow

```mermaid
flowchart TD
    A[Application starts] --> B[EtlJobRunner builds job parameters]
    B --> C[Spring launches etlJob]
    C --> D[BatchConfig builds steps]
    D --> E[Load source and target pair]
    E --> F[Resolve runtime model metadata]
    F --> G[Count source records]
    G --> H{recordCount > chunkThreshold?}

    H -- Yes --> I[Chunk step]
    H -- No --> J[Tasklet step]

    I --> K[Reader reads record]
    K --> L[Processor maps to target type]
    L --> M[Writer writes chunk]

    J --> N[Tasklet loops through reader]
    N --> O[Processor maps records]
    O --> P{wrapper required?}
    P -- Yes --> Q[Create wrapper object]
    P -- No --> R[Write buffered records]
    Q --> S[Writer writes single wrapped object]
    R --> S

    M --> T[Next configured step]
    S --> T
    T --> U[Job completes]
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

    App->>Loader: create config beans
    App->>Batch: build etlJob
    Runner->>Batch: run job
    Batch->>Resolver: resolveMetadata(source, target)
    Batch->>ReaderF: createReader(source, sourceClass)
    Batch->>ProcF: getProcessor(processorConfig, source, target, metadata)
    Batch->>WriterF: createWriter(target, writeClass)
    Batch->>Batch: choose chunk or tasklet step
    Batch->>Batch: execute Spring Batch step
```

## Important runtime decisions

### 1. Config resolution
`ConfigLoader` chooses external YAML when present, otherwise bundled classpath YAML.

### 2. Model resolution
`GeneratedModelClassResolver` translates config into concrete runtime class names and wrapper metadata.

### 3. Step strategy
`BatchConfig` calls `getRecordCount()` on the source and compares it to `etl.chunk.threshold`.

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
- multi-job orchestration will likely require a higher-level flow model than the current source-target index pairing

