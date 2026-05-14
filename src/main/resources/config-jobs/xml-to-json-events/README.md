# xml-to-json-events

Preserved explicit job scenario for converting one flat XML event feed into JSON output.

## Purpose

Use this bundle as the baseline XML-to-JSON example when you want:

- one flat XML source
- one flat JSON target
- one simple processor mapping with no transforms or validation rules
- generated model packages derived automatically from `job-config.yaml` instead of authored `packageName` values

## Files

- `job-config.yaml` - selects the runnable source, target, and processor files
- `source-config.yaml` - declares the XML source runtime contract, including explicit `DirectXml` behavior and the reference to the structural definition
- `definitions/events-source-model.yaml` - authoritative scenario-local structural XML source contract used by job-scoped generation
- `schemas/events.xsd` - optional XSD contract used by source validation before reading begins
- `rejects/` - optional scenario-local destination if you enable `validation.onFailure: rejectFile`
- `target-config.yaml` - declares the JSON output artifact
- `processor-config.yaml` - maps XML source fields directly to JSON target fields
- `input/events.xml` - preserved XML sample payload
- `output/` - scenario-local runtime folder for the final JSON artifact

## Package derivation note

This bundle intentionally omits `packageName` in both `source-config.yaml` and `target-config.yaml`.

For explicit `job-config.yaml` runs, the runtime and job-scoped generation path derive those packages as:

- `com.etl.generated.job.xmltojsonevents.source`
- `com.etl.generated.job.xmltojsonevents.target`

Keep explicit `packageName` only when you need a compatibility override.

## Expected behavior

- the XML reader streams each `<Event>` record from `input/events.xml`
- the source config uses the standard flat XML pattern: explicit `flatteningStrategy: DirectXml` plus `modelDefinitionPath`
- `definitions/events-source-model.yaml` is the single source of truth for the XML record structure in this bundle
- source validation first checks `input/events.xml` against `schemas/events.xsd`
- if you opt into `validation.onFailure: rejectFile`, the runtime moves invalid XML files into `rejects/` before failing the run
- the default processor maps each source field directly into the JSON target model
- the JSON writer publishes one staged output file only after the step completes successfully
- the final artifact is a JSON array written to `output/events-output.json`

## Run example

```powershell
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/xml-to-json-events/job-config.yaml" -DskipTests package
 java "-Detl.config.job=src/main/resources/config-jobs/xml-to-json-events/job-config.yaml" -jar target/spring-etl-engine-1.6.0.jar
```

