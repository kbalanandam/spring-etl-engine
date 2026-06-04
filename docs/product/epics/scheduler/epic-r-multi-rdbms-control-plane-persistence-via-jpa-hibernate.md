# Epic R - Multi-RDBMS control-plane persistence via JPA/Hibernate

## Summary

Epic R defines how OneFlow control-plane persistence moves from SQLite-oriented JDBC SQL to a deploy-time configurable JPA/Hibernate model that can support major relational databases without changing the selected-job ETL runtime contract.

## Scope

This epic is the home for work that:

- freezes the persistence boundary and non-goals before implementation expands
- defines deployment-time datasource/dialect configuration for major RDBMS targets
- introduces portable JPA/Hibernate entities and repository behavior for retained control-plane history
- introduces cross-RDBMS schema migration/versioning governance
- proves parity and fallback behavior across supported relational engines

This epic is **not** the place to make control-plane persistence mandatory for ETL runs or redesign the selected-job launch contract.

## Related backlog items

- [`R1 - Freeze JPA/Hibernate control-plane persistence boundary`](../../backlog-items/scheduler/R1-freeze-jpa-hibernate-control-plane-persistence-boundary.md)
- [`R2 - Define multi-RDBMS datasource and dialect profile contract`](../../backlog-items/scheduler/R2-multi-rdbms-datasource-and-dialect-profile-contract.md)
- [`R3 - Add JPA/Hibernate entities and repositories for control-plane history`](../../backlog-items/scheduler/R3-jpa-hibernate-entities-and-repositories-for-control-plane-history.md)
- [`R4 - Introduce cross-RDBMS schema migration baseline`](../../backlog-items/scheduler/R4-cross-rdbms-schema-migration-baseline.md)
- [`R5 - Prove multi-RDBMS parity and fallback behavior`](../../backlog-items/scheduler/R5-multi-rdbms-parity-and-fallback-verification.md)

## Related docs

- [`../../product-backlog.md`](../../product-backlog.md)
- [`../../../architecture/control-plane/control-plane-worker-boundary.md`](../../../architecture/control-plane/control-plane-worker-boundary.md)
- [`../../../architecture/control-plane/control-plane-operational-data-model.md`](../../../architecture/control-plane/control-plane-operational-data-model.md)
- [`../../../architecture/control-plane/control-plane-local-relational-schema.md`](../../../architecture/control-plane/control-plane-local-relational-schema.md)
- [`../../../adr/control-plane/0008-formalize-control-plane-and-etl-worker-boundary.md`](../../../adr/control-plane/0008-formalize-control-plane-and-etl-worker-boundary.md)
- [`../../../adr/control-plane/0009-formalize-sqlite-first-local-control-plane-persistence.md`](../../../adr/control-plane/0009-formalize-sqlite-first-local-control-plane-persistence.md)

## Maintenance note

Use [`../../product-backlog.md`](../../product-backlog.md) for live item-level board fields. Use this page for the shared Epic R boundary and phased multi-RDBMS persistence intent.

