# ADR index

This folder contains Architecture Decision Records (ADRs) that capture why significant design choices were made.

## Categories

- [`foundations/`](foundations/README.md) - cross-cutting and governance decision landing zone
- [`etl-core/`](etl-core/README.md) - ETL runtime and execution-contract decision landing zone
- [`control-plane/`](control-plane/README.md) - scheduler/control-plane decision landing zone
- [`operator-ui/`](operator-ui/README.md) - operator-UI decision landing zone

## Current ADRs by category

### Foundations

- [`foundations/0001-use-architecture-docs-and-adrs.md`](foundations/0001-use-architecture-docs-and-adrs.md)
- [`foundations/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md`](foundations/0005-use-shared-verification-evidence-for-markdown-and-html-reports.md)
- [`foundations/0012-adopt-capability-first-hypercare-evolution.md`](foundations/0012-adopt-capability-first-hypercare-evolution.md)
- [`foundations/0013-keep-spring-etl-engine-technical-identity-and-oneflow-product-name.md`](foundations/0013-keep-spring-etl-engine-technical-identity-and-oneflow-product-name.md)

### ETL core

- [`etl-core/0002-config-driven-etl-pipeline.md`](etl-core/0002-config-driven-etl-pipeline.md)
- [`etl-core/0003-adaptive-step-selection-and-generated-model-contract.md`](etl-core/0003-adaptive-step-selection-and-generated-model-contract.md)
- [`etl-core/0004-use-explicit-job-config-for-business-scenario-selection.md`](etl-core/0004-use-explicit-job-config-for-business-scenario-selection.md)
- [`etl-core/0006-separate-source-validation-and-processor-rule-spis.md`](etl-core/0006-separate-source-validation-and-processor-rule-spis.md)
- [`etl-core/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md`](etl-core/0007-add-separate-processor-transform-spi-for-cleaning-and-normalization.md)
- [`etl-core/0010-keep-native-parsers-behind-java-reader-boundary.md`](etl-core/0010-keep-native-parsers-behind-java-reader-boundary.md)
- [`etl-core/0011-enforce-single-default-processor-contract.md`](etl-core/0011-enforce-single-default-processor-contract.md)

### Control plane

- [`control-plane/0008-formalize-control-plane-and-etl-worker-boundary.md`](control-plane/0008-formalize-control-plane-and-etl-worker-boundary.md)
- [`control-plane/0009-formalize-sqlite-first-local-control-plane-persistence.md`](control-plane/0009-formalize-sqlite-first-local-control-plane-persistence.md)

### Operator UI

- no ADRs assigned yet


