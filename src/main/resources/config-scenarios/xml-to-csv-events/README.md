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
- This scenario remains a runnable baseline XML-to-CSV flow.
- The first validation/reject/archive slice is now shipped for CSV-backed scenarios, but this preserved XML scenario does not enable those optional config fields and still maps/writes records as-is.

## Run example

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-scenarios/xml-to-csv-events/job-config.yaml" spring-boot:run
```

