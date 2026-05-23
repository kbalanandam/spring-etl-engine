# T15 - XML-native duplicate identity for nested XML sources

## Summary

Add a dedicated follow-on item for XML-native duplicate identity so nested XML scenarios can resolve duplicates with source-structure-aware keys when flat mapped fields are not sufficient.

## Current board status

- Epic: **[Epic T](../epics/epic-t-transformation-capability.md)**
- Priority: **P2**
- Status: **In Progress**
- Milestone: **M3**
- Dependency: **T4, P3**

> Keep these fields synchronized with the row in [`product-backlog.md`](../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

The shipped duplicate rule works on mapped runtime fields and is reliable for flat or clearly keyed records, but some nested XML sources require identity keys that preserve XML path, scope, or attribute context. Without that context, duplicate elimination can produce incorrect winners or false matches.

### Quick example to preserve

Nested XML source excerpt:

```xml
<Events>
  <Event id="E1">
	<Customer><Id>100</Id></Customer>
	<Tags>
	  <Tag code="A">VIP</Tag>
	  <Tag code="B">VIP</Tag>
	</Tags>
	<UpdatedAt>2026-05-21T10:00:00</UpdatedAt>
  </Event>
  <Event id="E2">
	<Customer><Id>100</Id></Customer>
	<Tags><Tag code="A">VIP</Tag></Tags>
	<UpdatedAt>2026-05-21T11:00:00</UpdatedAt>
  </Event>
</Events>
```

Why this matters:

- flat mapped duplicate keys such as `customerId + tagValue` can collapse different nested nodes into one logical key
- XML-native identity can include path/scope/attribute context (for example `Tag/@code`) so duplicate winner selection stays correct for repeated nested structures
- reject output flow stays on the existing processor path; this item only improves how duplicate identity keys are formed for nested XML

## Goal

Define an additive XML-native identity mode for duplicate elimination that improves correctness on nested XML while preserving the current processor-centered runtime contract and reject pipeline.

## Scope

This item covers:

- XML-source-focused duplicate identity extraction for nested or repeated-node scenarios
- additive configuration surface that keeps current flat-key behavior backward compatible
- explicit operator evidence for duplicate identity mode and identity-source rationale
- preserved nested XML scenario proof and focused regression tests

## Out of scope

This item does not cover:

- replacing the existing processor `duplicate` rule for all sources
- reopening deprecated validation paths under `src/main/java/com/etl/validation/`
- large duplicate-state scalability redesign (tracked under [`T7`](T7-duplicate-tracking-scalability-redesign-deferment.md))
- target-aware deduplication or restart semantics redesign

## Proposed approach

The preferred direction is:

1. keep current `duplicate` behavior as the default contract
2. add XML-native identity as an opt-in mode for nested XML scenarios first
3. emit clear runtime evidence for selected identity mode and key-construction inputs
4. keep duplicate decisioning and reject emission on the active processor-rule path
5. document UI and runtime guardrails so inefficient or unsafe choices are warned or blocked
6. complete a final redesign cutover slice that removes legacy processor compatibility once all preserved bundles and docs are migrated

UI guardrail note for collaborators:

- default to the current flat-key mode for simple XML
- recommend XML-native identity for nested/repeating-node XML inputs
- warn (or block in stricter environments) when a likely-unsafe flat-key choice is selected for nested XML

## Operator / runtime impact

Expected impact when this item ships:

- duplicate outcomes become more accurate for nested XML sources with repeating structures
- existing non-XML and flat XML scenarios remain unchanged unless opt-in is configured
- operators can see identity mode evidence in startup/step logs
- config docs gain explicit guidance for when to use flat mapped keys vs XML-native identity

## Trade-off Snapshot

- Decision: add `duplicateIdentityMode: xmlNative` as an opt-in mode for XML duplicate rules.
- Benefit: improves duplicate correctness for nested/repeating XML identities where flat fields can false-merge records.
- Cost: additional key-resolution work for path-like fields and higher config-authoring care.
- Risk: using too few `keyFields` still collapses distinct records, even in `xmlNative` mode.
- Use when: duplicate identity requires XML path/attribute context (for example `/.../@code`).
- Avoid when: flat mapped fields already encode stable uniqueness for the scenario.
- Default: keep `flatMapped` for backward compatibility and simpler runtime behavior.
- Evidence: resolver parity tests and preserved `config-jobs/xml-nested-to-csv-tag-validation` proof pair.

- Decision: support ordered winner selection via `orderBy` on duplicate rules.
- Benefit: deterministic winner choice when business semantics require a retained best/latest record.
- Cost: increased state/memory pressure compared with keep-first duplicate elimination.
- Risk: overusing `orderBy` can increase runtime overhead without business benefit.
- Use when: outcome requires explicit winner semantics.
- Avoid when: duplicate policy is reject/keep-first and no winner ranking is required.
- Default: omit `orderBy`.
- Evidence: startup and step resolver-selection logs plus resolver summary counters.

## Acceptance criteria

- [x] XML-native duplicate identity is available as an additive option for nested XML source scenarios
- [x] backward compatibility is preserved for existing duplicate configurations that use flat mapped fields
- [x] startup/runtime evidence clearly reports chosen identity mode and reason
- [x] UI guardrails expose safe defaults and warnings for likely-unsafe flat-key choices on nested XML sources
- [x] at least one preserved nested XML scenario demonstrates a case where XML-native keys prevent false duplicate matches
- [x] docs under `docs/config/` and `docs/architecture/` explain boundaries, examples, and non-goals

## Execution slices (S1-S6)

Use this board to sequence implementation with strict compatibility in early slices and one explicit final cutover.

### S1 - Orchestration extraction (parity)

- Target: extract processor orchestration into a pipeline seam without changing behavior.
- Scope: move flow control out of `DefaultDynamicProcessor` into explicit pipeline stages while keeping current transforms/rules wiring.
- Acceptance criteria:
  - [x] existing processor behavior remains unchanged on preserved bundles
  - [x] parity-focused tests pass without requiring config migration
  - [x] startup/step evidence remains stable
- Backward compatibility: **Required**

### S2 - Rule dispatch registry (parity)

- Target: route rule execution by rule type + source format using factory/registry dispatch.
- Scope: introduce common rule handlers plus format-aware resolution, with fallback to shared handlers.
- Acceptance criteria:
  - [x] dispatch path is deterministic and fail-fast on ambiguous registration
  - [x] current rule outcomes match parity expectations across CSV/XML/relational preserved scenarios
  - [x] no contract break in current `processor-config.yaml`
- Backward compatibility: **Required**

### S3 - Duplicate rule format split

- Target: separate duplicate semantics into common + format-specific handlers (`xml`, `csv`, `sql/relational`).
- Scope: keep winner-selection and resolver contracts intact while isolating XML-native identity logic behind format-aware handlers.
- Acceptance criteria:
  - [x] duplicate tests pass for flat and nested XML, CSV, and relational paths
  - [x] ordered winner-selection behavior remains deterministic
  - [x] identity mode evidence is visible in runtime logs
- Backward compatibility: **Required**

### S4 - Remaining rule families + scoped config (additive)

- Target: extend format-aware dispatch to other rule families and add optional scope metadata.
- Scope: keep existing rule syntax valid; add additive scoping/guardrail fields only.
- Acceptance criteria:
    - [x] legacy rule config remains valid
    - [x] optional scope fields are validated fail-fast when invalid
    - [x] docs include usage guidance for common vs format-specific rules
- Backward compatibility: **Required**

### S5 - Preserved bundle and doc migration

- Target: migrate preserved bundles and docs to the new processor design as the primary contract.
- Scope: update runnable examples under `config-jobs/`, plus `docs/config/` and `docs/architecture/` references.
- Acceptance criteria:
  - [x] at least one preserved nested XML scenario proves XML-native identity outcomes
  - [x] migrated examples are runnable and documented
  - [x] verification workflow remains green with migrated bundles
- Backward compatibility: **Required**

### S6 - Final redesign cutover (non-compatible)

- Target: remove legacy processor compatibility and accept only the redesigned contract.
- Scope: delete deprecated processor wiring paths, enforce new config contract at startup, and publish migration notes.
- Acceptance criteria:
  - [ ] legacy processor contract is rejected with clear fail-fast startup errors
  - [ ] only redesigned pipeline + format-scoped rule dispatch remains active
  - [ ] release notes and migration guidance are published
- Backward compatibility: **Not required** (intentional cutover)

#### S6 migration checklist

Pre-cutover readiness:

- [ ] all preserved bundles under `src/main/resources/config-jobs/` are migrated to the redesigned processor contract
- [ ] `docs/config/processor/default-processor.md` reflects only the redesigned contract (legacy syntax removed)
- [ ] architecture docs describing processor flow and rule dispatch are updated and merged
- [ ] verification workflow is green on migrated bundles (`scripts/generate-verification-report.ps1`)
- [ ] migration notes include old-to-new config mapping examples and explicit unsupported legacy fields

Cutover-day checks:

- [ ] startup validation fails fast when legacy processor fields are present in selected `processor-config.yaml`
- [ ] legacy processor wiring classes/branches are removed from active runtime dispatch
- [ ] logs still emit expected run/step evidence fields after cutover
- [ ] one migrated nested XML scenario proves XML-native duplicate identity behavior end-to-end
- [ ] release notes clearly mark this as an intentional non-compatible processor-contract cutover

## Related docs

- [`Product backlog`](../product-backlog.md)
- [`T4 - Transformation quarantine and duplicate hardening`](T4-transformation-quarantine-and-duplicate-hardening.md)
- [`T7 - Duplicate tracking scalability redesign deferment`](T7-duplicate-tracking-scalability-redesign-deferment.md)
- [`P3 - XML parser maturity`](P3-expand-xml-parser-maturity-for-namespace-and-fragment-contracts.md)
- [`Default processor reference`](../../config/processor/default-processor.md)
- [`File ingestion hardening`](../../architecture/file-ingestion-hardening.md)

## Implementation notes

Treat this as a correctness-focused follow-on after T4 closure: source-aware identity extraction can be introduced without changing transform ordering or moving duplicate handling into source validation by default.

## Status notes

Created as a deferred follow-on when T4 moved to Done. Activate when nested XML duplicate cases require source-structure-aware identity beyond flat mapped fields.

Current implementation progress in this branch:

- Completed additive `xmlNative` identity support with ordered-resolver parity and runtime evidence.
- Added runnable preserved proof pair under `config-jobs/xml-nested-to-csv-tag-validation` (`flatMapped` false-merge vs `xmlNative` expected separation).
- Added fail-fast guardrails for unsupported repeating/list selector traversal and narrowed flatMapped XML selector detection to avoid over-rejecting literal keys containing `@`.
- Completed S4 additive guardrail UX slice with startup advisory evidence (`PROCESSOR_GUARDRAIL event=xml_duplicate_flatmapped_advisory`) for nested XML mappings that still use simple flat duplicate keys.
- Closed S1/S2 parity slices using the shipped processor pipeline seam and deterministic rule-dispatch registry path, with focused regression evidence across pipeline/dispatch and duplicate XML/CSV/relational rule outcomes.
- Started `S6-A` cutover enforcement by rejecting non-`default` processor types on the active selected-job path and documenting the shared default processor as the only supported runtime contract.
- Latest focused verification remains green (`scripts/generate-verification-report.ps1`, status `READY`).

Focused S1/S2 parity evidence (latest run):

- `mvn --no-transfer-progress "-Dtest=DefaultProcessorExecutionPipelineTest,ValidationRuleEvaluatorTest,ProcessorExtensionDefaultsTest,DuplicateProcessorValidationRuleTest,XmlDuplicateProcessorValidationRuleTest,InMemoryDuplicateResolverTest,EmbeddedDbDuplicateResolverTest" test`
- Result: `BUILD SUCCESS`, `Tests run: 59, Failures: 0, Errors: 0, Skipped: 0`.

## Preserved proof anchors (implemented so far)

The following focused proof tests now preserve the false-merge comparison pattern (`flatMapped` vs `xmlNative`) for ordered duplicate winner selection:

- `src/test/java/com/etl/runtime/InMemoryDuplicateResolverTest.java` (`xmlNativePreventsFalseDuplicateMergeComparedToFlatMapped`)
- `src/test/java/com/etl/runtime/EmbeddedDbDuplicateResolverTest.java` (`xmlNativePreventsFalseDuplicateMergeComparedToFlatMapped`)

These tests keep one minimal nested-map scenario where flat mapped keys collapse distinct nested identities, while XML-native path keys preserve distinct winners.



