# ETL custom-step pairing and context handoff

## Purpose

Define a future-direction architecture contract that lets customer-owned custom steps run before/after standard OneFlow steps while preserving one explicit ordered `job-config.yaml` runtime plan.

## Status

- Classification: **Future direction**
- Backlog anchor: [`A7 - Add custom-step pairing, context handoff, and failure-contract baseline`](../../product/backlog-items/etl-core/A7-custom-step-pairing-context-handoff-and-failure-contract.md)

## Design goals

- keep one executable ordered `steps[]` plan in `job-config.yaml`
- keep one runtime assembly path in `BatchConfig`
- allow bounded customer-owned code without forking OneFlow core runtime behavior
- preserve shared run evidence and failure categorization across standard and custom steps

## Non-goals

- scenario auto-discovery
- hidden orchestration hooks outside explicit `steps[]`
- replacing flat ordered Spring Batch execution with a second execution engine

## Runtime model

Conceptually, step resolution remains one path:

1. parse ordered `steps[]`
2. for each step, resolve by `kind`
   - `standard` -> existing source/processor/target step assembly
   - `custom` -> custom provider/handler assembly resolved by `steps[].custom.type`
3. build one ordered Spring Batch job from both step kinds

This keeps custom and standard steps operationally equivalent at runtime (`Step` -> `Step` -> `Step`).

## A7 Architecture Invariants

Use these as locked review criteria for A7 design and implementation PRs.

1. **One plan only**: execution order is defined only by explicit `job-config.yaml` `steps[]`; no hidden hooks or auto-discovery.
2. **One assembly path**: `BatchConfig` remains the single job assembly point; standard and custom steps both become normal Spring Batch `Step` instances.
3. **Backward compatibility first**: omitted `steps[].kind` defaults to `standard`; standard-only jobs preserve existing behavior and startup validation.
4. **Shared outcome semantics**: custom outcomes normalize only through `CONTINUE` / `STOP` / `FAIL`; no ad hoc terminal states.
5. **Shared evidence model**: custom and standard steps emit the same core run/step evidence shape; custom metadata is additive, not a replacement.
6. **Context ownership contract**: cross-step keys are namespaced and write-once by default; required key/type checks fail fast before dependent execution.
7. **Failure taxonomy contract**: every failure maps to one category (`config`, `binding`, `context`, `execution`) for Epic D-aligned operator diagnostics.
8. **Scheduler/control-plane parity**: scheduled and manual runs execute the same selected-job contract; no separate launch model for custom steps.
9. **Completion semantics parity**: custom step participation cannot bypass or weaken job completion/failure semantics used by standard flows.
10. **Scale boundary discipline**: context payloads stay lightweight (IDs/refs, not large blobs); heavy custom logic should stay externalized and stateless where possible.

Change rule:

- any invariant change requires synchronized updates to this note and [`A7 backlog item`](../../product/backlog-items/etl-core/A7-custom-step-pairing-context-handoff-and-failure-contract.md), and should include ADR review when tradeoffs materially shift runtime behavior.

## Backward compatibility contract

Standard-only jobs remain first-class and unchanged.

Compatibility rules for the first implementation slice:

- `steps[].kind` is optional; omitted means `standard`
- existing standard step fields (`name`, `source`, `target`) keep current semantics
- `custom` metadata is ignored unless `kind: custom`
- validation and failure behavior for standard-only jobs must stay unchanged
- existing operator evidence for standard-only steps remains stable; custom-step evidence is additive

Non-compatible change boundary (out of scope for A7 first slice):

- making `steps[].kind` mandatory for all steps
- requiring custom-step fields on standard steps
- altering standard-step continuation/failure rules

## Phase-1 custom-step identity contract

Keep identity split explicit and single-purpose in the first slice:

- `steps[].name` is operator-visible identity only (plan/logging/evidence)
- `steps[].custom.type` is runtime binding identity only (provider resolution)
- `steps[].kind` remains optional and defaults to `standard`

