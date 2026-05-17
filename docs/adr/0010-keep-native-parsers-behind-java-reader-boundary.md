# ADR-0010: Keep native parsers behind the Java reader boundary

- Status: Accepted
- Date: 2026-05-16

## Context

`spring-etl-engine` already uses Java/Spring Batch reader contracts as the active runtime seam for source ingestion. Future parser discussions may introduce pressure to adopt native parser engines, including C or C++, for throughput or specialized source-native parsing needs.

Without an explicit decision, that pressure could blur the current architecture by letting native parser work absorb responsibilities that belong elsewhere in the runtime, such as processor transforms, processor-rule validation, orchestration, generated-model ownership, or target publication behavior.

The product direction also needs to stay compatible with the existing reader factory, Spring Batch lifecycle participation, and operator-facing runtime categorization path.

## Decision

Future native parser implementations, including C/C++, are allowed only **behind the existing Java reader boundary**.

The stable runtime contract remains:

- `DynamicReaderFactory` selects the reader implementation
- `DynamicReader` builds a Java `ItemReader` or `ItemStreamReader`
- the Java reader adapter participates in Spring Batch lifecycle and `ExecutionContext`
- native code may perform source-native parsing only
- generated runtime source record emission remains the handoff point into the ETL core

When a native parser is introduced, prefer an **out-of-process sidecar or worker integration** over deep in-process JNI/JNA coupling.

In-process native integration is not forbidden, but it is a secondary option only when a strong parser-native need is proven and the same Java-owned reader/runtime contract remains intact.

## Consequences

### Positive
- preserves the current Java/Spring Batch runtime boundary
- keeps parser technology replaceable without redesigning the ETL core
- protects processor, orchestration, writer, and generated-model concerns from drifting into parser code
- supports future native-parser experimentation while keeping OneFlow operationally coherent
- favors process isolation and lower coupling for future native parser engines

### Negative
- native parser adoption will require an adapter layer instead of direct runtime ownership
- sidecar or worker integration adds protocol, packaging, and operational complexity
- some peak throughput opportunities may be deferred compared with a deeply embedded native runtime

## Alternatives considered
- make native parser technology a first-class runtime center and let it own more of ingestion flow
- prefer in-process JNI/JNA integration as the default first step for any native parser work
- reject native parser adoption entirely and require all parser evolution to stay Java-only

## Notes

This ADR does not introduce a shipped native parser.

It freezes the architectural rule that any future native parser must remain an implementation detail behind the current Java reader seam and must stop at source-native parsing plus runtime-record emission.

See also:

- [`../architecture/native-parser-adoptability.md`](../architecture/native-parser-adoptability.md)
- [`../architecture/oneflow-file-parser-capabilities.md`](../architecture/oneflow-file-parser-capabilities.md)

