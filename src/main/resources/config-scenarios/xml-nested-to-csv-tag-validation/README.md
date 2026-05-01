# xml-nested-to-csv-tag-validation

Preserved explicit job scenario for a nested XML source to CSV target flow using the same nested tag-validation sample payload.

## Purpose

This scenario proves that the shared nested XML flattening path can feed a flat CSV target without introducing a scenario-specific processor.

It preserves:

- build-time generated XML source models
- nested XML source flattening through `NestedXml`
- shared `DefaultDynamicProcessor` mapping from flattened nested keys
- CSV target writing through a checked-in flat target model class

## Files

- `job-config.yaml` - explicit job selection and step binding
- `source-config.yaml` - nested XML source definition using `modelDefinitionPath`
- `target-config.yaml` - CSV target definition for the flattened export shape
- `processor-config.yaml` - shared processor mapping from flattened nested keys to CSV fields
- `definitions/nested-source-model.yaml` - structural nested XML source contract
- `input/nested-sample.xml` - preserved nested XML sample payload reused from the XML target proof

## Notes

- This scenario intentionally reuses the same nested XML business payload as `xml-nested-tag-validation`.
- The source model definition remains structural only; flattening stays in the XML source strategy layer.
- The CSV target uses a flat checked-in target class `com.etl.model.target.TagValidationCsv`.

