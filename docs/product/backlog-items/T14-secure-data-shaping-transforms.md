# T14 â€” Secure data-shaping transforms for sensitive fields

## Summary

Define secure transformation patterns (masking, tokenization, hashing/redaction) for sensitive fields so privacy and compliance handling is explicit, reusable, and auditable.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **Deferred**
- Milestone: **M3**
- Dependency: **T8, G1**
- Sequence rank: **#6** in deferred advanced transform sequence

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Sensitive-data transformation is currently not a dedicated product capability. Teams need consistent policy-driven patterns instead of ad hoc masking logic in each scenario.

## Goal

Provide a secure data-shaping contract for sensitive fields with explicit policy semantics and auditable run evidence.

## Scope

- define supported secure transform categories (mask/tokenize/hash/redact)
- define policy metadata and allowed usage boundaries
- define logging/evidence guardrails to avoid sensitive leakage
- define compatibility with existing mapping and reject handling

## Out of scope

- full enterprise key-management platform implementation
- broad secrets platform replacement
- general encryption-at-rest design

## Proposed approach

- introduce secure transforms as governed transform types
- make policy choice explicit in config
- ensure evidence model captures policy usage without exposing sensitive payloads

## Operator / runtime impact

- safer handling of sensitive fields in transformation pipelines
- consistent compliance posture across scenarios
- reduced risk of accidental sensitive data exposure in logs/reject outputs

## Concrete transformation examples

```yaml
# conceptual secure transform usage in processor mapping
mappings:
  - source: CustomerRaw
    target: CustomerSafe
    fields:
      - from: ssn
        to: ssnMasked
        transforms:
          - type: secureMask
            policy: pii-ssn-mask-v1
      - from: accountNumber
        to: accountToken
        transforms:
          - type: secureTokenize
            policy: pci-account-token-v1
```

Conceptual secure-transform event payload shape:

```json
{
  "event": "SECURE_TRANSFORM_APPLIED",
  "scenario": "customer-load",
  "stepName": "secure-shaping",
  "field": "ssn",
  "policy": "pii-ssn-mask-v1",
  "status": "APPLIED"
}
```

Expected behavior:

- secure transform policies are required and validated at startup
- runtime evidence shows policy usage and outcome but never raw sensitive values
- reject outputs and logs remain policy-safe by default

## Developer expectations

- enforce explicit policy references for secure transform types (no implicit defaults)
- align with `G1` secure configuration/secret injection boundary before activation
- include negative tests proving sensitive payloads are not logged in failure paths
- provide at least one preserved scenario showing mask and tokenize behavior together

## Acceptance criteria

- [ ] secure transform contract and policy boundaries are documented
- [ ] runtime/logging guardrails for sensitive data are documented
- [ ] at least one scenario/test demonstrates policy-compliant secure shaping when implemented
- [ ] compatibility with reject and observability model is defined

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`Transformation capability catalog`](../../architecture/etl-core/transformation-capability-catalog.md)
- [`Secret injection and secure configuration`](G1-secret-injection-via-environment-or-secure-config-source.md)
- [`Security test strategy`](../../architecture/foundations/security-test-strategy.md)

## Implementation notes

Treat secure data-shaping as a governed transform family, not an ad hoc utility call pattern.

## Status notes

Deferred until reusable profile and secure-configuration prerequisites are stronger.





