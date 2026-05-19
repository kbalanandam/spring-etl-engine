# Architecture index

Use this page as the folder-level index for `docs/architecture/`.

It organizes the design notes by concern so the architecture set stays navigable as OneFlow grows.

## Purpose

This index is the best starting point when you know you need an architecture note, but do not yet know the exact filename.

Use it to:

- browse architecture notes by topic instead of scanning one long flat list
- find the right note before changing runtime, config, extension points, or future-direction docs
- discover newer parser, control-plane, and observability notes without relying on memory

## Status labels used here

- **Shipped** — part of the active runtime path today
- **Current baseline + future evolution** — starts from the shipped baseline and preserves the next direction
- **Future direction** — design guidance, not a shipped runtime path today
- **Template** — authoring helper, not a product/runtime contract

## Start here

If you are not sure where to begin, use one of these entry points first:

- [`overview.md`](overview.md) — high-level system architecture (**Current baseline + future evolution**)
- [`runtime-flow.md`](runtime-flow.md) — shipped end-to-end ETL runtime flow (**Shipped**)
- [`scenario-driven-runtime-direction.md`](scenario-driven-runtime-direction.md) — next runtime-direction target (**Future direction**)
- [`extension-points.md`](extension-points.md) — where new formats, processors, and capabilities should plug in (**Current baseline + future evolution**)
- [`oneflow-file-parser-capabilities.md`](oneflow-file-parser-capabilities.md) — parser boundary for file sources (**Current baseline + future evolution**)
- [`etl-product-evolution-roadmap.md`](etl-product-evolution-roadmap.md) — what belongs now vs later in the product/runtime evolution (**Current baseline + future evolution**)

## Recently added / high-signal notes

Use this short list when you want the newest or most actively discussed architecture topics first:

- [`oneflow-file-parser-capabilities.md`](oneflow-file-parser-capabilities.md) — parser capability boundary for file sources (**Current baseline + future evolution**)
- [`native-parser-adoptability.md`](native-parser-adoptability.md) — future boundary for C/C++ or other native parser engines (**Future direction**)
- [`csv-native-parser-sidecar-protocol.md`](csv-native-parser-sidecar-protocol.md) — concrete CSV-first sidecar protocol sketch for future native parsing (**Future direction**)
- [`java-native-parser-reader-adapter-contract.md`](java-native-parser-reader-adapter-contract.md) — Java-side adapter contract for future sidecar-backed native parsing (**Future direction**)
- [`generated-model-naming-standard.md`](generated-model-naming-standard.md) — selected-job naming/package standard and bridge-cleanup direction (**Current baseline + future evolution**)
- [`job-history-and-operational-observability.md`](job-history-and-operational-observability.md) — current observability baseline plus retained-history direction (**Current baseline + future evolution**)
- [`security-test-strategy.md`](security-test-strategy.md) — phased security test strategy aligned to selected-job runtime and verification evidence (**Current baseline + future evolution**)

## Runtime foundations and flow

- [`overview.md`](overview.md) — current high-level system architecture (**Current baseline + future evolution**)
- [`runtime-flow.md`](runtime-flow.md) — end-to-end ETL runtime flow (**Shipped**)
- [`csv-to-xml-runtime-flow.md`](csv-to-xml-runtime-flow.md) — operational deep dive for the shipped CSV-to-XML path (**Current baseline + future evolution**)
- [`runtime-flow-walkthrough.html`](runtime-flow-walkthrough.html) — animated walkthrough of the shipped runtime path (**Current baseline + future evolution**)
- [`hierarchical-flow-composition.md`](hierarchical-flow-composition.md) — reusable `MainFlow -> SubFlow -> Step` direction (**Future direction**)
- [`flow-normalization-rules.md`](flow-normalization-rules.md) — normalization rules for simple and complex flows (**Future direction**)
- [`scenario-driven-runtime-direction.md`](scenario-driven-runtime-direction.md) — target next-direction runtime contract (**Future direction**)
- [`runtime-to-scenario-gap-assessment.md`](runtime-to-scenario-gap-assessment.md) — current-to-target gap assessment (**Future direction**)
- [`1-4-to-next-architecture-classification.md`](1-4-to-next-architecture-classification.md) — transition map for classifying current code during the next architecture shift (**Future direction**)
- [`job-level-activation-and-startup-guardrails.md`](job-level-activation-and-startup-guardrails.md) — job-level `isActive` guardrail and startup fail-fast rules (**Shipped**)
- [`generated-model-naming-standard.md`](generated-model-naming-standard.md) — selected-job naming/package standard and remaining bridge cleanup direction (**Current baseline + future evolution**)

