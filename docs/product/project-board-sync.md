# Product Backlog → GitHub Project Sync

## Purpose

This repository now supports a one-way sync from the `## Current Execution Board` table in [`product-backlog.md`](product-backlog.md) into the GitHub Project **OneFlow Executive Dashboard**.

The intent is to keep the Markdown execution board as the canonical planning surface while letting the GitHub Project act as the live operational view.

## Source-of-truth rule

- **Canonical source** — [`product-backlog.md`](product-backlog.md) `## Current Execution Board`
- **Projected live view** — GitHub Project **OneFlow Executive Dashboard**
- **Sync direction** — Markdown table → GitHub Project

When the sync is enabled, update the execution-board table first and avoid manually editing the mirrored Project fields unless you are doing emergency cleanup.

## What gets synced

Each execution-board row becomes one GitHub Project draft item keyed by the row `ID`.

Current field mapping:

| Markdown column | Project field / value |
|---|---|
| `ID` + `Item` | draft-item title in the form `A4 — Standardize generated-model naming and package derivation` |
| `Status` | Project field `Status` |
| `Priority` | Project field `Priority` |
| `Epic` | Project field `Epic` |
| `Milestone` | Project field `Milestone` |
| `Dependency` | Project field `Dependency` |
| `Notes` | draft-item body when public-mode sanitization is **not** enabled |

The sync uses an internal marker comment in each draft item body so reruns update the same item instead of creating duplicates.

## Supported project-field shapes

The sync currently supports:

- `Status` as a **single-select** or **text** field
- `Priority`, `Epic`, `Milestone`, and `Dependency` as **single-select** or **text** fields

For single-select fields, the Project options must already contain the values used in the Markdown table.

Recommended `Status` options:

- `Ready`
- `In Progress`
- `Blocked`
- `Done`
- `Deferred`

Recommended `Priority` options:

- `P0`
- `P1`
- `P2`

## Workflow and script

- Sync script: [`../../scripts/sync_project_board.py`](../../scripts/sync_project_board.py)
- Validation tests: [`../../scripts/tests/test_sync_project_board.py`](../../scripts/tests/test_sync_project_board.py)
- GitHub Actions workflow: [`../../.github/workflows/product-backlog-project-sync.yml`](../../.github/workflows/product-backlog-project-sync.yml)

The workflow runs when the backlog or sync files change on `master`, and it also supports manual `workflow_dispatch` runs with an optional dry-run mode.

## Required GitHub configuration

Configure these repository-level settings before expecting the live sync to mutate the Project:

### Secret

- `ONEFLOW_PROJECT_SYNC_TOKEN`

Use a Personal Access Token that can update Project V2 items. For private repositories or private projects, the token should also have the minimum repo access required to read the backlog source and mutate the project.

### Variables

- `ONEFLOW_PROJECT_NUMBER` — the numeric project number from the Project URL, for example `3`
- `ONEFLOW_PROJECT_OWNER` — optional; defaults to the repository owner when omitted. If the Project URL is user-owned (`/users/<login>/projects/<number>`), set this to that user login. If the Project URL is organization-owned (`/orgs/<org>/projects/<number>`), set it to that organization login.
- `ONEFLOW_PROJECT_PUBLIC_MODE` — optional; set to `true` to omit internal notes and relative drill-down links from the synced draft-item body

## Public-mode guidance

If the GitHub Project is public while the repository or backlog details remain private/internal, set:

```text
ONEFLOW_PROJECT_PUBLIC_MODE=true
```

That causes the sync to publish only a sanitized draft-item body and omit internal notes plus private relative links.

## Local validation

You can validate the parser locally without calling GitHub:

```powershell
Set-Location 'C:\spring-etl-engine'
python .\scripts\sync_project_board.py --backlog-file docs/product/product-backlog.md --dry-run
python -m unittest discover -s scripts/tests -p "test_*.py"
```

If you want to exercise a real sync locally, provide a token in `GITHUB_TOKEN` plus the project owner/number:

```powershell
Set-Location 'C:\spring-etl-engine'
$env:GITHUB_TOKEN = '<token>'
python .\scripts\sync_project_board.py --backlog-file docs/product/product-backlog.md --project-owner 'kbalanandam' --project-number 3 --dry-run
```

Remove `--dry-run` only after the Project fields and token are confirmed.

## Current limitations

- The sync is intentionally **one-way**. GitHub Project edits do not write back into `product-backlog.md`.
- Items removed from the Markdown table are reported as stale, but they are not auto-deleted from the Project in this first slice.
- The sync currently expects the execution-board table shape to remain stable.

## Recommended operating pattern

1. Update `docs/product/product-backlog.md` `## Current Execution Board`
2. Commit the backlog change
3. Let the workflow sync the GitHub Project projection
4. Use the Project for filtering, board views, and stakeholder visibility

That keeps the backlog narrative and the execution board aligned without maintaining the same state manually in two places.
