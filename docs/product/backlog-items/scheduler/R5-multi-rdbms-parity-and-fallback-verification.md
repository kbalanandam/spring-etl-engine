# R5 - Multi-RDBMS parity and fallback verification

## Summary

Prove that control-plane persistence behavior is equivalent across supported RDBMS targets and that direct selected-job ETL execution still works when control-plane persistence is disabled.

## Current board status

- Epic: **[Epic R](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)**
- Priority: **P2**
- Status: **Ready**
- Milestone: **M3**
- Dependency: **R3, R4**

> Keep these fields synchronized with the row in [`product-backlog.md`](../../product-backlog.md). The execution board remains the canonical source for changing status values.

Use the linked `Epic` entry above to navigate to the shared epic-level product context for this backlog item.

## Problem

Portability claims are risky without repeatable, cross-engine parity evidence and fallback verification.

## Goal

Establish confidence through automated and documented parity checks across target engines and optional-persistence fallback scenarios.

## Scope

- define parity test matrix for supported engines
- validate run summary, recovery, step/artifact read-model parity
- validate fallback behavior when control-plane persistence is disabled
- publish verification evidence expectations for release readiness

## Out of scope

- broad performance benchmarking across all engines
- deep HA/cluster testing
- changing runtime contracts while testing portability

## Proposed approach

Build an incremental integration-test matrix (PR or scheduled lanes) and pair it with reproducible verification evidence artifacts.

### First-pass parity matrix (planning baseline)

| Engine | Lane target | Minimum checks | Evidence expectation |
|---|---|---|---|
| SQLite | PR lane | run summary + recovery + step/artifact read-model checks | automated test logs + verification summary |
| PostgreSQL | PR or near-PR lane | same parity checks as SQLite | automated test logs + verification summary |
| MySQL | PR or near-PR lane | same parity checks as SQLite | automated test logs + verification summary |
| SQL Server | scheduled or manual lane | parity checks + fallback check | signed validation note + verification summary |
| Oracle | scheduled or manual lane | parity checks + fallback check | signed validation note + verification summary |

### Fallback verification lane

For each release-candidate cycle, run one explicit lane with control-plane persistence disabled and verify direct selected-job ETL execution still succeeds with expected run evidence from the ETL runtime path.

## Operator / runtime impact

- clearer confidence for deployment DB selection
- predictable fallback expectations when control-plane persistence is off
- improved release-readiness evidence for enterprise rollout

## Trade-off Snapshot

- Decision: require parity evidence before defaulting to new persistence mode
- Benefit: lower regression risk and clearer rollout confidence
- Cost: longer CI and added environment setup complexity
- Risk: underpowered CI matrix can miss vendor-specific edge cases
- Use when: enabling portability claims in release messaging
- Avoid when: feature is still pre-parity prototype stage
- Default: prove behavior parity first, then widen rollout
- Evidence: CI matrix results and verification report entries per supported engine lane

## Acceptance criteria

- [ ] parity matrix includes at least SQLite, PostgreSQL, and MySQL in automated lanes
- [ ] documented plan exists for SQL Server and Oracle validation lanes
- [ ] control-plane-disabled fallback path is verified for direct selected-job ETL execution
- [ ] release-facing verification evidence references portability results explicitly

## Related docs

- [`Product backlog`](../../product-backlog.md)
- [`Epic R`](../../epics/scheduler/epic-r-multi-rdbms-control-plane-persistence-via-jpa-hibernate.md)
- [`verification-reporting`](../../../../scripts/generate-verification-report.ps1)
- [`project-board-sync.md`](../../project-board-sync.md)

## Implementation notes

Keep this item evidence-driven. If a vendor lane is not automated yet, record explicit manual validation procedure and ownership.

## Status notes

Pending R3/R4 completion.


