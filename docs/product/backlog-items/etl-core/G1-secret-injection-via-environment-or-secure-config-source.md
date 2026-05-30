# G1 - Support secret injection via environment or secure config source

## Summary

Define and implement a safer secret-consumption path so sensitive runtime settings do not need to live as plain committed values in job YAML.

## Current board status

- Epic: **[Epic G](../../epics/etl-core/epic-g-secret-injection-and-secure-configuration.md)**
- Priority: **P1**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **C1**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Enterprise usage eventually needs a safer way to inject credentials or sensitive settings than committed static config values.

## Goal

Support secret resolution from environment variables or approved secure config sources while keeping local examples simple and non-misleading.

## Scope

- environment-based secret injection support
- optional secure-config-source direction
- guidance that separates example placeholders from real secret handling

## Out of scope

- partner-facing transport security by itself
- full secrets UI/control-plane product
- custom secret management product integration for every provider in the first slice

## Proposed approach

Start with the smallest safe secret-injection contract that improves deployment readiness without making local preserved examples harder to understand.

## Operator / runtime impact

- deployments gain a safer configuration path
- committed examples can remain placeholder-based and simpler
- future enterprise deployment guidance becomes more credible

## Acceptance criteria

- [ ] one supported secret-injection path exists for runtime use
- [ ] preserved docs clearly separate placeholders/examples from real secret handling
- [ ] unsupported secret-resolution scenarios fail clearly instead of silently degrading

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`ETL product evolution roadmap`](../../../architecture/foundations/etl-product-evolution-roadmap.md)
- [`Control-plane worker boundary`](../../../architecture/control-plane/control-plane-worker-boundary.md)

## Implementation notes

Keep the first slice portable and environment-friendly before considering broader secret-provider integration.

## Status notes

Deferred until higher-priority runtime, transport, and reporting foundations are stable.

