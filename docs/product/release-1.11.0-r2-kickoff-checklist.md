# Release 1.11.0 R2 kickoff checklist

## Purpose

Track the first bounded implementation slice for `1.11.0` so release gating and scope discipline remain visible across docs, backend, runtime, UI, and hardening PRs.

## Scope guardrails

- In scope: `R2` acceptance closure only (profile/property matrix, vendor-token validation, fallback proof, runbook updates)
- Out of scope: `R3`-`R5` implementation details, transport expansion, and any change that makes control-plane persistence mandatory for direct ETL runs

## Entry gate checklist

- [ ] Scope and out-of-scope notes are explicit in PR description
- [ ] Acceptance criteria are testable and linked to evidence
- [ ] Release target is confirmed as `1.11.0`
- [ ] Dependencies/blockers are listed with owner and due date

## PR stack kickoff

### 1) docs/contract
- [ ] Update affected `docs/config/*` contracts if R2 inputs change
- [ ] Update control-plane architecture notes if behavior/guardrails change
- [ ] Link R2 acceptance evidence targets from this checklist

### 2) backend
- [ ] Validate profile/property matrix behavior (including fail-fast paths)
- [ ] Validate vendor token/placeholder detection behavior
- [ ] Preserve current read-model/API contracts

### 3) runtime
- [ ] Prove control-plane-disabled fallback behavior remains intact
- [ ] Verify startup/runtime guardrail evidence is emitted as expected

### 4) operator-ui
- [ ] Confirm existing control-plane views remain contract-compatible
- [ ] No new feature scope beyond R2 compatibility/diagnostics

### 5) hardening
- [ ] Add/adjust tests only for R2 acceptance boundaries
- [ ] Run verification workflow and capture evidence references
- [ ] Add merged-note entries to `CHANGELOG.md` `Unreleased`

## Exit evidence

- [ ] All R2 acceptance items are marked complete with evidence links
- [ ] Verification workflow is green or exceptions are documented
- [ ] Docs reflect shipped behavior and guardrails
- [ ] Release blockers are closed or explicitly deferred

