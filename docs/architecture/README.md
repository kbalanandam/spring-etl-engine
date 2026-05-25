# Architecture index

Use this page as the folder-level index for `docs/architecture/`.

It now organizes the design notes by **product layer first** so the architecture set stays navigable as OneFlow grows across ETL runtime, optional control-plane work, and future operator UI design.

## Purpose

This index is the best starting point when you know you need an architecture note, but do not yet know the exact filename.

Use it to:

- browse architecture notes by topic instead of scanning one long flat list
- find the right note before changing runtime, config, extension points, or future-direction docs
- discover newer parser, control-plane, and observability notes without relying on memory

## Status labels used here

- **Shipped** â€” part of the active runtime path today
- **Current baseline + future evolution** â€” starts from the shipped baseline and preserves the next direction
- **Future direction** â€” design guidance, not a shipped runtime path today
- **Template** â€” authoring helper, not a product/runtime contract

## Folder map

The architecture set now starts with four layer-oriented buckets:

- [`foundations/README.md`](foundations/README.md) â€” cross-cutting architecture baseline, roadmap fit, and broad guardrails
- [`etl-core/README.md`](etl-core/README.md) â€” shipped ETL worker runtime, execution flow, and extension seams
- [`control-plane/README.md`](control-plane/README.md) â€” optional scheduler, trigger, watcher, and retained-history backend direction
- [`operator-ui/README.md`](operator-ui/README.md) â€” future admin, monitoring, scheduling, and job-authoring UI direction

## Migration note

This is the first pass of the reorganization.

- the new folder taxonomy is now in place
- new architecture notes should land in the layer folder that matches their concern
- legacy root paths now use temporary compatibility stubs that point to the new layer folders
- new updates should target the layer-folder paths directly and remove stubs in a follow-on cleanup once downstream links are updated

## Start here

If you are not sure where to begin, use one of these entry points first:

- [`foundations/README.md`](foundations/README.md) â€” cross-cutting architecture baseline and roadmap entry points
- [`etl-core/README.md`](etl-core/README.md) â€” shipped ETL runtime flow and extension seams
- [`control-plane/README.md`](control-plane/README.md) â€” optional scheduler/control-plane boundary and retained-history direction
- [`operator-ui/README.md`](operator-ui/README.md) â€” future operator UI direction for admin, monitoring, scheduling, and authoring
- [`scenario-driven-runtime-direction.md`](scenario-driven-runtime-direction.md) â€” next runtime-direction target (**Future direction**)

## Recently added / high-signal notes

Use this short list when you want the newest or most actively discussed architecture topics first:

- [`oneflow-file-parser-capabilities.md`](oneflow-file-parser-capabilities.md) â€” parser capability boundary for file sources (**Current baseline + future evolution**)
- [`native-parser-adoptability.md`](native-parser-adoptability.md) â€” future boundary for C/C++ or other native parser engines (**Future direction**)
- [`csv-native-parser-sidecar-protocol.md`](csv-native-parser-sidecar-protocol.md) â€” concrete CSV-first sidecar protocol sketch for future native parsing (**Future direction**)
- [`java-native-parser-reader-adapter-contract.md`](java-native-parser-reader-adapter-contract.md) â€” Java-side adapter contract for future sidecar-backed native parsing (**Future direction**)
- [`generated-model-naming-standard.md`](generated-model-naming-standard.md) â€” selected-job naming/package standard and bridge-cleanup direction (**Current baseline + future evolution**)
- [`job-history-and-operational-observability.md`](job-history-and-operational-observability.md) â€” current observability baseline plus retained-history direction (**Current baseline + future evolution**)
- [`security-test-strategy.md`](security-test-strategy.md) â€” phased security test strategy aligned to selected-job runtime and verification evidence (**Current baseline + future evolution**)
- [`control-plane/scheduler-architecture-direction.md`](control-plane/scheduler-architecture-direction.md) â€” first scheduler-specific architecture direction under the optional control plane (**Future direction**)
- [`operator-ui/operator-ui-architecture-direction.md`](operator-ui/operator-ui-architecture-direction.md) â€” first operator UI architecture direction for admin, monitoring, scheduling, and job authoring (**Future direction**)

## Layer-oriented indexes

- [`foundations/README.md`](foundations/README.md)
- [`etl-core/README.md`](etl-core/README.md)
- [`control-plane/README.md`](control-plane/README.md)
- [`operator-ui/README.md`](operator-ui/README.md)

## Runtime foundations and flow

- [`overview.md`](overview.md) â€” current high-level system architecture (**Current baseline + future evolution**)
- [`runtime-flow.md`](runtime-flow.md) â€” end-to-end ETL runtime flow (**Shipped**)
- [`oneflow-runtime-fallback-reference.md`](oneflow-runtime-fallback-reference.md) â€” consolidated matrix of shipped runtime fallback/default decisions (**Shipped**)
- [`csv-to-xml-runtime-flow.md`](csv-to-xml-runtime-flow.md) â€” operational deep dive for the shipped CSV-to-XML path (**Current baseline + future evolution**)
- [`runtime-flow-walkthrough.html`](runtime-flow-walkthrough.html) â€” animated walkthrough of the shipped runtime path (**Current baseline + future evolution**)
- [`hierarchical-flow-composition.md`](hierarchical-flow-composition.md) â€” reusable `MainFlow -> SubFlow -> Step` direction (**Future direction**)
- [`flow-normalization-rules.md`](flow-normalization-rules.md) â€” normalization rules for simple and complex flows (**Future direction**)
- [`scenario-driven-runtime-direction.md`](scenario-driven-runtime-direction.md) â€” target next-direction runtime contract (**Future direction**)
- [`runtime-to-scenario-gap-assessment.md`](runtime-to-scenario-gap-assessment.md) â€” current-to-target gap assessment (**Future direction**)
- [`1-4-to-next-architecture-classification.md`](1-4-to-next-architecture-classification.md) â€” transition map for classifying current code during the next architecture shift (**Future direction**)
- [`job-level-activation-and-startup-guardrails.md`](job-level-activation-and-startup-guardrails.md) â€” job-level `isActive` guardrail and startup fail-fast rules (**Shipped**)
- [`generated-model-naming-standard.md`](generated-model-naming-standard.md) â€” selected-job naming/package standard and remaining bridge cleanup direction (**Current baseline + future evolution**)

