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

- **Shipped** - part of the active runtime path today
- **Current baseline + future evolution** - starts from the shipped baseline and preserves the next direction
- **Future direction** - design guidance, not a shipped runtime path today
- **Template** - authoring helper, not a product/runtime contract

## Folder map

The architecture set now starts with four layer-oriented buckets:

- [`foundations/README.md`](foundations/README.md) - cross-cutting architecture baseline, roadmap fit, and broad guardrails
- [`etl-core/README.md`](etl-core/README.md) - shipped ETL worker runtime, execution flow, and extension seams
- [`control-plane/README.md`](control-plane/README.md) - optional scheduler, trigger, watcher, and retained-history backend direction
- [`operator-ui/README.md`](operator-ui/README.md) - future admin, monitoring, scheduling, and job-authoring UI direction

## Migration note

The layer-folder reorganization is now active for architecture notes.

- new architecture notes should land in the layer folder that matches their concern
- links should target layer-folder paths directly

## Start here

If you are not sure where to begin, use one of these entry points first:

- [`foundations/README.md`](foundations/README.md) - cross-cutting architecture baseline and roadmap entry points
- [`etl-core/README.md`](etl-core/README.md) - shipped ETL runtime flow and extension seams
- [`control-plane/README.md`](control-plane/README.md) - optional scheduler/control-plane boundary and retained-history direction
- [`operator-ui/README.md`](operator-ui/README.md) - future operator UI direction for admin, monitoring, scheduling, and authoring
- [`scenario-driven-runtime-direction.md`](etl-core/scenario-driven-runtime-direction.md) - next runtime-direction target (**Future direction**)

## Recently added / high-signal notes

Use this short list when you want the newest or most actively discussed architecture topics first:

- [`oneflow-file-parser-capabilities.md`](etl-core/oneflow-file-parser-capabilities.md) - parser capability boundary for file sources (**Current baseline + future evolution**)
- [`native-parser-adoptability.md`](etl-core/native-parser-adoptability.md) - future boundary for C/C++ or other native parser engines (**Future direction**)
- [`csv-native-parser-sidecar-protocol.md`](etl-core/csv-native-parser-sidecar-protocol.md) - concrete CSV-first sidecar protocol sketch for future native parsing (**Future direction**)
- [`java-native-parser-reader-adapter-contract.md`](etl-core/java-native-parser-reader-adapter-contract.md) - Java-side adapter contract for future sidecar-backed native parsing (**Future direction**)
- [`generated-model-naming-standard.md`](etl-core/generated-model-naming-standard.md) - selected-job naming/package standard and bridge-cleanup direction (**Current baseline + future evolution**)
- [`job-history-and-operational-observability.md`](control-plane/job-history-and-operational-observability.md) - current observability baseline plus retained-history direction (**Current baseline + future evolution**)
- [`security-test-strategy.md`](foundations/security-test-strategy.md) - phased security test strategy aligned to selected-job runtime and verification evidence (**Current baseline + future evolution**)
- [`control-plane/scheduler-architecture-direction.md`](control-plane/scheduler-architecture-direction.md) - scheduler direction under the optional control plane, including the current S1 contract-freeze checkpoint for launch/evidence boundary alignment (**Future direction**)
- [`control-plane/operator-ui-mvp-api-surface.md`](control-plane/operator-ui-mvp-api-surface.md) - first control-plane REST API surface for the Angular MVP UI screens (**Future direction**)
- [`control-plane/operator-ui-mvp-openapi.yaml`](control-plane/operator-ui-mvp-openapi.yaml) - machine-readable OpenAPI 3.1 draft for the operator UI MVP API surface (**Future direction**)
- [`operator-ui/operator-ui-architecture-direction.md`](operator-ui/operator-ui-architecture-direction.md) - operator UI direction for admin/monitoring/scheduling/authoring, including S1 -> U3 freeze-checkpoint alignment for guarded ad hoc trigger-now behavior (**Future direction**)
- [`operator-ui/angular-ui-mvp-structure.md`](operator-ui/angular-ui-mvp-structure.md) - practical Angular-based MVP structure for first-screen wireframes, routes, components, and API client boundaries (**Future direction**)
- [`operator-ui/angular-ui-mvp-wireframes.md`](operator-ui/angular-ui-mvp-wireframes.md) - low-fidelity wireframes for the first five Angular MVP operator screens and their drill-down flows (**Future direction**)

