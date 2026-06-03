# Operator UI Smoke Tests

Minimal Node-based smoke tests for modularized operator UI list components:

- `jobs-list-ui.js`
- `runs-list-ui.js`

These tests use a tiny in-repo DOM shim (`dom-shim.js`) and do not require external npm dependencies.

## Run

From the repository root:

```powershell
npm run test:operator-ui-smoke
```

Or directly with Node:

```powershell
node --test scripts/tests/operator-ui-smoke/*.test.js
```

## Scope

- Control wiring (`initializeControls`)
- Route-state application (`applyRouteState`)
- Table rendering behavior (`renderTable`)
- Click navigation placeholders (`location.hash` updates)

## Optional CI workflow

An optional workflow at `.github/workflows/operator-ui-smoke.yml` runs these smoke tests in GitHub Actions.

- It is intentionally non-blocking (`continue-on-error: true`) so required Maven checks remain the baseline gate.
- It runs on manual trigger (`workflow_dispatch`) and on pull requests that touch operator UI files or this smoke-test bundle.


