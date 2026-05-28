# ADR-0012: Adopt Capability-First OneFlow Evolution with Optional Service Extraction

- Status: Accepted
- Date: 2026-05-28

## Context
OneFlow is evolving as a unified product that includes ETL runtime and control-plane capabilities such as scheduling and operational visibility. The team needs to keep delivery speed high now while preserving a safe path to split capabilities into independently scalable services later.

At this time, Hypercare is not yet a fully defined runtime role. It remains a near-term planned capability and should be represented with language that matches its current maturity.

## Decision
Use a modular-monolith-first strategy under the OneFlow umbrella, with capability boundaries that are future-service-safe.

- Treat each product capability (`ETL`, `Scheduler`, `Hypercare`, and future capabilities) as a first-class boundary in code and architecture.
- Keep OneFlow UI unified, and expose capabilities through a capability registry or equivalent feature-toggle contract so modules are plug-and-play.
- Represent Hypercare in three phases:
  - **Now**: planned capability (no mandatory runtime role yet)
  - **Near term**: in-process capability module
  - **Future**: optional independent service under OneFlow when extraction triggers are met
- Support deployable bundles that can compose enabled capabilities without changing the OneFlow product identity.
- Defer service extraction until objective triggers are present (for example independent scaling, independent release cadence, clear team ownership boundary, or stronger SLO isolation needs).

## Consequences

### Positive
- Preserves delivery speed in the current phase while avoiding premature distributed-system overhead.
- Keeps OneFlow as one coherent product with one UI integration model.
- Enables phased packaging of capability bundles without immediate repo or platform fragmentation.
- Reduces future extraction risk by enforcing capability seams early.

### Negative
- Requires disciplined boundary enforcement to avoid accidental cross-capability coupling.
- Some deployment and runtime concerns remain shared until extraction happens.
- Capability toggles and UI gating add governance and testing overhead.

## Alternatives considered
- Split into independent services immediately.
- Keep one undifferentiated monolith with no explicit capability seams.

## Notes
- This ADR does not define internals for each capability yet; it defines the product-wide architectural evolution path and wording policy.
- The existing control-plane versus ETL worker boundary remains valid and should continue to guide implementation scope.
- When capability scopes become concrete, add follow-up ADRs for data ownership, API/event contracts, and extraction criteria.


