# customer-load-reject-quarantine

Preserved runnable proof for processor-level duplicate rejection with additive quarantine publication.

## Purpose

This bundle proves that when `rejectHandling.quarantinePath` is configured:

- rejected records are published to `output/rejects/`
- the finalized reject artifact is also published to `output/quarantine/`
- accepted rows are still written to `output/customers.xml`

## Run

```powershell
Set-Location 'C:\spring-etl-engine'
powershell.exe -ExecutionPolicy Bypass -File '.\scripts\job-runner.ps1' -Action both -JobConfigPath src/main/resources/config-jobs/customer-load-reject-quarantine/job-config.yaml
```

## Expected evidence

- `STEP_EVENT event=step_finished ... status=COMPLETED ... rejectedCount=1`
- `rejectOutputPath=...\output\rejects\customers-step-rejects.csv`
- reject file also present under `output/quarantine/customers-step-rejects.csv`

