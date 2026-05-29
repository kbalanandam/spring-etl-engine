# customer-load-skip-policy-category

Preserved explicit-job baseline for B1 category-first skip-policy config on the flat `CSV -> XML` runtime path.

## Flow

- source: CSV `Customers`
- target: XML `Customers`
- processor: default field-to-field mapping
- skip policy: step-level `skipPolicy` with `skippableCategories: [runtime]`

## Files

- `job-config.yaml` - explicit selected scenario and ordered step list with category-first skip policy
- `source-config.yaml` - CSV source contract for `Customers`
- `target-config.yaml` - flat XML target contract for `Customers`
- `processor-config.yaml` - shared processor field mappings

## Notes

- this bundle is education-focused: it shows category-first skip policy shape without requiring framework exception class names
- the default preserved sample input is clean, so skip counts may remain zero during normal runs
- legacy `skippableExceptions` remains compatibility-only and is intentionally not used in this bundle

## Run example

Generate the job-scoped XML classes first, then run the selected scenario:

```powershell
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/customer-load-skip-policy-category/job-config.yaml" process-classes
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/customer-load-skip-policy-category/job-config.yaml" spring-boot:run
```

