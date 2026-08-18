# Release 1.11.0 R2 kickoff checklist

## Purpose

Track the first bounded implementation slice for `1.11.0` so release gating and scope discipline remain visible across docs, backend, runtime, UI, and hardening PRs.

## Scope guardrails

- In scope: `R2` acceptance closure only (profile/property matrix, vendor-token validation, fallback proof, runbook updates)
- Out of scope: `R3`-`R5` implementation details, transport expansion, and any change that makes control-plane persistence mandatory for direct ETL runs

## Entry gate checklist

- [x] Scope and out-of-scope notes are explicit in PR description
- [x] Acceptance criteria are testable and linked to evidence
- [x] Release target is confirmed as `1.11.0`
- [x] Dependencies/blockers are listed with owner and due date (`R2` owner: control-plane persistence lane; blocker: remaining explicit vendor/mode matrix proofs before `R3` status gate)

## PR stack kickoff

### 1) docs/contract
- [x] Update affected `docs/config/*` contracts if R2 inputs change (no property-key contract change in this slice; existing contract references revalidated)
- [ ] Update control-plane architecture notes if behavior/guardrails change
- [x] Link R2 acceptance evidence targets from this checklist

### 2) backend
- [x] Validate profile/property matrix behavior (including fail-fast paths)
- [x] Validate vendor token/placeholder detection behavior
- [x] Preserve current read-model/API contracts

### 3) runtime
- [x] Prove control-plane-disabled fallback behavior remains intact
- [x] Verify startup/runtime guardrail evidence is emitted as expected

### 4) operator-ui
- [ ] Confirm existing control-plane views remain contract-compatible
- [x] No new feature scope beyond R2 compatibility/diagnostics

### 5) hardening
- [x] Add/adjust tests only for R2 acceptance boundaries
- [x] Run verification workflow and capture evidence references
- [x] Add merged-note entries to `CHANGELOG.md` `Unreleased`

## Evidence links

- Targeted R2 tests (`110` tests): `target/tmp-r2-targeted-tests.log`
  - `target/surefire-reports/TEST-com.etl.config.ConfigLoaderJobConfigTest.xml`
  - `target/surefire-reports/TEST-com.etl.config.relational.RelationalConnectionConfigTest.xml`
  - `target/surefire-reports/TEST-com.etl.controlplane.monitoring.RunSummaryReadModelServiceTest.xml`
  - `target/surefire-reports/TEST-com.etl.controlplane.triggers.TriggerEventPersistenceModeGuardTest.xml`
- Profile/bootstrap contract tests (`5` tests): `target/tmp-r2-profile-tests.log`
  - `target/surefire-reports/TEST-com.etl.config.ApplicationDevProfileDatasourceTest.xml`
  - `target/surefire-reports/TEST-com.etl.controlplane.api.SystemControllerTest.xml`
  - `target/surefire-reports/TEST-com.etl.controlplane.monitoring.ControlPlaneBootstrapScriptsTest.xml`
- Smoke/fallback verification: `target/tmp-r2-verify-recent.log`
  - `target/verify-customer-load.log`
  - `target/verify-csv-to-sqlserver.log`
  - `target/verify-trigger-now.log`
- Persistence contract guard matrix/fail-fast tests (`11` tests): `target/tmp-r2-guard-tests.log`
  - `target/surefire-reports/TEST-com.etl.controlplane.ControlPlanePersistenceContractGuardTest.xml`
- Trigger registry unavailable fallback tests (`27` tests in slice): `target/tmp-r2-next-task-tests.log`
  - Includes `JobBundleControllerTriggerNowUnitTest` and `JobBundleControllerTest` fallback coverage

## Exit evidence

- [ ] All R2 acceptance items are marked complete with evidence links
- [x] Verification workflow is green or exceptions are documented
- [x] Docs reflect shipped behavior and guardrails
- [x] Release blockers are closed or explicitly deferred



