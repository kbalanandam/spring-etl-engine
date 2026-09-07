# Release planning and delivery control

## Purpose

Set up release planning ahead of implementation so teams can deliver visible value in parallel without losing control of scope, version mapping, or release quality.

This plan is a control layer over the canonical execution board in [`product-backlog.md`](product-backlog.md).

## Source of truth and ownership

- Backlog status, priority, milestone, and dependencies: [`product-backlog.md`](product-backlog.md)
- Live projected board: [OneFlow Executive Dashboard](https://github.com/users/kbalanandam/projects/3/views/1)
- Sync contract: [`project-board-sync.md`](project-board-sync.md)
- Release planning and version mapping policy: this file

## Release model

Use a train model with one visible product chunk per minor release.

- `patch` (`1.10.x`): security, compatibility, and low-risk hardening
- `minor` (`1.11.0`, `1.12.0`): user-visible capability chunks
- `rc` (`-rcN`): optional cut for high-risk cross-cutting changes

## Proposed release lanes

### Lane A - current patch line (`1.10.x`)

Goal: keep the branch safe and releasable while larger chunks are built.

- dependency/CVE remediation
- targeted bug fixes
- docs and verification workflow hardening

### Lane B - next visible chunk (`1.11.0`)

Goal: ship the first **product-grade optional control-plane persistence lane** as one visible operational value jump.

Planned scope:

- `R2` acceptance closure (profile/property matrix, vendor-token validation, fallback proof, runbook updates)
- `R3` portable control-plane history persistence without breaking current read-model/API contracts
- `R4` migration/versioning baseline that stays separate from bootstrap repair behavior
- `R5` release-facing parity evidence for MySQL/MSSQL/PostgreSQL plus control-plane-disabled fallback proof
- `S3` follow-on scheduler governance hardening once the persistence lane is stable enough to support run-state-aware overlap/audit behavior
- `F1` implementation-oriented recovery/read-model hardening that keeps advisory recovery shipped while deferring unsupported resume execution

Out of scope for `1.11.0`:

- broad transport expansion beyond already-approved scope
- advanced transform/parser growth that is not required for the product-grade control-plane lane
- any change that makes control-plane persistence mandatory for direct ETL execution

### Lane C - follow-on visible chunk (`1.12.0`)

Goal: expand the product-grade baseline into stronger governance, release control, and enterprise deployment readiness.

Candidate scope:

- `G1` secure secret-injection slice
- `V4` verification provenance/retention/release gating
- `V3` HTML verification reporting from the shared evidence model
- follow-on `S3` governance depth and/or `X1` transport contract work once the persistence lane exits cleanly

## PR stacking strategy for parallel delivery

Use stackable PRs that merge independently and preserve visible progress:

1. `docs/contract` PR
   - freeze runtime/control-plane boundaries
   - update backlog and architecture docs
2. `backend` PR
   - model + persistence + API/contracts
3. `runtime` PR
   - scheduler execution path and evidence
4. `operator-ui` PR
   - visible controls and diagnostics
5. `hardening` PR
   - tests, smoke verification, and release notes

Each PR must include:

- clear value statement
- explicit out-of-scope list
- evidence/tests for only that slice

## Version mapping policy

Map backlog milestones to release targets before coding starts.

| Milestone | Default release target | Notes |
|---|---|---|
| `M2` | shipped across `1.8.0` -> `1.9.1` | closed MVP/control-plane foundation lane |
| `M3` | `1.11.0` | product-grade persistence, governance, and release-control lane |

If an `M2` item is intentionally deferred, keep milestone status in the board but record version exception notes in this file.

## Entry and exit gates

### Entry gate (before starting a chunk)

- scope is bounded and written
- acceptance criteria are testable
- release target is named (`1.11.0` or `1.12.0`)
- dependencies are explicitly resolved or declared blocked

### Exit gate (before release cut)

- merged PRs map to planned scope only
- changelog draft for the release is complete
- verification workflow is green with known exceptions documented
- docs reflect shipped behavior (no design-only drift)

## Changelog workflow

Use `CHANGELOG.md` `Unreleased` as a staging area for merged PR notes.

- add entries when PR merges, not when PR opens
- group by `Added`, `Changed`, `Fixed`, `Security`
- at release cut, move grouped entries into the version section and clear `Unreleased`

## Lightweight documentation guardrail

Keep this page as control-plane planning only, not a design dump.

- keep one-screen summaries for each release lane
- keep implementation detail in PRs and code-level docs
- link to architecture/backlog pages instead of duplicating long detail
- prefer updates to existing sections over adding many new sections

## Operating template (copy per release)

Use this compact template for each new target version:

```markdown
### Release `<version>`

- Goal: <visible value>
- In scope: <3-5 bullets>
- Out of scope: <2-4 bullets>
- Planned PR stack: <docs -> backend -> runtime -> ui -> hardening>
- Risks/blockers: <short list>
- Gate status: Entry [ ] Exit [ ]
```

## Operating rhythm

- weekly: checkpoint scope, blockers, and gate status
- per merged PR: update `CHANGELOG.md` `Unreleased`
- at release cut: move notes from `Unreleased` into the version section

## Immediate next step

`1.11.0` lane now has `R2` -> `R5` closed for the product-grade optional control-plane persistence slice; the active follow-on scope is `S3`, `F1`, `G1`, and `V4` without reopening MVP scheduler/UI scope or making control-plane persistence mandatory for direct ETL runs.

Use [`product-backlog.md`](product-backlog.md) plus the Epic/detail pages for `S3`, `F1`, `G1`, and `V4` as the active execution trackers for the next `1.11.0` follow-on slices.