## Parser, source ingestion, and hardening

- [`oneflow-file-parser-capabilities.md`](oneflow-file-parser-capabilities.md) â€” parser capability boundary for file sources (**Current baseline + future evolution**)
- [`file-ingestion-hardening.md`](file-ingestion-hardening.md) â€” shipped and follow-on file-ingestion hardening direction (**Current baseline + future evolution**)
- [`file-ingestion-hardening-checklist.md`](file-ingestion-hardening-checklist.md) â€” checklist for the file-ingestion hardening slice (**Current baseline + future evolution**)
- [`hardening-documentation-sync-checklist.md`](hardening-documentation-sync-checklist.md) â€” implementation-to-documentation sync note for hardening work (**Current baseline + future evolution**)
- [`native-parser-adoptability.md`](native-parser-adoptability.md) â€” future boundary for C/C++ or other native parser engines (**Future direction**)
- [`csv-native-parser-sidecar-protocol.md`](csv-native-parser-sidecar-protocol.md) â€” concrete CSV-first sidecar protocol sketch for future native parsing (**Future direction**)
- [`java-native-parser-reader-adapter-contract.md`](java-native-parser-reader-adapter-contract.md) â€” Java-side `DynamicReader` / `ItemStreamReader` adapter contract for future sidecar-backed native parsing (**Future direction**)
- [`validation-extension-architecture.md`](validation-extension-architecture.md) â€” source-validation and processor-rule extension architecture (**Current baseline + future evolution**)

## Transformation, enrichment, and mapping growth

- [`extension-points.md`](extension-points.md) â€” where readers, processors, writers, and validation seams live (**Current baseline + future evolution**)
- [`transformation-capability-roadmap.md`](transformation-capability-roadmap.md) â€” phased transformation maturity direction (**Future direction**)
- [`transformation-capability-catalog.md`](transformation-capability-catalog.md) â€” comprehensive now/next/future transformation family catalog with examples and backlog-seeding map (**Current baseline + future evolution**)
- [`reference-set-validation-and-enrichment.md`](reference-set-validation-and-enrichment.md) â€” future processor-side reference-set validation and later enrichment growth (**Future direction**)
- [`t6-shared-default-value-mapping-syntax-comparison.md`](t6-shared-default-value-mapping-syntax-comparison.md) â€” future-only comparison for shared default-value mapping syntax under `T6` (**Future direction**)

## Control plane, scheduling, and retained operational data

- [`control-plane-worker-boundary.md`](control-plane-worker-boundary.md) â€” future boundary between ETL core worker and optional control plane (**Future direction**)
- [`control-plane-operational-data-model.md`](control-plane-operational-data-model.md) â€” retained data model for schedules, triggers, runs, steps, and artifacts (**Future direction**)
- [`control-plane-local-relational-schema.md`](control-plane-local-relational-schema.md) â€” SQLite-first local persistence direction for control-plane history (**Future direction**)
- [`control-plane/scheduler-architecture-direction.md`](control-plane/scheduler-architecture-direction.md) â€” scheduler backend direction that preserves the selected-job launch contract (**Future direction**)

## Operator UI direction

- [`operator-ui/README.md`](operator-ui/README.md) â€” UI-layer entry point for admin, monitoring, scheduling, and authoring notes (**Future direction**)
- [`operator-ui/operator-ui-architecture-direction.md`](operator-ui/operator-ui-architecture-direction.md) â€” first UI architecture direction over the optional control plane (**Future direction**)

## Observability, operations, and watchpoints

- [`job-history-and-operational-observability.md`](job-history-and-operational-observability.md) â€” current observability baseline plus retained-history direction (**Current baseline + future evolution**)
- [`ai-assisted-operations-intelligence.md`](ai-assisted-operations-intelligence.md) â€” future AI-assisted operator-support direction (**Future direction**)
- [`architectural-risks-and-watchpoints.md`](architectural-risks-and-watchpoints.md) â€” key risks to watch as the roadmap evolves (**Current baseline + future evolution**)
- [`security-test-strategy.md`](security-test-strategy.md) â€” security testing layers, PR gates, evidence model, and rollout plan (**Current baseline + future evolution**)

## Connector and transport directions

- [`relational-db-support.md`](relational-db-support.md) â€” relational support baseline and future hardening direction (**Current baseline + future evolution**)
- [`sftp-transport-capability.md`](sftp-transport-capability.md) â€” staged SFTP transport direction and deployment boundary (**Future direction**)

## Roadmap and decision support

- [`etl-product-evolution-roadmap.md`](etl-product-evolution-roadmap.md) â€” high-level guide for what belongs now vs later (**Current baseline + future evolution**)
- [`TEMPLATE.md`](TEMPLATE.md) â€” template for new architecture notes (**Template**)

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

