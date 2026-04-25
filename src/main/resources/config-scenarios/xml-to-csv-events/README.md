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
- This scenario is a runnable baseline for the later file-ingestion hardening slice.
- In the current shipped runtime, all records are mapped and written as-is; future validation/reject/archive behavior is still a separate planned slice.

## Run example

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-scenarios/xml-to-csv-events/job-config.yaml" spring-boot:run
```

