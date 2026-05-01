# Hardening Documentation Sync Checklist

## Purpose

This note tracks the documentation follow-up needed for the recent explicit-scenario hardening work without claiming runtime behavior that is not yet visible in the current code snapshot.

Use it when implementation and docs do not land at exactly the same time.

## Current status

### Already updated

These documentation fixes are already applied because they match the checked-in scenario assets today:

- `src/main/resources/config-scenarios/xml-nested-to-csv-tag-validation/README.md`
- `docs/config/README.md`

Those files now correctly describe that:

- `xml-nested-to-csv-tag-validation` keeps the preserved `9002_9002_20260427070109.DTVL` payload
- tiny sample-based nested XML examples should reuse `xml-nested-tag-validation/input/nested-sample.xml`

### Pending once the hardening code is present in the current branch

The following updates should be made only after the implementation is visible in the checked-in source:

- processor config validation runs before generated-model validation for explicit `etl.config.job` scenarios
- processor config failures are surfaced with scenario-aware context
- `NestedXml` source validation requires the generated record class, but does not always require the XML source root class
- nested XML verification notes consistently distinguish between the shared tiny sample and the larger preserved DTVL payload

## Current verification blocker

Before updating the remaining reference docs, confirm that the active code in these files actually contains the intended hardening behavior:

- `src/main/java/com/etl/config/ConfigLoader.java`
- `src/main/java/com/etl/common/util/GeneratedModelClassResolver.java`

At the time this checklist was written, the current workspace snapshot did **not** clearly show all of the expected implementation markers, such as:

- a dedicated `validateProcessorConfig(...)` helper
- scenario-aware processor-config exception wording
- a `NestedXml`-specific relaxation for XML source root-class validation

Until those behaviors are present in code, reference docs should not describe them as shipped runtime guarantees.

## Exact docs to update after code lands

### 1. `README.md`

Update the high-level explicit-run description only briefly.

Planned edits:

- in `Explicit job-config mode`, add one short note that explicit scenario startup validates selected source, target, and processor config before step execution
- mention that processor config failures should surface before unrelated generated-model class failures
- keep this section high-level and operator-oriented instead of duplicating the full config reference

## 2. `docs/config/job-config.md`

This is the best place to document explicit-run validation order.

Planned edits:

- extend `Runtime behavior today` with a bullet that explicit runs validate the selected processor config before generated-model class checks
- add a validation note that scenario-specific processor config failures should reference the selected scenario and config path
- keep the wording scoped to explicit `etl.config.job` runs so demo fallback behavior is not overstated

## 3. `docs/config/source/xml-source.md`

This page should describe the actual XML source model contract used at startup.

Planned edits:

- refine the `packageName` field description so it does not imply that `NestedXml` always requires both XML source root and record classes
- update `Runtime behavior today` to say:
  - XML source record classes are required for explicit XML runs
  - non-`NestedXml` XML source paths still require the XML source root class
  - `NestedXml` flows may not require the XML source root wrapper class
- keep target-side XML validation notes separate unless target-side behavior also changes

## 4. `docs/config/processor/default-processor.md`

This page should explain startup validation expectations for processor config.

Planned edits:

- add a short usage note that explicit scenario runs validate processor mappings and rule configuration during startup
- mention that processor config problems should fail before unrelated generated-model validation issues
- note that scenario-aware messages are preferred so operators can identify which selected scenario/config failed

## 5. `docs/architecture/runtime-flow.md`

This page should reflect the real startup order once hardening is present.

Planned edits:

- update the sequence diagram so `ConfigLoader` validates processor config before generated-model validation
- update `Important runtime decisions` to describe the explicit-run validation order clearly
- if fixture guidance is included here, note that nested XML proof runs use:
  - the shared small sample for test-sized verification
  - the larger preserved DTVL payload for the nested XML-to-CSV preserved scenario

## Recommended update order

After the code lands, update docs in this order:

1. `docs/config/job-config.md`
2. `docs/config/source/xml-source.md`
3. `docs/config/processor/default-processor.md`
4. `docs/architecture/runtime-flow.md`
5. `README.md`

This keeps detailed technical truth in the config and architecture docs first, then rolls a concise summary into the top-level README.

## Verification before closing this checklist

Before marking this work done:

- confirm the intended hardening behavior exists in the current `ConfigLoader` and `GeneratedModelClassResolver`
- update the five pending docs above
- rerun a quick wording sweep for:
  - `NestedXml`
  - `processor config`
  - `generated model`
  - `scenario-aware`
- keep scenario READMEs aligned with the actual checked-in assets

