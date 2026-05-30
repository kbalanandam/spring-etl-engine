# ADR-0005: Use a shared verification evidence model for Markdown and HTML reports

- Status: Accepted
- Date: 2026-04-25

## Context

`spring-etl-engine` now has a meaningful local verification workflow built around:

- `mvn test`
- scenario-level smoke verification
- machine-readable runtime evidence such as `RUN_SUMMARY`, `STEP_EVENT`, and related lifecycle logs
- generated Markdown verification reports under `target/`

That workflow is already useful for local development, but it is not yet sufficient for enterprise-grade verification and release evidence.

The product now needs reporting that can clearly separate and present categories such as:

- change-focused verification
- regression suite evidence
- runtime / smoke verification
- configuration and environment validity
- release-readiness interpretation

The team also needs to decide how to balance two competing needs:

1. keep verification output easy to review and diff inside the repository
2. provide a richer report format with drill-down, navigation, and audience-friendly presentation for enterprise use

A direct choice of Markdown-only or HTML-only would force an unnecessary tradeoff.

Markdown is useful for repository history, pull-request review, and lightweight local evidence.

HTML is better for drill-down, enterprise presentation, and future operator/release-consumer usability.

The product therefore needs a reporting direction that keeps both needs aligned without duplicating verification logic in separate renderers.

## Decision

The product will treat verification reporting as a structured evidence capability rather than as one format-specific script output.

A shared verification evidence model will become the canonical reporting source.

That evidence model should eventually preserve at least:

- repository and branch identity
- commit or config identity where available
- changed-file context
- verification category labels
- Maven suite and testcase results
- smoke/runtime verification results
- generated log and artifact references
- release-readiness status and interpretation

From that same evidence model, the product will generate at least two report views:

1. a Markdown report as the repository-friendly, review-friendly artifact
2. an HTML report as the enterprise-friendly presentation artifact for richer navigation and drill-down

Markdown remains important because it is easy to diff, store, and review in pull requests.

HTML becomes the preferred enterprise presentation layer because it supports richer navigation, collapsible sections, clearer audience-specific presentation, and future drill-down behavior more naturally than plain Markdown.

The reporting direction should be delivered in phases:

- first define the evidence model and verification categories
- then align the Markdown report to that shared model
- then add HTML rendering from the same source evidence

## Consequences

### Positive
- avoids duplicating verification logic across separate Markdown and HTML implementations
- preserves Markdown for repo-native review while enabling richer HTML reporting for enterprise use
- makes verification reporting a product capability rather than an ad hoc local script artifact
- supports explicit categories such as change-focused testing and regression evidence
- creates a stronger base for future release gating, retention, provenance, and auditability
- aligns verification reporting with the existing observability direction that values structured evidence over raw text alone

### Negative
- introduces an additional design layer because evidence capture must be modeled explicitly before richer rendering is added
- requires the team to maintain consistency between renderers as the report evolves
- increases scope beyond simple script formatting and therefore should be tracked as roadmap work, not casual utility work
- may delay a polished HTML output until the evidence model and category boundaries are stable

## Alternatives considered

### 1. Keep Markdown only
Rejected as the long-term enterprise answer because plain Markdown is useful but limited for drill-down, structured navigation, and presentation to broader enterprise stakeholders.

### 2. Move directly to HTML only
Rejected because repository review, Git diffs, and lightweight pull-request evidence still benefit from a plain-text Markdown artifact.

### 3. Maintain separate Markdown and HTML logic without a shared evidence model
Rejected because it would duplicate business logic, increase drift risk, and make category/report evolution harder to govern.

## Notes

This ADR establishes direction, not full implementation detail.

Current phase-1 implementation status:

- a shared in-memory verification evidence model now exists inside `scripts/generate-verification-report.ps1`
- the current renderer produces categorized Markdown evidence for change-focused verification, regression-suite verification, runtime/smoke verification, and release readiness
- HTML rendering is intentionally still pending so the evidence contract can stabilize first

Follow-up work should be tracked in the product backlog as dedicated verification-reporting items covering:

- evidence model definition
- verification category design
- Markdown rendering aligned to the shared model
- HTML rendering aligned to the shared model
- provenance, retention, and release-gating rules

Future implementation may also introduce a machine-readable intermediate artifact such as JSON if that improves renderer reuse, auditability, or CI/release integration.


