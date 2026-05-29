# Product Backlog â†’ GitHub Project Sync

## Purpose

This repository now supports a one-way sync from the `## Current Execution Board` table in [`product-backlog.md`](product-backlog.md) into the GitHub Project **OneFlow Executive Dashboard**.

The intent is to keep the Markdown execution board as the canonical execution-planning surface while letting the GitHub Project act as the live projected execution view.

## Source-of-truth rule

- **Canonical source** - [`product-backlog.md`](product-backlog.md) `## Current Execution Board`
- **Projected live view** - GitHub Project **OneFlow Executive Dashboard**
- **Sync direction** - Markdown table â†’ GitHub Project

When the sync is enabled, update the execution-board table first and avoid manually editing the mirrored Project fields unless you are doing emergency cleanup.

## What gets synced

Each execution-board row becomes one GitHub Project draft item keyed by the row `ID`.

Current field mapping:

| Markdown column | Project field / value |
|---|---|
| `ID` + `Item` | draft-item title in the form `A4 - Standardize generated-model naming and package derivation` |
| `Status` | Project field `Status` |
| `Priority` | Project field `Priority` |
| `Epic` | Project field `Epic` |
| `Milestone` | Project field `Milestone` or fallback field `Execution Milestone` |
| `Dependency` | Project field `Dependency` |
| `Epic` page link | draft-item body as a raw repository URL when the epic label resolves to a known epic doc |
| `ID` detail-page link | draft-item body as a raw repository URL when sync has repository URL context |
| `Notes` | draft-item body when public-mode sanitization is **not** enabled |

The sync uses an internal marker comment in each draft item body so reruns update the same item instead of creating duplicates.

When a backlog `ID` cell links to a detail page such as `backlog-items/etl-core/A6-retire-internal-generated-model-package-bridge.md`, the sync now resolves that relative path against the backlog file and emits the final repository URL in the draft-item body. In GitHub Actions this is derived automatically from the workflow repository context; for local/manual sync runs you can also provide an explicit repository URL/ref.

When the execution-board `Epic` value matches one of the maintained epic labels (for example `Epic A`, `Epic P`, `Epic T`, or `Epic S`), the sync also renders a separate `Epic page` entry plus the final repository URL that points to the matching page under `docs/product/epics/`.

## Supported project-field shapes

The sync currently supports:

- `Status` as a **single-select** or **text** field
- `Priority`, `Epic`, `Milestone`, and `Dependency` as **single-select** or **text** fields

The `Epic` Project field still behaves as a normal Project field for grouping/filtering. The epic-doc navigation lives in the synced draft-item body under a dedicated `Epic page` line plus raw URL, not in the Project field widget itself.

### Important `Status` note

The preferred setup is still a custom `Status` field whose option names exactly match the backlog table:

- `Ready`
- `In Progress`
- `Blocked`
- `Done`
- `Deferred`

To reduce operator friction during first-time setup, the sync also tolerates a small alias set for `Status` single-select fields:

- `Ready` â†’ `Todo`, `To do`, `To-do`
- `In Progress` â†’ `In progress`, `In-Progress`, `InProgress`
- `Done` â†’ `Completed`, `Complete`

That alias support is only a compatibility bridge. The most predictable setup remains using the exact backlog labels in the Project field options.

### Important `Milestone` note

GitHub Projects also exposes a built-in field named `Milestone` with data type `MILESTONE`.
That built-in field is **not** the same as the custom execution-board milestone contract and is not currently supported by the sync script.

Use one of these setups:

- preferred: a custom `Milestone` field of type **single-select** or **text**
- fallback when the built-in field name is already present: a custom `Execution Milestone` field of type **single-select** or **text**

The sync now looks for `Milestone` first and falls back to `Execution Milestone` when needed.

During sync preflight, field-shape validation is reported once per run rather than once per backlog row:

- if only the built-in `Milestone` field is present, the sync emits one warning telling you to create `Execution Milestone` or replace the built-in field
- if a supported `Execution Milestone` field is present, the sync emits one informational message and then writes execution-board milestone values into that fallback field

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

For local/manual sync runs outside GitHub Actions, you can optionally override the generated detail-page link target with:

- `--repository-url <repo-web-url>` or environment variable `ONEFLOW_REPOSITORY_URL`
- `--repository-ref <branch-or-tag>` or environment variable `ONEFLOW_REPOSITORY_REF`

## Required GitHub configuration

Configure these repository-level settings before expecting the live projection sync to mutate the Project:

### Secret

- `ONEFLOW_PROJECT_SYNC_TOKEN`

Use a Personal Access Token that can update Project V2 items. For private repositories or private projects, the token should also have the minimum repo access required to read the backlog source and mutate the project.

### Variables

- `ONEFLOW_PROJECT_NUMBER` - the numeric project number from the Project URL, for example `3`
- `ONEFLOW_PROJECT_OWNER` - optional; defaults to the repository owner when omitted. If the Project URL is user-owned (`/users/<login>/projects/<number>`), set this to that user login. If the Project URL is organization-owned (`/orgs/<org>/projects/<number>`), set it to that organization login.
- `ONEFLOW_PROJECT_PUBLIC_MODE` - optional; set to `true` to omit internal notes and relative drill-down links from the synced draft-item body

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
$env:ONEFLOW_REPOSITORY_URL = 'https://github.com/kbalanandam/spring-etl-engine'
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

That keeps the canonical execution board and the live Project projection aligned without maintaining the same state manually in two places.