Phase-1 guardrails:

- do not introduce `taskRef` in this slice
- do not bind providers from `steps[].name`
- do not add alternate identity aliases that duplicate `custom.type`

## Proposed Java skeletons

### Config model

```java
public final class JobStepConfig {
    private String name;
    private String kind; // "standard" | "custom"

    // Standard-step fields
    private String source;
    private String target;

    // Custom-step fields
    private CustomStepConfig custom;
}

public final class CustomStepConfig {
    private String type;
    private Map<String, String> publish;
    private Map<String, String> consume;
    private Map<String, String> onResult;
    private Map<String, Object> config;
}
```

### Provider and handler seam

```java
public interface CustomStepProvider {
    String providerId();
    String stepType();
    int order();
    boolean isOverride();

    CustomStepHandler createHandler(CustomStepConfig config);
}

public interface CustomStepHandler {
    CustomStepResult execute(CustomStepContext context) throws Exception;
}
```

### Factory and context bridge seam

```java
public interface DynamicCustomStepFactory {
    Step buildCustomStep(String stepName, CustomStepConfig config);
}

public interface CustomStepContextBridge {
    void publish(String key, Object value, ContextWriteMode mode);
    <T> T require(String key, Class<T> expectedType);
    Optional<Object> read(String key);
}
```

### Outcome model

```java
public enum CustomStepAction {
    CONTINUE,
    STOP,
    FAIL
}

public final class CustomStepResult {
    private String resultCode; // SUCCESS, FAILED, COMPLETED_WITH_WARNINGS, ...
    private String message;
    private Map<String, Object> outputs;
}
```

`CustomStepOutcomeMapper` maps `resultCode` + configured `onResult` to `CustomStepAction`.

## Proposed package layout

Use this as the first-slice package boundary proposal (non-binding until implementation PR finalizes names):

```text
src/main/java/com/etl/config/job/
  JobConfig.java                           // extend JobStepConfig with kind/custom
  CustomStepConfig.java                    // typed custom-step config object

src/main/java/com/etl/step/custom/
  DynamicCustomStepFactory.java
  DefaultDynamicCustomStepFactory.java
  CustomStepOutcomeMapper.java
  CustomStepFailureFinalizer.java

src/main/java/com/etl/step/custom/spi/
  CustomStepProvider.java
  CustomStepHandler.java

src/main/java/com/etl/step/custom/context/
  CustomStepContext.java
  CustomStepContextBridge.java
  DefaultCustomStepContextBridge.java
  ContextWriteMode.java

src/main/java/com/etl/step/custom/exception/
  CustomStepConfigException.java
  CustomStepBindingException.java
  CustomStepContextException.java
  CustomStepExecutionException.java
```

Design boundary notes:

- keep config objects under `com.etl.config.*` and runtime execution seams under `com.etl.step.custom.*`
- keep SPI contracts in `...custom.spi` so customer extensions remain discoverable
- keep context enforcement isolated in `...custom.context` so key ownership rules are centralized
- keep exception types in one package so taxonomy mapping stays explicit for Epic D alignment

## Exception hierarchy proposal

Use one clear category per failure path.

```java
// startup/config stage
class CustomStepConfigException extends ConfigException {}
class CustomStepBindingException extends ConfigException {}

// runtime execution stage
class CustomStepContextException extends IllegalStateException {}
class CustomStepExecutionException extends RuntimeException {}
```

Category meaning:

- `config`: invalid/missing custom-step config shape or required field combination
- `config`: invalid/missing custom-step config shape or required field combinations
- `binding`: unknown custom `type`, duplicate provider conflict, override misuse
- `context`: missing key, incompatible type, forbidden overwrite
- `execution`: SQL/IO/business failure inside customer handler

## Context-key policy

### Naming

- use namespaced keys, for example `header.fileId`, `header.status`, `audit.errorCount`
- avoid generic keys such as `id`, `status`, `count`

### Ownership

