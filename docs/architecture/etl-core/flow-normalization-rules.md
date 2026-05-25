# Flow Normalization Rules

## Purpose

This note explains how simple and complex flow shapes should normalize into one consistent internal model without forcing unnecessary authoring complexity for small scenarios.

It exists to preserve two goals at the same time:

1. one standard flow model for runtime assembly, logging, and evidence
2. a lightweight authoring experience for simple cases such as one-step flows

## Status

- Classification: **Current baseline + future evolution**
- This note documents normalization rules for the frozen first implementation slice and the longer-term reusable-at-any-level direction.

## Core principle

Use one standard internal flow model, but do not force every layer to be explicitly authored in every scenario.

The practical rule is:

> standardize the model, not the user burden

## Standard internal model

The internal model should normalize toward this shape:

```text
Scenario execution
  -> MainFlow
      -> SubFlow
          -> Step
```

Where:

- `Scenario execution` remains the explicit runtime boundary
- `MainFlow` remains the top-level business flow for the selected run and the shared context boundary across all contained subflows
- `SubFlow` groups one or more steps into a reusable nested phase
- `Step` remains the smallest executable `source -> processor -> target` unit in the current baseline

## Normalization rule

### Always present

The following levels should always exist conceptually:

- scenario execution
- main flow
- step

### Optional or implicit

`SubFlow` should be:

- explicit when it adds meaningful composition, reuse, or observability value
- implicit when the authored flow is too small to justify a visible extra layer

This means the platform may infer a default subflow internally even when the author does not explicitly define one.

## Why keep MainFlow even for simple cases

Even a one-step flow should retain `MainFlow` context because it gives:

- one stable business-flow identity
- one stable logging root above the step
- one stable evidence roll-up root
- one future-safe composition boundary if the flow later grows into multiple steps or subflows
- one place for shared control-plane context such as cross-subflow handshake metadata when the flow later becomes composed

Without `MainFlow`, simple flows and larger flows would need different runtime identities and different observability rules.

## Why SubFlow may be implicit for simple cases

For one-step or very small flows, forcing explicit subflow authoring adds conceptual weight without enough immediate value.

Allowing `SubFlow` to be implicit gives:

- simpler scenario authoring
- a cleaner first-run experience
- one consistent internal runtime model
- no loss of future extensibility

## Normalization patterns

## 1. Single-step simple flow

### Authored shape

```text
Scenario execution
  -> MainFlow
      -> Step
```

### Normalized internal shape

```text
Scenario execution
  -> MainFlow
      -> SubFlow(default)
          -> Step
```

### Example

```text
customer-xml-export
  -> CustomerXmlExportFlow
      -> customers-xml-to-csv
```

This is the recommended normalization for simple `xml -> csv`, `csv -> xml`, or similarly small one-step flows.

## 2. Single-subflow multi-step flow

### Authored shape

```text
Scenario execution
  -> MainFlow
      -> SubFlow
          -> Step 1
          -> Step 2
```

### Normalized internal shape

Same as authored.

Use this shape when a grouped technical or business phase already matters.

## 3. Multiple-subflow main flow

### Authored shape

```text
Scenario execution
  -> MainFlow
      -> SubFlow A
          -> Step 1
      -> SubFlow B
          -> Step 2
          -> Step 3
```

### Normalized internal shape

Same as authored.

Use this shape when grouped phases should be separately named, logged, or evidenced.

## 4. Longer-term reusable flow-at-any-level direction

The frozen first implementation slice uses `MainFlow` and `SubFlow` role labels.

The longer-term direction should allow one reusable flow to play either role:

- top-level `MainFlow` in one composition
- nested `SubFlow` in another composition

That later direction does not change the normalization rule for the early slice.

It means the early slice should be designed so it can later generalize into a reusable `Flow` model with different composition roles.

## Logging normalization

For the first slice, logging should always preserve:

- scenario identity
- main-flow identity
- step identity
- current recovery policy

Subflow identity should be:

- explicit when authored explicitly
- implicit/default when normalized for simple flows

That means a simple one-step flow may still log as:

```text
RUN_EVENT scenario=customer-xml-export mainFlow=CustomerXmlExportFlow
STEP_EVENT step=customers-xml-to-csv source=CustomersXml target=CustomersCsv
RUN_SUMMARY scenario=customer-xml-export mainFlow=CustomerXmlExportFlow
```

while the internal model may still carry a default subflow for consistency.

## Evidence normalization

For the first slice:

- `MainFlow` should always remain the top summary boundary within the selected scenario
- `Step` evidence should always exist
- `SubFlow` evidence may be explicit or synthesized depending on whether subflow identity is explicit in the authored shape

In practice:

- one-step simple flows need main-flow summary + step evidence
- multi-step grouped flows need main-flow summary + subflow summaries + step evidence

## Shared-context normalization

For the bounded first slice, `MainFlow` should carry the common context that spans all contained work:

- logging and recovery context
- shared artifact references
- handshake/readiness metadata between reusable subflows
- named subflow statuses and upstream failure blocking metadata consumed by later subflows

That shared context should stay control-plane only. Actual business payload should continue to move through explicit step/subflow outputs and inputs rather than being stored directly in `MainFlow` context.

## Recovery normalization

For the current bounded implementation slice, recovery should normalize the same way for simple and grouped flows:

- the selected scenario is the restart boundary
- if any step fails, the run fails
- the next attempt reruns the whole selected scenario from the beginning
- restart from a failed step or checkpoint is deferred to a later maturity slice

That means `MainFlow` and `SubFlow` should already exist in the internal model and logs, but they are not yet resumable boundaries.

## Implementation guardrails

### 1. Keep the user-facing simple case lightweight

Do not require explicit authored subflow definitions for one-step flows.

### 2. Keep the internal model uniform

Normalize simple flows internally so downstream runtime, logging, and evidence code can stay coherent.

### 3. Do not remove MainFlow from the model

MainFlow should remain the stable top-level business identity for the selected scenario even when only one step exists.

### 4. Keep the first slice bounded

Use the `Scenario -> MainFlow -> optional/implicit SubFlow -> Step` model for the early implementation slice.

### 5. Preserve future generalization

Design the first slice so `MainFlow` and `SubFlow` can later collapse into a more general reusable `Flow` identity when reuse-at-any-level is implemented.

## Decision table

| Scenario shape | MainFlow | SubFlow | Step | Recommended handling |
|---|---|---|---|---|
| One-step simple flow | explicit | implicit/default | explicit | keep authored config light; normalize internally |
| One grouped multi-step phase | explicit | explicit | explicit | preserve authored grouping directly |
| Multiple grouped phases | explicit | explicit | explicit | preserve grouping and roll-up evidence |
| Future reusable flow at any level | explicit role | explicit role | explicit | later generalize to reusable flow identity |

## Relationship to current config baseline

The current shipped executable contract remains the flat `job-config.yaml` `steps` list.

This note does **not** change that baseline.

It explains how future hierarchy-aware runtime assembly should normalize simple and complex cases while preserving the same single-scenario execution boundary.

## Related docs

- [`Scenario-driven runtime direction`](scenario-driven-runtime-direction.md)
- [`Hierarchical flow composition`](hierarchical-flow-composition.md)
- [`Runtime-to-scenario gap assessment`](runtime-to-scenario-gap-assessment.md)
- [`Job config reference`](../../config/job-config.md)

