# ADR-0009: formalize SQLite-first local control-plane persistence

- Status: Accepted
- Date: 2026-05-15

## Context

The product now has a documented future direction for optional control-plane retained history and persistence:

- [`ADR-0008`](0008-formalize-control-plane-and-etl-worker-boundary.md) freezes the boundary that keeps the ETL worker independently runnable and the control plane optional.
- [`control-plane-operational-data-model.md`](../architecture/control-plane/control-plane-operational-data-model.md) defines the conceptual retained entities for schedules, watchers, trigger events, runs, steps, artifacts, and recovery anchors.
- [`control-plane-local-relational-schema.md`](../architecture/control-plane/control-plane-local-relational-schema.md) defines a first relational shape for that optional control-plane history.
- [`job-history-and-operational-observability.md`](../architecture/control-plane/job-history-and-operational-observability.md) preserves the broader observability and retained-history direction that later operator search, drill-down, and recovery workflows depend on.

At this stage, contributors need a practical persistence direction that supports local development and single-node control-plane experimentation without requiring immediate shared database infrastructure.

At the same time, that early persistence choice must not create accidental product lock-in:

- the ETL core must still run with no control-plane database present
- the logical retained model must stay portable to stronger relational deployment targets later
- external schedulers and orchestrators must remain first-class launchers of the same selected-job contract
- early convenience must not become a hidden SQLite-only architectural assumption

## Decision

The product formally adopts this persistence direction:

1. **SQLite is acceptable as the first local relational persistence target for the optional control plane.** It is appropriate for developer-laptop and single-node experimentation around scheduler, watcher, retained run history, and operator-facing local workflows.
2. **The ETL core remains independently runnable with no control-plane database prerequisite.** Direct selected-job execution through `etl.config.job` must not depend on SQLite or any other control-plane persistence layer.
3. **The logical retained model must stay portable.** Early schema and repository choices should preserve clean portability to stronger relational databases such as PostgreSQL or SQL Server later.
4. **SQLite is a first convenience target, not the permanent product-wide storage commitment.** As retained operational history, concurrency, multi-user control-plane access, and broader operator workflows grow, stronger relational deployment targets remain in scope.
5. **Portable relational modeling takes priority over SQLite-specific shortcuts.** Core trigger, run, step, artifact, attempt, and checkpoint relationships should remain queryable and relational rather than being hidden behind opaque SQLite-specific storage patterns.

## Consequences

### Positive

- makes early control-plane development practical on a personal laptop without extra infrastructure
- gives scheduler, watcher, and retained-history work a concrete first persistence target
- preserves the optional-control-plane rule from `ADR-0008`
- keeps the product open to PostgreSQL or SQL Server later without rethinking the logical model from scratch
- encourages portable relational design before vendor-specific tuning becomes necessary

### Negative

- the first implementation may under-represent later concurrency and multi-user deployment concerns
- some contributors may be tempted to rely on SQLite-specific conveniences unless portability stays explicit
- future migration work will still be required when stronger relational deployment targets are introduced
- documentation and schema reviews must stay disciplined so "SQLite-first" is not misread as "SQLite-only"

## Alternatives considered

- **Wait until PostgreSQL or SQL Server is selected before defining any persistence direction** - rejected because that would slow local development and postpone useful design discipline for optional control-plane history.
- **Treat SQLite as the permanent control-plane store** - rejected because the product direction already anticipates stronger relational deployment targets as history volume, operator concurrency, and enterprise requirements grow.
- **Keep the retained model mostly in generic blobs or files instead of a relational shape** - rejected because trigger, run, step, artifact, and recovery lineage need to remain queryable, auditable, and portable.

## Notes

- This ADR freezes the first persistence direction, not a final production deployment standard.
- Later PostgreSQL or SQL Server adoption should preserve the same logical entity meanings even when indexing, retention, concurrency, or migration strategies become more advanced.
- This ADR does not weaken the boundary from `ADR-0008`: persisted control-plane history remains additive and optional from the ETL worker point of view.

## Related

- [`ADR-0008: formalize control-plane and ETL worker boundary`](0008-formalize-control-plane-and-etl-worker-boundary.md)
- [`Control-plane operational data model`](../architecture/control-plane/control-plane-operational-data-model.md)
- [`Control-plane local relational schema`](../architecture/control-plane/control-plane-local-relational-schema.md)
- [`Job history and operational observability`](../architecture/control-plane/job-history-and-operational-observability.md)
- [`S4 - Control-plane operational data model`](../product/backlog-items/S4-control-plane-operational-data-model.md)

