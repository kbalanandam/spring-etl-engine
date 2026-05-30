# ADR-0008: formalize control-plane and ETL worker boundary

- Status: Accepted
- Date: 2026-05-15

## Context

`spring-etl-engine` already has a clear and independently runnable execution contract:

1. select one `etl.config.job`
2. resolve one `job-config.yaml`
3. load the selected source, target, and processor configuration files
4. execute the explicit ordered `steps` for that selected run

That boundary is the active ETL runtime path today.

At the same time, the product direction now includes legitimate future needs around that runtime, such as:

- time-based scheduling
- file-watcher triggers
- persisted trigger and run history
- operator-facing APIs and UI views
- later recovery and restartability workflows

Without an explicit decision, those control-plane capabilities could drift into a second orchestration model, make the built-in scheduler a hidden prerequisite for normal use, or weaken interoperability with enterprise schedulers and orchestrators that teams already use.

The product direction also needs one consistent position on implementation style:

- stay Java-first for early ETL-core and control-plane work
- allow lightweight local relational persistence such as SQLite for first laptop or single-node control-plane slices
- keep transformation maturity as a first-class roadmap track rather than letting scheduler or UI work dominate product identity

## Decision

The product formally adopts this boundary:

1. **The ETL core worker remains the only mandatory runtime component.** Teams must be able to run OneFlow directly through the selected-job contract with no built-in scheduler, watcher, persistence service, or UI present.
2. **The stable launch boundary remains the explicit selected-job contract** rooted in `etl.config.job` and `job-config.yaml`.
3. **A OneFlow-native scheduler, watcher layer, persisted operational-history service, or operator UI is optional.** Those capabilities may grow around the ETL core, but they must not replace the ETL worker runtime boundary.
4. **External schedulers, orchestrators, and platform-native triggers remain first-class launchers** of the same selected-job contract. Native scheduling is a supported launcher, not the only supported launcher.
5. **File watching belongs to the optional trigger-control layer.** Watchers may detect, classify, and stabilize candidate files, but actual job launch must still resolve through the same selected-job boundary.
6. **Persisted operational history is optional control-plane support, not a prerequisite for core ETL execution.**
7. **The first control-plane slices should stay Java-first and close to the current stack.** SQLite is acceptable for early local or single-node control-plane persistence, with stronger relational targets introduced later as operational needs expand.
8. **Transformation capability remains first-class.** Scheduler, watcher, persistence, or UI work must not displace the shared transformation roadmap.

## Consequences

### Positive

- preserves the current independently runnable ETL core model
- prevents a second execution contract from drifting away from `job-config.yaml`
- keeps external scheduler/orchestrator integration as a supported deployment pattern rather than a compatibility afterthought
- allows native control-plane features to grow without becoming mandatory for teams that do not want them
- keeps scheduling, watcher evidence, retained history, and future UI work aligned with the same worker launch contract
- protects transformation maturity as a parallel product track

### Negative

- native control-plane features cannot assume exclusive ownership of job launching
- evidence and trigger-audit design must work for both native and external triggers
- future APIs and docs must stay disciplined so optional services do not quietly become required
- some implementation choices may be slower because interoperability and optional deployment must be preserved deliberately

## Alternatives considered

- **Make the OneFlow scheduler mandatory for all launches** - rejected because it would break the independently runnable ETL core model and reduce compatibility with enterprise scheduling estates already in use.
- **Embed watcher, scheduling, and operator controls directly into the worker runtime** - rejected because it would blur trigger governance with ETL execution and make optional capabilities harder to deploy, disable, or replace.
- **Treat scheduler/control-plane work as a separate pseudo-product with its own launch contract** - rejected because it would encourage orchestration drift away from the explicit selected-job runtime that the ETL core already uses.

## Notes

- This ADR freezes the boundary and optionality rule; it does not define one final control-plane implementation.
- Follow-on work such as schedule models, watcher semantics, restartability, and retained operational history should build on this boundary instead of re-deciding it.
- The selected-job contract remains the interoperability point for direct runs, native control-plane launches, and third-party orchestration.

## Related

- [`Control plane and worker boundary`](../../architecture/control-plane/control-plane-worker-boundary.md)
- [`Control-plane local relational schema`](../../architecture/control-plane/control-plane-local-relational-schema.md)
- [`Scenario-driven runtime direction`](../../architecture/etl-core/scenario-driven-runtime-direction.md)
- [`Runtime flow`](../../architecture/etl-core/runtime-flow.md)
- [`ETL product evolution roadmap`](../../architecture/foundations/etl-product-evolution-roadmap.md)
- [`Transformation capability roadmap`](../../architecture/etl-core/transformation-capability-roadmap.md)
- [`S1 - Schedule model and trigger contract`](../../product/backlog-items/scheduler/S1-schedule-model-and-trigger-contract.md)