## Layer-oriented indexes

- [`foundations/README.md`](foundations/README.md)
- [`etl-core/README.md`](etl-core/README.md)
- [`control-plane/README.md`](control-plane/README.md)
- [`operator-ui/README.md`](operator-ui/README.md)

## Runtime foundations and flow

- [`overview.md`](foundations/overview.md) - current high-level system architecture (**Current baseline + future evolution**)
- [`runtime-flow.md`](etl-core/runtime-flow.md) - end-to-end ETL runtime flow (**Shipped**)
- [`oneflow-runtime-fallback-reference.md`](etl-core/oneflow-runtime-fallback-reference.md) - consolidated matrix of shipped runtime fallback/default decisions (**Shipped**)
- [`csv-to-xml-runtime-flow.md`](etl-core/csv-to-xml-runtime-flow.md) - operational deep dive for the shipped CSV-to-XML path (**Current baseline + future evolution**)
- [`runtime-flow-walkthrough.html`](etl-core/runtime-flow-walkthrough.html) - animated walkthrough of the shipped runtime path (**Current baseline + future evolution**)
- [`hierarchical-flow-composition.md`](etl-core/hierarchical-flow-composition.md) - reusable `MainFlow -> SubFlow -> Step` direction (**Future direction**)
- [`flow-normalization-rules.md`](etl-core/flow-normalization-rules.md) - normalization rules for simple and complex flows (**Future direction**)
- [`scenario-driven-runtime-direction.md`](etl-core/scenario-driven-runtime-direction.md) - target next-direction runtime contract (**Future direction**)
- [`runtime-to-scenario-gap-assessment.md`](etl-core/runtime-to-scenario-gap-assessment.md) - current-to-target gap assessment (**Future direction**)
- [`1-4-to-next-architecture-classification.md`](etl-core/1-4-to-next-architecture-classification.md) - transition map for classifying current code during the next architecture shift (**Future direction**)
- [`job-level-activation-and-startup-guardrails.md`](etl-core/job-level-activation-and-startup-guardrails.md) - job-level `isActive` guardrail and startup fail-fast rules (**Shipped**)
- [`generated-model-naming-standard.md`](etl-core/generated-model-naming-standard.md) - selected-job naming/package standard and remaining bridge cleanup direction (**Current baseline + future evolution**)

## Parser, source ingestion, and hardening

- [`oneflow-file-parser-capabilities.md`](etl-core/oneflow-file-parser-capabilities.md) - parser capability boundary for file sources (**Current baseline + future evolution**)
- [`file-ingestion-hardening.md`](etl-core/file-ingestion-hardening.md) - shipped and follow-on file-ingestion hardening direction (**Current baseline + future evolution**)
- [`file-ingestion-hardening-checklist.md`](etl-core/file-ingestion-hardening-checklist.md) - checklist for the file-ingestion hardening slice (**Current baseline + future evolution**)
- [`hardening-documentation-sync-checklist.md`](etl-core/hardening-documentation-sync-checklist.md) - implementation-to-documentation sync note for hardening work (**Current baseline + future evolution**)
- [`native-parser-adoptability.md`](etl-core/native-parser-adoptability.md) - future boundary for C/C++ or other native parser engines (**Future direction**)
- [`csv-native-parser-sidecar-protocol.md`](etl-core/csv-native-parser-sidecar-protocol.md) - concrete CSV-first sidecar protocol sketch for future native parsing (**Future direction**)
- [`java-native-parser-reader-adapter-contract.md`](etl-core/java-native-parser-reader-adapter-contract.md) - Java-side `DynamicReader` / `ItemStreamReader` adapter contract for future sidecar-backed native parsing (**Future direction**)
- [`validation-extension-architecture.md`](etl-core/validation-extension-architecture.md) - source-validation and processor-rule extension architecture (**Current baseline + future evolution**)

## Transformation, enrichment, and mapping growth

