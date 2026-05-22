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

### Runnable T15 proof pair (false merge vs xmlNative)

This bundle now includes one side-by-side proof pair that demonstrates how XML-native duplicate identity prevents a false duplicate merge.

- `input/tag-validation-false-merge-proof.xml` - two records share the same `HomeAgencyID` but have different nested account identities
- `job-config-proof-flatMapped.yaml` + `processor-config-proof-flatMapped.yaml` - flat mapped key (`HomeAgencyID`) intentionally collapses both records into one duplicate group
- `job-config-proof-xmlNative.yaml` + `processor-config-proof-xmlNative.yaml` - XML-native path key (`/TVLAccountDetails/AccountNumber`) preserves both records as distinct

Expected outputs after running both proof jobs:

- `output/tag-validation-proof-flatMapped.csv` contains **1** data row and `output/rejects-proof-flatMapped/` contains one rejected duplicate row.
- `output/tag-validation-proof-xmlNative.csv` contains **2** data rows and `output/rejects-proof-xmlNative/` contains no rejected duplicate rows.

### Duplicate identity guardrails

- Literal flat keys that contain `@` as part of the field name (for example `tag@code`) remain valid in `duplicateIdentityMode: flatMapped`.
- Selector-shaped XML keys (for example `@code` or `/event/tag/@code`) require `duplicateIdentityMode: xmlNative`.
- For `xmlNative`, selector expressions that explicitly encode repeating-node traversal are rejected at startup in the current runtime. Examples: `/event/tags[0]/@code`, `/event/tags[*]/@code`, and wildcard segment forms such as `/*/`.

## Notes

- This scenario keeps a sanitized scenario-local XML sample so the preserved bundle stays safe to publish and rerun.
- The real `.DTVL` input and any deployable environment-specific variant now belong in a developer-local copy under `private-jobs/<collection>/xml-nested-to-csv-tag-validation/`, not under this checked-in `config-jobs` bundle.
- The source model definition remains structural only; flattening stays in the XML source strategy layer.
- The CSV target uses a generated flat target class `com.etl.generated.job.xmlnestedtocsvtagvalidation.target.TagValidationCsv` on the job-scoped generation path.
- The preserved bundle keeps its visible runtime artifacts under `output/` so the sanitized sample input, flattened CSV output, and archived source copies stay together during local inspection.

Run commands (explicit selected-job mode):

```powershell
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/xml-nested-to-csv-tag-validation/job-config-proof-flatMapped.yaml" spring-boot:run
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/xml-nested-to-csv-tag-validation/job-config-proof-xmlNative.yaml" spring-boot:run
```


