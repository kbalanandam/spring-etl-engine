# xml-nested-tag-validation

Preserved explicit job scenario for a nested XML source to XML target flow.

## Purpose

This scenario is the first checked-in runtime bundle that proves the next-direction XML path can support:

- build-time generated XML source and target models
- nested XML source flattening through `NestedXml`
- shared `DefaultDynamicProcessor` field mapping from flattened nested keys
- XML target writing through generated target model classes

## Files

- `job-config.yaml` - explicit job selection and step binding
- `source-config.yaml` - nested XML source definition using `modelDefinitionPath`
- `target-config.yaml` - XML target definition for the flattened export shape
- `processor-config.yaml` - shared processor mapping from flattened nested keys to target fields
- `definitions/nested-source-model.yaml` - structural nested XML source contract
- `input/nested-sample.xml` - preserved nested XML sample payload

## Notes

- This scenario keeps flattening outside the XML generator.
- The source model definition is structural only.
- The target model is generated from flat target fields because the output shape is flat XML.

