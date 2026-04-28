# ADR-0007: add separate processor transform SPI for cleaning and normalization

- Status: Accepted
- Date: 2026-04-28

## Context

`spring-etl-engine` now has an active processor-rule validation SPI through:

- `src/main/java/com/etl/config/processor/ProcessorConfig.java`
- `src/main/java/com/etl/processor/validation/ValidationRuleEvaluator.java`
- `src/main/java/com/etl/processor/validation/ProcessorValidationRule.java`
- `src/main/java/com/etl/mapping/ValidationAwareDynamicMapping.java`

That SPI is appropriate for record acceptance and rejection decisions such as:

- `notNull`
- `timeFormat`
- `duplicate`

However, upcoming business needs also include field cleanup and code normalization such as:

- `1 -> Success`, `2 -> Fail`, else `Unknown`
- `USA -> US`, `IND -> IN`
- future N-step cleaner chains on one field

Those behaviors are not validation in the strict sense.

They answer:

> How should this value be rewritten before it is validated or written?

not:

> Should this record be rejected?

If those cleaner behaviors are pushed into the validation SPI, the product will blur two different responsibilities:

- value transformation / normalization
- record acceptance / rejection

That would make future growth harder once the product needs many cleaner techniques, reusable mappings, and multiple transform steps on the same field.

## Decision

The product will keep a separate future **processor transform SPI** for field cleaning and normalization.

This processor transform extension point should:

1. live on the active default-processor path
2. run before processor validation rules
3. stay config-driven and field-scoped
4. support chaining multiple transforms for one field
5. begin with a narrow first built-in transform such as `valueMap`

The intended runtime order is:

1. read raw source value
2. apply configured field transforms / cleaners
3. evaluate processor validation rules on the transformed value
4. write the transformed target value

The first YAML rollout should stay narrow:

- ship processor-side field transforms first
- keep the transform block optional by omission
- support zero, one, or many ordered transform steps on a field
- defer a broad source-transform YAML contract until a real source-native case requires it

The default ownership rule is:

- use processor transforms for shared field/value cleanup that works once a normal runtime record exists
- reserve source transforms for future source-native adaptation cases such as XPath-, namespace-, header-, token-, or other pre-flattening/source-shape logic
- keep accept/reject decisions in processor validation rules even when a transformed value later causes rejection

That means transform-then-reject is valid by design. For example, a transform may normalize unknown country codes to `UNKNOWN`, and a later processor rule may reject `UNKNOWN` for a strict target contract.

## Consequences

### Positive

- value cleanup stays separate from reject logic
- future cleaner techniques have a coherent extension home
- validation rules can evaluate normalized values instead of raw upstream codes
- common mappings such as status decoding and country-code normalization can remain config-driven
- the product can grow from simple value maps toward richer normalization without forcing early adoption of a broad expression language

### Negative

- the processor path will gain another extension point to document and test
- config validation will need to expand beyond current `rules`
- some future contributors may initially try to model transforms as validation rules unless the distinction stays well documented
- once source transforms exist, config validation will also need guardrails so equivalent generic value rewriting is not configured ambiguously in both source and processor layers

## Notes

- first planned transform example: `valueMap`
- first planned scenarios: code-to-label mapping and code normalization
- validation remains responsible for pass/fail decisions only
- this ADR does not claim that processor transforms are fully shipped yet; it defines the architectural direction for the next transformation slice
- if the same field is later configured for equivalent generic rewriting in both source and processor layers, the product should fail fast or at least emit a strong warning unless the layered behavior is clearly source-native adaptation followed by processor normalization

## Related

- [`0006-separate-source-validation-and-processor-rule-spis.md`](0006-separate-source-validation-and-processor-rule-spis.md)
- [`../architecture/extension-points.md`](../architecture/extension-points.md)
- [`../architecture/transformation-capability-roadmap.md`](../architecture/transformation-capability-roadmap.md)
- [`../config/processor/default-processor.md`](../config/processor/default-processor.md)



