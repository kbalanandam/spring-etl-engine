# T12 — Transformation governance and lineage evidence model

## Summary

Define governance and lineage requirements for transformation definitions, including version traceability, approval lifecycle expectations, and run-time evidence linkage.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **T8, C1**
- Sequence rank: **#3** in deferred advanced transform sequence

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

As transformation logic grows and gets reused, teams need auditable answers to: which transform definition/version ran, who changed it, and what operational evidence links to that version.

## Goal

Provide a practical governance and lineage model for transformation definitions that supports enterprise change control and auditability.

## Scope

- define transformation-definition identity/version model
- define minimum approval and change-control metadata expectations
- define run-evidence linkage to transform version/identity
- define compatibility expectations for profile upgrades

## Out of scope

- full control-plane product workflow implementation
- broad policy engine design for all product areas
- replacing current roadmap/backlog governance process

## Proposed approach

- begin with lightweight explicit metadata and version linkage
- align with existing run-summary and observability evidence models
- expand to stricter approval controls as adoption matures

## Operator / runtime impact

- easier audit and compliance reporting for transform changes
- safer rollout of shared transform updates
- clearer diagnosis when a behavior change is version-driven

## Concrete transformation examples

```yaml
# conceptual governance metadata attached to profile definitions
profiles:
  - name: customer-normalization
    version: 2.1.0
    governance:
      ownerTeam: data-platform
      changeTicket: CHG-1042
      approvedBy: architecture-review-board
      approvedAt: 2026-05-19T10:30:00Z
```

Conceptual run-evidence payload shape:

```json
{
  "event": "RUN_SUMMARY",
  "scenario": "customer-load",
  "transformProfile": "customer-normalization",
  "transformProfileVersion": "2.1.0",
  "governanceChangeTicket": "CHG-1042"
}
```

Expected behavior:

- each run can be traced to transform definition identity and version
- evidence payloads include governance references without exposing sensitive policy internals
- profile upgrades with breaking changes are rejected unless compatibility policy allows them

## Developer expectations

- define minimal required governance metadata for first slice; keep optional fields explicit
- align lineage fields with existing run/step correlation IDs and verification report schema
- add startup validation for missing required governance attributes on governed profiles
- include at least one end-to-end example from profile definition to run evidence linkage

## Acceptance criteria

- [ ] governance metadata model is documented
- [ ] transform-version to run-evidence linkage is defined
- [ ] compatibility/upgrade behavior is documented
- [ ] at least one example flow demonstrates governance metadata usage when implemented

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Transformation capability catalog`](../../architecture/transformation-capability-catalog.md)
- [`Machine-readable run summary`](C1-machine-readable-run-summary.md)
- [`Job history and operational observability`](../../architecture/job-history-and-operational-observability.md)

## Implementation notes

Keep the first slice lightweight and additive so governance value arrives before heavy platform dependencies.

## Status notes

Deferred until reusable profile contract and run-evidence link rules are stabilized.




