# ADR-0002: Use a config-driven ETL pipeline with dynamic factories

- Status: Accepted
- Date: 2026-04-18

## Context

The engine supports multiple formats and needs to remain extensible without rewriting orchestration for each new source or target type.

The current codebase already uses:
- polymorphic config objects for sources and targets
- factory-based reader, processor, and writer selection
- Spring Batch for runtime orchestration

## Decision

The engine will continue to use a config-driven architecture where:

- source and target config types describe input/output behavior
- readers, processors, and writers are selected dynamically by type
- batch orchestration remains independent of specific formats as much as possible

## Consequences

### Positive
- new formats can be added with localized changes
- orchestration logic stays reusable
- product behavior can be expressed in YAML instead of branching Java code
- testing can focus on extension points and contracts

### Negative
- runtime contracts must stay explicit and documented
- debugging may involve both config and code paths
- config validation becomes critical as the number of formats increases

## Implications for future work

This decision supports future additions such as:
- relational database readers/writers
- API-based integrations
- stored procedure-backed step operations

Those features should extend the factory/config model rather than bypass it with special-case orchestration.