## Parser, source ingestion, and hardening

- [`oneflow-file-parser-capabilities.md`](oneflow-file-parser-capabilities.md) — parser capability boundary for file sources (**Current baseline + future evolution**)
- [`file-ingestion-hardening.md`](file-ingestion-hardening.md) — shipped and follow-on file-ingestion hardening direction (**Current baseline + future evolution**)
- [`file-ingestion-hardening-checklist.md`](file-ingestion-hardening-checklist.md) — checklist for the file-ingestion hardening slice (**Current baseline + future evolution**)
- [`hardening-documentation-sync-checklist.md`](hardening-documentation-sync-checklist.md) — implementation-to-documentation sync note for hardening work (**Current baseline + future evolution**)
- [`native-parser-adoptability.md`](native-parser-adoptability.md) — future boundary for C/C++ or other native parser engines (**Future direction**)
- [`csv-native-parser-sidecar-protocol.md`](csv-native-parser-sidecar-protocol.md) — concrete CSV-first sidecar protocol sketch for future native parsing (**Future direction**)
- [`java-native-parser-reader-adapter-contract.md`](java-native-parser-reader-adapter-contract.md) — Java-side `DynamicReader` / `ItemStreamReader` adapter contract for future sidecar-backed native parsing (**Future direction**)
- [`validation-extension-architecture.md`](validation-extension-architecture.md) — source-validation and processor-rule extension architecture (**Current baseline + future evolution**)

## Transformation, enrichment, and mapping growth

- [`extension-points.md`](extension-points.md) — where readers, processors, writers, and validation seams live (**Current baseline + future evolution**)
- [`transformation-capability-roadmap.md`](transformation-capability-roadmap.md) — phased transformation maturity direction (**Future direction**)
- [`transformation-capability-catalog.md`](transformation-capability-catalog.md) — comprehensive now/next/future transformation family catalog with examples and backlog-seeding map (**Current baseline + future evolution**)
- [`reference-set-validation-and-enrichment.md`](reference-set-validation-and-enrichment.md) — future processor-side reference-set validation and later enrichment growth (**Future direction**)
- [`t6-shared-default-value-mapping-syntax-comparison.md`](t6-shared-default-value-mapping-syntax-comparison.md) — future-only comparison for shared default-value mapping syntax under `T6` (**Future direction**)

## Control plane, scheduling, and retained operational data

- [`control-plane-worker-boundary.md`](control-plane-worker-boundary.md) — future boundary between ETL core worker and optional control plane (**Future direction**)
- [`control-plane-operational-data-model.md`](control-plane-operational-data-model.md) — retained data model for schedules, triggers, runs, steps, and artifacts (**Future direction**)
- [`control-plane-local-relational-schema.md`](control-plane-local-relational-schema.md) — SQLite-first local persistence direction for control-plane history (**Future direction**)

## Observability, operations, and watchpoints

- [`job-history-and-operational-observability.md`](job-history-and-operational-observability.md) — current observability baseline plus retained-history direction (**Current baseline + future evolution**)
- [`ai-assisted-operations-intelligence.md`](ai-assisted-operations-intelligence.md) — future AI-assisted operator-support direction (**Future direction**)
- [`architectural-risks-and-watchpoints.md`](architectural-risks-and-watchpoints.md) — key risks to watch as the roadmap evolves (**Current baseline + future evolution**)
- [`security-test-strategy.md`](security-test-strategy.md) — security testing layers, PR gates, evidence model, and rollout plan (**Current baseline + future evolution**)

## Connector and transport directions

- [`relational-db-support.md`](relational-db-support.md) — relational support baseline and future hardening direction (**Current baseline + future evolution**)
- [`sftp-transport-capability.md`](sftp-transport-capability.md) — staged SFTP transport direction and deployment boundary (**Future direction**)

## Roadmap and decision support

- [`etl-product-evolution-roadmap.md`](etl-product-evolution-roadmap.md) — high-level guide for what belongs now vs later (**Current baseline + future evolution**)
- [`TEMPLATE.md`](TEMPLATE.md) — template for new architecture notes (**Template**)

## Suggested usage pattern

When working in `docs/architecture/`:

1. start from this index to find the closest existing note
2. update the most relevant note before creating a new one when the concern already exists
3. add a new note only when the change introduces a genuinely new architectural topic or boundary
4. add or update an ADR when the change records a meaningful design choice or tradeoff

## Related docs

- [`../README.md`](../README.md)
- [`../adr/TEMPLATE.md`](../adr/TEMPLATE.md)
- [`../product/product-backlog.md`](../product/product-backlog.md)

