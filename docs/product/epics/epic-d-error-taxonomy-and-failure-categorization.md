# Epic D - Error taxonomy and failure categorization

## Summary

Epic D covers the future operator-facing failure vocabulary for the ETL runtime. Its goal is to make launch failures, validation failures, runtime IO failures, and business-rule failures easier to classify consistently across logs, reports, and future control-plane views.

## Scope

This epic is the home for work that:

- defines stable error categories and taxonomy rules
- keeps runtime and reporting surfaces aligned on failure meaning
- improves downstream operator and dashboard interpretation of failures

This epic is **not** the place for broader retry policy, scheduling policy, or transport scope by itself.

## Related backlog items

- [`D1 - Add stable error taxonomy / error categories`](../backlog-items/etl-core/D1-stable-error-taxonomy-and-categories.md)
- Cross-epic dependency: [`A7 - Add custom-step pairing, context handoff, and failure-contract baseline`](../backlog-items/etl-core/A7-custom-step-pairing-context-handoff-and-failure-contract.md) (shared failure category vocabulary)
- Cross-epic dependency: [`T16 - Define customer-owned processor transform extension seam`](../backlog-items/etl-core/T16-customer-owned-processor-transform-extension-seam.md) (shared transform failure category vocabulary)

## Related docs

- [`../../architecture/control-plane/job-history-and-operational-observability.md`](../../architecture/control-plane/job-history-and-operational-observability.md)
- [`../../architecture/foundations/architectural-risks-and-watchpoints.md`](../../architecture/foundations/architectural-risks-and-watchpoints.md)
- [`../../architecture/etl-core/customer-owned-processor-transform-seam.md`](../../architecture/etl-core/customer-owned-processor-transform-seam.md)

## Maintenance note

Use [`../product-backlog.md`](../product-backlog.md) for the live status of `D1`. Use this page only for the broader intent and boundary of the Epic D capability track.

