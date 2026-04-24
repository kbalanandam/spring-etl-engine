# relational-to-relational

Business scenario for relational source to relational target flow.

## Flow

- source: relational `Customers`
- target: relational `CustomersSql`
- processor: default field-to-field mapping

## Files

- `job-config.yaml`
- `source-config.yaml`
- `target-config.yaml`
- `processor-config.yaml`

## Notes

- Replace placeholder SQL Server connection values before running this scenario.
- `countQuery` is included so the job can make a predictable chunk/tasklet decision for the source.
- `fetchSize` and `batchSize` are included as the phase-1 large-volume tuning knobs.

## Run example

```powershell
Set-Location '<repo-root>'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-scenarios/relational-to-relational/job-config.yaml" spring-boot:run
```

