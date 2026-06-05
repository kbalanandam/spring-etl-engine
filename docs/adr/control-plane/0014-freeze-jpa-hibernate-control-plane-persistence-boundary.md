# ADR-0014: freeze JPA/Hibernate control-plane persistence boundary

- Status: Accepted
- Date: 2026-06-04
- Accepted: 2026-06-05

## Context

Control-plane persistence currently relies on a JDBC implementation with SQL that is tightly coupled to SQLite behavior in several areas.

The product backlog now includes `Epic R` (`R1`-`R5`) to make persistence deploy-time configurable across major relational engines while preserving two existing contracts:

- selected-job ETL execution remains the primary runtime contract
- control-plane persistence remains optional and additive

Without a boundary freeze, implementation work may drift into mixed patterns (engine-specific SQL + ORM), widen runtime coupling, or unintentionally change read-model contracts consumed by operators.

## Decision

Adopt a boundary-first approach for Epic R and require portability implementation to follow these rules:

1. Keep ETL worker launch semantics unchanged (`etl.config.job` selected-job contract remains the execution boundary).
2. Keep control-plane persistence optional; direct ETL runs must remain valid when control-plane persistence is disabled or unavailable.
3. Preserve public read-model behavior for run summary, recovery, step records, and artifact records while persistence internals evolve.
4. Move toward JPA/Hibernate-backed persistence for retained control-plane history, with deploy-time datasource/dialect selection.
5. Treat schema evolution as migration-governed, portable-first, and explicit about vendor-specific deltas.

## Boundary invariants

- `etl.config.job` selected-job execution semantics remain unchanged for ETL worker launches.
- Control-plane persistence remains optional; direct ETL execution stays valid without a configured control-plane datasource.
- Public run read-model semantics remain stable for run summary, advisory recovery, step records, and artifact records.
- Portability work is delivered in phased slices (`R2`-`R5`) behind this boundary and must not bypass it.

## Consequences

### Positive

- Reduces portability risk by freezing invariants before implementation
- Keeps scheduler/control-plane evolution aligned with the optional-layer boundary
- Creates a stable decision anchor for `R2`-`R5` implementation and review

### Negative

- Adds up-front coordination before coding begins
- May require temporary dual-path support while parity is being proven

## Alternatives considered

- Keep JDBC SQL as the long-term path and add per-vendor SQL variants
- Replace persistence with JPA/Hibernate immediately without boundary freeze

## Notes

This ADR is the decision anchor for backlog items:

- `R1`: boundary freeze
- `R2`: datasource/dialect profile contract
- `R3`: JPA/Hibernate entity/repository implementation
- `R4`: cross-RDBMS schema migration baseline
- `R5`: parity and fallback verification

