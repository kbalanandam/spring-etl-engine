# xml-nested-to-csv-tag-validation

Preserved explicit job scenario for a nested XML source to CSV target flow using a sanitized nested tag-validation sample payload.

## Purpose

This scenario proves that the shared nested XML flattening path can feed a flat CSV target without introducing a scenario-specific processor.

It preserves:

- build-time generated XML source models
- build-time generated flat CSV target model for the selected step
- nested XML source flattening through `NestedXml`
- shared `DefaultDynamicProcessor` mapping from flattened nested keys
- CSV target writing through the generated flat target model class
- archive-on-success for the preserved XML input under `output/archive/success/`

## Files

- `job-config.yaml` - explicit job selection and step binding
- `source-config.yaml` - nested XML source definition using `modelDefinitionPath` plus archive-on-success
- `target-config.yaml` - CSV target definition for the flattened export shape
- `processor-config.yaml` - shared processor mapping from flattened nested keys to CSV fields
- `definitions/nested-source-model.yaml` - structural nested XML source contract
- `input/tag-validation-sample.xml` - sanitized nested XML sample payload for the tag-validation proof
- `output/` - scenario-local runtime output folder for the flattened CSV export, rejected records, and archived source files

## Notes

- This scenario keeps a sanitized scenario-local XML sample so the preserved bundle stays safe to publish and rerun.
- The real `.DTVL` input and any deployable environment-specific variant now belong under `private-jobs/TVL/xml-nested-to-csv-tag-validation/`, not under this checked-in `config-jobs` bundle.
- The source model definition remains structural only; flattening stays in the XML source strategy layer.
- The CSV target uses a generated flat target class `com.etl.generated.job.xmlnestedtocsvtagvalidation.target.TagValidationCsv` on the job-scoped generation path.
- The preserved bundle keeps its visible runtime artifacts under `output/` so the sanitized sample input, flattened CSV output, and archived source copies stay together during local inspection.


