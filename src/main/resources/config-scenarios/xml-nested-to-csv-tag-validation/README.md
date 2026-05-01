# xml-nested-to-csv-tag-validation

Preserved explicit job scenario for a nested XML source to CSV target flow using the shared nested tag-validation shape.

## Purpose

This scenario proves that the shared nested XML flattening path can feed a flat CSV target without introducing a scenario-specific processor.

It preserves:

- build-time generated XML source models
- nested XML source flattening through `NestedXml`
- shared `DefaultDynamicProcessor` mapping from flattened nested keys
- CSV target writing through a job-scoped generated flat target model class

## Files

- `job-config.yaml` - explicit job selection and step binding
- `source-config.yaml` - nested XML source definition using `modelDefinitionPath`
- `target-config.yaml` - CSV target definition for the flattened export shape
- `processor-config.yaml` - shared processor mapping from flattened nested keys to CSV fields
- `definitions/nested-source-model.yaml` - structural nested XML source contract
- `input/9002_9002_20260427070109.DTVL` - preserved production-style DTVL payload used for higher-volume nested XML verification

## Notes

- This scenario intentionally keeps a larger preserved DTVL payload for scale-oriented nested XML verification.
- Tests or examples that need a tiny sample should reuse `../xml-nested-tag-validation/input/nested-sample.xml` instead of assuming a local `nested-sample.xml` copy exists here.
- The source model definition remains structural only; flattening stays in the XML source strategy layer.
- The CSV target class `com.etl.generated.job.xmlnestedtocsvtagvalidation.target.TagValidationCsv` is regenerated from `target-config.yaml` during the job-scoped generation flow.


