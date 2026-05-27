# XML to CSV Events Transform Showcase

This preserved bundle demonstrates chained built-in transforms plus one ServiceLoader-discovered extension transform on the active `type: default` processor seam.

Transform chain highlights:

- `expression` builds a parseable datetime string from `eventTime`
- `zoneConvert` converts from `UTC` to `America/Chicago`
- `valueMap` normalizes partner status keys
- `partnerStatusTranslate` (from `ShowcaseProcessorExtensionProvider`) translates normalized partner keys

Run example:

```powershell
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/xml-to-csv-events-transform-showcase/job-config.yaml" spring-boot:run
```

Expected output file:

- `src/main/resources/config-jobs/xml-to-csv-events-transform-showcase/output/events-transform-showcase.csv`

Notes:

- `zoneConvert` uses `fallbackValue: systemTime` for non-parseable input values.
- `partnerStatusTranslate` is discovered through `META-INF/services/com.etl.processor.spi.ProcessorExtensionProvider`.

