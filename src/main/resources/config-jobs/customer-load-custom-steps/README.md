# customer-load-custom-steps

Preserved A7 phase-1 runnable bundle that interleaves custom and standard steps in one explicit ordered plan.

## What this proves

- `kind: custom` steps execute in authored order with standard steps
- `custom.type: auditNoop` binds through `DynamicCustomStepFactory`
- runtime remains one flat Spring Batch plan while emitting custom-step evidence

## Run (Windows)

```powershell
Set-Location "C:\\spring-etl-engine"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\job-runner.ps1 -Action both -JobConfigPath src/main/resources/config-jobs/customer-load-custom-steps/job-config.yaml
```

## Expected evidence

- Startup plan events: `logs/startup/startup.log`
- Run/step events: `logs/<yyyy-MM-dd>/customer-load-custom-steps.log`
- Output file: `src/main/resources/config-jobs/customer-load-custom-steps/output/customers-custom-steps.xml`

Expected run-step sequence in run logs:

1. `stepName=run-start-audit` (`stepKind=custom`)
2. `stepName=customers-step` (`source=Customers target=Customers`)
3. `stepName=run-finish-audit` (`stepKind=custom`)

