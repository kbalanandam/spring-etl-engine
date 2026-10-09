# PR label governance policy

## Goal

Keep pull requests easy to triage by applying a small, consistent label set that reflects the change area and review impact.

## Active operating model

- Apply labels on every PR before review/merge.
- Prefer a small shared vocabulary instead of ad hoc labels.
- Treat labels as governance metadata, not as a substitute for the PR description or checklist.
- If a PR changes both implementation and policy, apply both area and governance labels.

## Recommended label groups

### Area labels

- `deps` - dependency updates and version bumps
- `security` - CVE, secret-scanning, or dependency-security changes
- `ci` - workflows, automation, and build pipeline changes
- `docs` - documentation-only changes
- `runtime` - worker/runtime behavior changes
- `control-plane` - API, scheduler, persistence, or operator-control changes
- `generated-models` - model generation, package naming, or XML/DTO generation changes
- `config` - configuration, defaults, or profile changes
- `governance` - policy, review, or process changes

### Review / risk labels

- `needs-review` - requires deliberate human review before merge
- `needs-test` - requires extra verification or scenario coverage
- `breaking-change` - likely to affect compatibility or behavior
- `release-blocker` - must be resolved before release

### Automation / source labels

- `dependabot` - created by Dependabot
- `automation` - created by a bot or scripted workflow
- `manual-change` - created and reviewed manually

## Default mapping guidance

- Dependabot Maven PRs: `deps`, `automation`, and `needs-review` when the update changes the dependency graph materially.
- Dependabot GitHub Actions PRs: `ci`, `automation`.
- Security or suppression changes: `security`, `deps`, `needs-review`.
- Workflow changes: `ci`, `automation`.
- Runtime or generated-model contract changes: `runtime`, `generated-models`, `needs-test`.
- Governance/policy updates: `governance`, `docs`.

## What not to do

- Do not add labels that do not help reviewers understand impact.
- Do not use labels as a substitute for writing the PR summary.
- Do not introduce one-off labels for a single PR unless they are being promoted to a standard.
- Do not treat `dependabot` or `automation` labels as approval signals.

## Practical rule

If a PR changes dependency/security policy or release governance, it should carry at least one of:

- `governance`
- `security`
- `deps`
- `ci`

Use the narrowest accurate label set possible.

