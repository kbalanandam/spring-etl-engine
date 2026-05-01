# Hardening Documentation Sync Note

## Purpose

This note records the documentation sync that accompanied the recent explicit-scenario hardening work.

It remains in the repo as a lightweight implementation-to-doc audit trail.

## Current status

### Completed in the current working tree

These documentation fixes are already applied because they match the checked-in scenario assets today:

- `src/main/resources/config-scenarios/xml-nested-to-csv-tag-validation/README.md`
- `docs/config/README.md`

Those files now correctly describe that:

- `xml-nested-to-csv-tag-validation` keeps the preserved `9002_9002_20260427070109.DTVL` payload
- tiny sample-based nested XML examples should reuse `xml-nested-tag-validation/input/nested-sample.xml`

The current working tree now contains the hardening behavior and matching documentation updates for:

- processor config validation runs before generated-model validation for explicit `etl.config.job` scenarios
- processor config failures are surfaced with scenario-aware context
- `NestedXml` source validation requires the generated record class, but does not always require the XML source root class
- nested XML verification notes consistently distinguish between the shared tiny sample and the larger preserved DTVL payload

## Runtime/code checkpoints

The matching runtime behavior is now present in these files:

- `src/main/java/com/etl/config/ConfigLoader.java`
- `src/main/java/com/etl/common/util/GeneratedModelClassResolver.java`

The matching regression coverage is present in:

- `src/test/java/com/etl/config/ConfigLoaderJobConfigTest.java`
- `src/test/java/com/etl/common/util/GeneratedModelClassResolverTest.java`

Focused verification also passed after the implementation and doc sync updates were applied.

## Docs updated as part of the sync

### 1. `README.md`

Updated with a brief explicit-run note.

- explicit scenario startup validation now mentions selected source/target/processor validation before execution
- processor config failures are described as surfacing before unrelated generated-model class failures
- the wording stays high-level and operator-oriented

## 2. `docs/config/job-config.md`

Updated to document explicit-run validation order.

- `Runtime behavior today` now notes that explicit runs validate the selected processor config before generated-model class checks
- validation notes now mention scenario-specific processor config failures with scenario and config-path context
- the wording stays scoped to explicit `etl.config.job` runs

## 3. `docs/config/source/xml-source.md`

Updated to describe the actual XML source model contract used at startup.

- the `packageName` field description no longer implies that `NestedXml` always requires both XML source root and record classes
- runtime notes now distinguish between record-class requirements and the relaxed `NestedXml` root-class rule
- target-side XML validation notes remain separate

## 4. `docs/config/processor/default-processor.md`

Updated to explain startup validation expectations for processor config.

- usage notes now say that explicit scenario runs validate processor mappings and rule configuration during startup
- processor-config problems are documented as failing before unrelated generated-model validation issues
- scenario-aware failure messages are called out so operators can identify the selected scenario/config quickly

## 5. `docs/architecture/runtime-flow.md`

Updated to reflect the real startup order.

- the sequence view now shows `ConfigLoader` validating processor config before generated-model validation
- `Important runtime decisions` now describe the explicit-run validation order clearly
- XML startup notes now distinguish between the shared `NestedXml` record-model path and the non-`NestedXml` root-class requirement

## Sync order used

The doc sync was applied in this order:

1. `docs/config/job-config.md`
2. `docs/config/source/xml-source.md`
3. `docs/config/processor/default-processor.md`
4. `docs/architecture/runtime-flow.md`
5. `README.md`

That kept the detailed technical truth in the config and architecture docs first, then rolled a concise summary into the top-level README.

## Verification completed

This sync note was closed after:

- confirming the hardening behavior exists in the current `ConfigLoader` and `GeneratedModelClassResolver`
- updating the five reference docs above
- rerunning a quick wording sweep for:
  - `NestedXml`
  - `processor config`
  - `generated model`
  - `scenario-aware`
- keeping scenario READMEs aligned with the actual checked-in assets



