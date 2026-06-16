# customer-load-custom-step-fail-finalizer

Preserved A7b runnable bundle that proves custom-step FAIL mapping and bounded failure-finalizer invocation in one explicit ordered run.

## What this proves

- `custom.onResult` maps provider result code `verify_failed` to `FAIL`
- mapped `FAIL` keeps custom-step semantics explicit and fails the run through one runtime path
- failed job triggers provider finalizer hook (`CustomStepProvider.createFailureFinalizer(...)`)

## Run (Windows)

```powershell
Set-Location "C:\spring-etl-engine"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\job-runner.ps1 -Action both -JobConfigPath src/main/resources/config-jobs/customer-load-custom-step-fail-finalizer/job-config.yaml
```

## Expected evidence

- startup plan evidence: `logs/startup/startup.log`
- run evidence: `logs/<yyyy-MM-dd>/customer-load-custom-step-fail-finalizer.log`
- step output before terminal failure: `src/main/resources/config-jobs/customer-load-custom-step-fail-finalizer/output/customers-custom-step-fail-finalizer.xml`

Look for these run log signals:

1. `STEP_EVENT event=custom_step_outcome_mapped ... stepName=run-finish-audit ... mappedAction=FAIL ... providerResult=verify_failed`
2. `RUN_EVENT event=custom_step_failure_finalized ... stepName=run-start-audit`
3. `RUN_EVENT event=custom_step_failure_finalized ... stepName=run-finish-audit`
4. `RUN_SUMMARY ... status=FAILED`

`STOP` remains a separate controlled-stop path: it stops downstream execution but does not invoke failure finalizers.