- default write mode: `WRITE_ONCE`
- explicit override mode required to mutate existing keys
- key ownership tracked with step name in execution context metadata

### Validation

- required consumed keys must be validated before dependent step execution
- type checks are mandatory on `require(key, type)` reads
- missing/invalid keys fail fast through `CustomStepContextException`

### Evidence

publish and consume actions should emit step-level structured logs, for example:

- `STEP_EVENT event=context_publish key=header.fileId ownerStep=header-start`
- `STEP_EVENT event=context_consume key=header.fileId consumerStep=detail-load`

## Failure finalization contract

For header/detail scenarios, preserve one bounded finalization seam:

- `CustomStepFailureFinalizer` runs when any upstream step fails
- finalizer should use an independent transaction for status updates
- finalizer must not alter final job failure semantics (job stays failed unless action mapping explicitly stops)

## Example sequence: CSV -> relational with header/detail

1. `custom header-start`
   - insert header row with `IN_PROGRESS`
   - publish `header.fileId`
2. `standard detail-load`
   - consume `header.fileId`
   - write child rows with FK `file_id`
3. `custom header-finalize-success`
   - update header status to `SUCCESS`
4. failure path
   - `CustomStepFailureFinalizer` updates header status to `FAILED`

## Compatibility and rollout

1. add schema and startup validation first
2. add provider/factory + context bridge
3. add outcome mapping and failure finalizer
4. add preserved examples under `config-jobs/`
5. align taxonomy with Epic D (`D1`) and add focused tests

## Test matrix

Use this matrix to define the first focused test slice before expanding scenarios.

| Area | Scenario | Expected result | Category |
|---|---|---|---|
| compatibility | legacy standard-only job (no `kind`) | job runs successfully with unchanged step assembly | compatibility |
| compatibility | legacy multi-step standard-only job (no `kind`) | ordered execution and status behavior unchanged | compatibility |
| compatibility | standard step declares no `custom` block | startup/step behavior unchanged | compatibility |
| config | `kind: custom` without `custom.type` | startup fails fast with `CustomStepConfigException` | config |
| config | `kind: custom` with empty/blank `custom` block | startup fails fast before job assembly | config |
| binding | unknown custom `type` with no provider | startup fails with `CustomStepBindingException` | binding |
| binding | duplicate provider registration without explicit override | startup fails deterministically | binding |
| context | standard step consumes missing required key (`header.fileId`) | dependent step fails before write starts | context |
| context | consumed key type mismatch | step fails with categorized context error | context |
| context | second publish to write-once key without override mode | step fails with `CustomStepContextException` | context |
| execution | customer SQL insert/update throws exception | step fails with wrapped `CustomStepExecutionException` | execution |
| flow | custom result mapped to `STOP` | job ends `STOPPED` and downstream steps do not run | execution |
| flow | custom result mapped to `FAIL` | job ends `FAILED` and failure finalizer runs | execution |
| finalize | upstream standard step fails after header create | failure finalizer updates header to `FAILED` | execution |
| observability | context publish/consume path | `STEP_EVENT` evidence emitted with key + step identity | evidence |

Recommended first preserved scenario coverage:

- `csv-to-relational-with-header-status` proves pre-step publish, standard consume, and failure finalization
- `xml-to-csv-with-custom-run-audit` proves non-relational custom pre/post audit behavior

## Related docs

- [`Extension points`](extension-points.md)
- [`Runtime flow`](runtime-flow.md)
- [`Flow normalization rules`](flow-normalization-rules.md)
- [`Job config`](../../config/job-config.md)
- [`A7 backlog item`](../../product/backlog-items/etl-core/A7-custom-step-pairing-context-handoff-and-failure-contract.md)
- [`Epic D - Error taxonomy and failure categorization`](../../product/epics/etl-core/epic-d-error-taxonomy-and-failure-categorization.md)
- [`Proposed package layout`](#proposed-package-layout)
- [`Test matrix`](#test-matrix)