- [`extension-points.md`](etl-core/extension-points.md) - where readers, processors, writers, and validation seams live (**Current baseline + future evolution**)
- [`custom-step-pairing-and-context-handoff.md`](etl-core/custom-step-pairing-and-context-handoff.md) - future custom-step pairing seam for customer-owned pre/post steps, context handoff, and failure-category boundaries (**Future direction**)
- [`customer-owned-processor-transform-seam.md`](etl-core/customer-owned-processor-transform-seam.md) - future customer-owned processor transform seam for additive project-specific field transformation without core forking (**Future direction**)
- [`a7-t16-extensibility-charter.md`](etl-core/a7-t16-extensibility-charter.md) - unified extensibility charter for job-level custom steps plus processor-level custom transforms (**Future direction**)
- [`transformation-capability-roadmap.md`](etl-core/transformation-capability-roadmap.md) - phased transformation maturity direction (**Future direction**)
- [`transformation-capability-catalog.md`](etl-core/transformation-capability-catalog.md) - comprehensive now/next/future transformation family catalog with examples and backlog-seeding map (**Current baseline + future evolution**)
- [`reference-set-validation-and-enrichment.md`](etl-core/reference-set-validation-and-enrichment.md) - future processor-side reference-set validation and later enrichment growth (**Future direction**)
- [`t6-shared-default-value-mapping-syntax-comparison.md`](etl-core/t6-shared-default-value-mapping-syntax-comparison.md) - future-only comparison for shared default-value mapping syntax under `T6` (**Future direction**)

## Control plane, scheduling, and retained operational data

- [`control-plane-worker-boundary.md`](control-plane/control-plane-worker-boundary.md) - future boundary between ETL core worker and optional control plane (**Future direction**)
- [`control-plane-operational-data-model.md`](control-plane/control-plane-operational-data-model.md) - retained data model for schedules, triggers, runs, steps, and artifacts (**Future direction**)
- [`control-plane-local-relational-schema.md`](control-plane/control-plane-local-relational-schema.md) - SQLite-first local persistence direction for control-plane history (**Future direction**)
- [`control-plane/scheduler-architecture-direction.md`](control-plane/scheduler-architecture-direction.md) - scheduler backend direction that preserves the selected-job launch contract (**Future direction**)
- [`control-plane/operator-ui-mvp-api-surface.md`](control-plane/operator-ui-mvp-api-surface.md) - first API contract map for Jobs, Runs, Run detail, Schedules, and System MVP screens (**Future direction**)
- [`control-plane/operator-ui-mvp-openapi.yaml`](control-plane/operator-ui-mvp-openapi.yaml) - OpenAPI 3.1 YAML draft for machine-readable contract validation and client generation work (**Future direction**)

## Operator UI direction

- [`operator-ui/README.md`](operator-ui/README.md) - UI-layer entry point for admin, monitoring, scheduling, and authoring notes (**Future direction**)
- [`operator-ui/operator-ui-architecture-direction.md`](operator-ui/operator-ui-architecture-direction.md) - first UI architecture direction over the optional control plane (**Future direction**)
- [`operator-ui/angular-ui-mvp-structure.md`](operator-ui/angular-ui-mvp-structure.md) - Angular-oriented MVP structure for first operator screens, route map, and control-plane-facing client contracts (**Future direction**)
- [`operator-ui/angular-ui-mvp-wireframes.md`](operator-ui/angular-ui-mvp-wireframes.md) - low-fidelity Jobs, Runs, Run detail, Schedules, and System wireframes for the Angular MVP (**Future direction**)

## Observability, operations, and watchpoints

- [`job-history-and-operational-observability.md`](control-plane/job-history-and-operational-observability.md) - current observability baseline plus retained-history direction (**Current baseline + future evolution**)
- [`ai-assisted-operations-intelligence.md`](operator-ui/ai-assisted-operations-intelligence.md) - future AI-assisted operator-support direction (**Future direction**)
- [`architectural-risks-and-watchpoints.md`](foundations/architectural-risks-and-watchpoints.md) - key risks to watch as the roadmap evolves (**Current baseline + future evolution**)
- [`security-test-strategy.md`](foundations/security-test-strategy.md) - security testing layers, PR gates, evidence model, and rollout plan (**Current baseline + future evolution**)

## Connector and transport directions

- [`relational-db-support.md`](etl-core/relational-db-support.md) - relational support baseline and future hardening direction (**Current baseline + future evolution**)
- [`sftp-transport-capability.md`](etl-core/sftp-transport-capability.md) - staged SFTP transport direction and deployment boundary (**Future direction**)

## Roadmap and decision support

- [`etl-product-evolution-roadmap.md`](foundations/etl-product-evolution-roadmap.md) - high-level guide for what belongs now vs later (**Current baseline + future evolution**)
- [`TEMPLATE.md`](foundations/TEMPLATE.md) - template for new architecture notes (**Template**)

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
