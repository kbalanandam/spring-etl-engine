# xml-to-csv-events

Business scenario for converting one realistic flat XML event feed into CSV output.

## Flow

- source: XML `Events`
- target: CSV `EventsCsv`
- processor: default field-to-field mapping

## Files

- `job-config.yaml`
- `source-config.yaml`
- `target-config.yaml`
- `processor-config.yaml`

## Notes

- The XML sample lives at `src/main/resources/demo-input/Events.xml`.
- The CSV artifact is written to `output/events-output.csv` inside this scenario bundle.
- This scenario remains a runnable baseline XML-to-CSV flow.
- The shared validation/reject/archive seams now exist for file-backed scenarios such as CSV and XML, but this preserved XML scenario intentionally does not enable those optional config fields and still maps/writes records as-is.

## Run example

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-scenarios/xml-to-csv-events/job-config.yaml" spring-boot:run
```

