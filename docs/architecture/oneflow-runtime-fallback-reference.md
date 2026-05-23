# OneFlow Runtime Fallback Reference

## Purpose

This page is a decision-support reference for fallback and defaulting behavior in the shipped OneFlow runtime.

Use it when deciding whether to keep, tighten, or remove fallback paths.

## Status

- Classification: **Current runtime**
- Scope: **Shipped fallback/default behavior only**

## Read This First

Not every decision below is an optional fallback. Some are strict fail-fast guardrails that intentionally prevent fallback behavior.

For each item, this page lists:

- trigger condition
- runtime behavior
- operator evidence impact
- primary code anchor

## Fallback and default matrix

| Area | Trigger | Behavior today | Evidence / impact | Code anchor |
|---|---|---|---|---|
| Run entry selection | `etl.config.job` missing and `etl.config.allow-demo-fallback=false` | Startup fails fast (no implicit fallback) | Explicit startup failure before run assembly | `src/main/java/com/etl/config/ConfigLoader.java` (`buildRuntimeConfig`) |
| Run entry selection | `etl.config.job` missing and `etl.config.allow-demo-fallback=true` | Runtime enters demo fallback mode and uses direct config properties | Scenario is reported as `demo-fallback` | `src/main/java/com/etl/config/ConfigLoader.java` (`buildRuntimeConfig`) |
| Direct config file load | Demo fallback mode and configured direct YAML path is missing | Loader falls back to classpath resource (`source-config.yaml`, `target-config.yaml`, `processor-config.yaml`) | Warning log indicates configured path missing and classpath fallback used | `src/main/java/com/etl/config/ConfigLoader.java` (`loadYamlConfig`) |
| Selected job path alias | `etl.config.job` points to legacy `config-scenarios/...` path and canonical `config-jobs/...` exists | Path is remapped to canonical bundle location | Info log emits alias resolution | `src/main/java/com/etl/common/util/ConfigBundlePathAliasResolver.java`, `src/main/java/com/etl/config/ConfigLoader.java` (`resolveSelectedJobConfigPath`) |
| Referenced config paths under selected job | `job-config.yaml` references `sourceConfigPath`/`targetConfigPath`/`processorConfigPath` | No alias remap at this stage; paths resolve directly from selected job-config folder | Missing referenced files fail fast | `src/main/java/com/etl/config/ConfigLoader.java` (`resolveReferencedPath`, `loadRequiredExternalYamlConfig`) |
| Relative artifact path normalization | Nested paths begin with `src/...` or `target/...` | Treated as working-directory compatibility paths | Preserves legacy repo-relative artifact locations | `src/main/java/com/etl/config/ConfigLoader.java` (`resolveScenarioPath`, `isWorkingDirectoryRelativeCompatibilityPath`) |
| Demo step synthesis | Demo fallback has sources/targets but no explicit job steps | Steps are synthesized by source/target list index | Requires source and target counts to match; otherwise fail fast | `src/main/java/com/etl/config/ConfigLoader.java` (`synthesizeDemoSteps`, `requireDemoFallbackTargets`) |
| Job activation default | `job-config.yaml` omits `isActive` | Treated as active (`true`) | Only explicit `isActive: false` blocks startup | `src/main/java/com/etl/config/ConfigLoader.java` (`requireActiveSelectedJob`) |
| Selected-job package naming | Explicit selected-job source/target configs omit `packageName` | Package names are derived from `job-config.yaml -> name` | Deterministic job-scoped generated package contract | `src/main/java/com/etl/config/ConfigLoader.java` (`applyDefaultSourcePackages`, `applyDefaultTargetPackages`), `src/main/java/com/etl/common/util/JobScopedPackageNameResolver.java` |
| Direct-config package naming | Demo fallback direct config path | Source/target packages default to `com.etl.model.source` / `com.etl.model.target` | Keeps direct-config compatibility path stable | `src/main/java/com/etl/config/ConfigLoader.java` (`applyDirectConfigSourcePackages`, `applyDirectConfigTargetPackages`) |
| Step mode selection | `getRecordCount()` throws or returns unknown (`< 0`) | Runtime defaults to chunk path by treating count as `chunkThreshold + 1` | Logs note unknown count and chunk defaulting | `src/main/java/com/etl/config/BatchConfig.java` |
| Chunk vs tasklet default | Record count compared to `etl.chunk.threshold` | `recordCount > threshold` => chunk; else tasklet | `STEP_READY` logs selected mode | `src/main/java/com/etl/config/BatchConfig.java` |
| Ordered duplicate execution override | Duplicate winner selection (`duplicate + orderBy`) would otherwise run chunk | Runtime overrides to tasklet for final-winner buffering | `STEP_READY event=step_mode_override` evidence | `src/main/java/com/etl/config/BatchConfig.java` |
| Duplicate storage mode default | Duplicate winner selection rule omits `storageMode` | Defaults to `auto` | Resolver mode decided from volume hints | `src/main/java/com/etl/runtime/DuplicateRule.java` |
| Duplicate resolver auto choice | `storageMode=auto` and ordered duplicate winner selection active | Uses in-memory resolver for smaller known sets; embedded DB for larger or unknown sets | `duplicate_resolver_plan` and resolver lifecycle logs show mode/reason | `src/main/java/com/etl/config/BatchConfig.java`, `src/main/java/com/etl/runtime/DuplicateResolverFactory.java` |
| Duplicate identity mode default | Duplicate rule omits `duplicateIdentityMode` | Defaults to `flatMapped` | Identity mode/reason emitted in duplicate planning evidence | `src/main/java/com/etl/runtime/DuplicateRule.java` |
| Processor type contract | `processor-config.yaml` type missing/blank/non-default on selected-job path | No fallback. Startup fails fast; runtime accepts only `type: default` | Clear config error before processor creation | `src/main/java/com/etl/config/ConfigLoader.java`, `src/main/java/com/etl/processor/DynamicProcessorFactory.java` |

## Decision guidance

Use this quick policy when discussing future fallback changes:

1. Keep fail-fast when fallback can hide a production misconfiguration.
2. Keep compatibility fallback only when migration cost outweighs operational risk.
3. Require machine-readable evidence for fallback paths that remain active.
4. Prefer explicit config over inferred behavior when a run boundary is business-critical.

## Known boundaries

- This page tracks runtime/config fallback behavior only.
- It does not catalog every field-level default in every source/target/processor subtype.
- It does not replace scenario-specific operator runbooks.

## Related docs

- [`Runtime flow`](runtime-flow.md)
- [`Job config`](../config/job-config.md)
- [`Default processor reference`](../config/processor/default-processor.md)
- [`Extension points`](extension-points.md)
- [`Job-level activation and startup guardrails`](job-level-activation-and-startup-guardrails.md)

