# customer-load-skip-policy-category-unclassified

Preserved proof bundle for category-first skip policy where one malformed CSV row is skipped with runtime-category policy plus compatibility exception matching.

## Why this bundle exists

- demonstrates non-zero `skipCount` evidence
- keeps category-first policy while also listing compatibility exception classes used by the read path
- uses one intentionally bad CSV row (`BAD_ID`) with `skipLimit: 1`

## Expected result

- step status: `COMPLETED`
- `skipCount=1`
- written records: `2`

## Run example

```powershell
mvn --no-transfer-progress -Pxml-generation "-Detl.xml.generation.jobConfig=src/main/resources/config-jobs/customer-load-skip-policy-category-unclassified/job-config.yaml" process-classes
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.main-class=com.etl.ETLEngineApplication" "-Dspring-boot.run.jvmArguments=-Detl.config.job=src/main/resources/config-jobs/customer-load-skip-policy-category-unclassified/job-config.yaml" spring-boot:run
```


