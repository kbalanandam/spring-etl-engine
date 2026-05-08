# Private Jobs

Use `private-jobs/` for **private deployable job bundles** that must not be committed to GitHub.

This folder is intentionally structured as a repo-root sibling of `src/` so real runtime bundles stay separate from the checked-in preserved reference bundles under `src/main/resources/config-jobs/`.

## What belongs here

- real customer or partner job bundles
- environment-specific JDBC or filesystem paths
- private production-like testing bundles
- private input, output, reject, and archive paths
- any values that should stay outside committed docs/examples

## What does not belong here

- preserved examples for docs, smoke verification, or regression reference
- sanitized sample data that should stay visible in the repository
- baseline demo fallback YAML under `src/main/resources/`

Keep those checked-in reference bundles under `src/main/resources/config-jobs/`.

## Bundle layout

Prefer a grouped layout where the first folder is a private collection you can purge in one delete operation and the second folder is the runnable job bundle:

```text
private-jobs/
  acme-prod/
    partner-orders-daily/
      config/
        job-config.yaml
        source-config.yaml
        target-config.yaml
        processor-config.yaml
      input/
      output/
      reject/
      archive/
```

Use the collection folder for the boundary you may want to purge later, for example:

- one customer or partner
- one project
- one environment-specific workspace
- one business domain or intake stream

Relative paths inside `job-config.yaml` resolve from the job-config file's folder, so when private bundles place YAML under `config/`, sibling runtime folders are typically referenced as `../input`, `../output`, and `../definitions` from those config files.

Direct single-level bundles such as `private-jobs/partner-orders/` still work, but grouped collections are preferred for new private bundles.

## Run example

```powershell
Set-Location 'C:\spring-etl-engine'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=private-jobs/partner-orders/config/job-config.yaml" spring-boot:run
```

Grouped collection example:

```powershell
Set-Location 'C:\spring-etl-engine'
mvn --no-transfer-progress -DskipTests "-Dspring-boot.run.jvmArguments=-Detl.config.job=private-jobs/acme-prod/partner-orders-daily/config/job-config.yaml" spring-boot:run
```

## Promotion workflow

Use this simple flow:

1. prototype safely from a preserved reference bundle under `src/main/resources/config-jobs/`
2. copy the bundle into `private-jobs/<collection>/<real-job-name>/`
3. replace sample paths and placeholder values with environment-specific settings
4. keep real inputs and generated outputs only in the ignored private bundle
5. delete the whole collection folder when that private workspace should be purged
6. if a new config contract is discovered, update docs plus one preserved reference bundle separately

## Git behavior

The repository tracks this `README.md` so the folder exists for all contributors.

All other contents under `private-jobs/` are ignored by Git.

