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

- `patch` (`1.7.x`): security, compatibility, and low-risk hardening
- `minor` (`1.8.0`, `1.9.0`): user-visible capability chunks
- `rc` (`-rcN`): optional cut for high-risk cross-cutting changes

## Proposed release lanes

### Lane A - current patch line (`1.7.x`)

Goal: keep the branch safe and releasable while larger chunks are built.

- dependency/CVE remediation
- targeted bug fixes
- docs and verification workflow hardening

### Lane B - next visible chunk (`1.8.0`)

Goal: ship **Scheduled Runs MVP** as one visible operational value jump.

Planned scope:

- `S1` contract freeze (single selected-job launch boundary, trigger-origin evidence, retry/restart separation)
- first `S2` slice (time-based schedule definitions + pause/resume baseline)
- operator UI schedule visibility and controls aligned to the same launch contract
- run evidence fields to explain launch origin and schedule identity

Out of scope for `1.8.0`:

- advanced overlap/missed-run policies (`S3`)
- full retained operational data model closure (`S4`)
- broad transport expansion beyond already-approved scope

### Lane C - follow-on visible chunk (`1.9.0`)

Goal: expand into controlled transport and scheduler reliability follow-ons.

Candidate scope:

- `X1` transport contract freeze and first implementation sequencing into `X2`
- `S3` overlap/missed-run policy baseline
- scheduler evidence hardening and retained diagnostics improvements

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
| `M2` | `1.8.0` | primary near-term visible chunk lane |
| `M3` | `1.9.0` | follow-on reliability/transport scale-up |

If an `M2` item is intentionally deferred, keep milestone status in the board but record version exception notes in this file.

## Entry and exit gates

### Entry gate (before starting a chunk)

- scope is bounded and written
- acceptance criteria are testable
- release target is named (`1.8.0` or `1.9.0`)
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

Start `1.8.0` lane with an `S1` contract-freeze PR, then execute the stacked implementation sequence.


