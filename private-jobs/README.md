# Private Jobs

Use `private-jobs/` as a **developer-local placeholder workspace** for private deployable job bundles that must not be committed to GitHub.

This folder is intentionally structured as a repo-root sibling of `src/` so real runtime bundles stay separate from the checked-in preserved reference bundles under `src/main/resources/config-jobs/`.

Think of `private-jobs/` as the place where each developer or deployment team copies a preserved example, adapts it to their private environment, and keeps that adapted bundle out of source control.

## What belongs here

- copies of preserved reference bundles that developers are adapting for local/private use
- real customer or partner job bundles
- environment-specific JDBC or filesystem paths
- private production-like testing bundles
- private input, output, reject, and archive paths
- any values that should stay outside committed docs/examples

## What does not belong here

- committed repository examples that other contributors should rely on directly
- preserved examples for docs, smoke verification, or regression reference
- sanitized sample data that should stay visible in the repository
- baseline demo fallback YAML under `src/main/resources/`

Keep those checked-in reference bundles under `src/main/resources/config-jobs/`.

If you need a starting point, copy one of those preserved bundles into `private-jobs/` and then replace the sample settings with your own private values.

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

## Cleanup helper

When you want to purge one private bundle and its generated job-scoped model artifacts together, use the generalized cleanup helper:

```powershell
Set-Location 'C:\spring-etl-engine'
powershell.exe -ExecutionPolicy Bypass -File .\scripts\remove-job-bundle.ps1 -JobConfigPath .\private-jobs\local-verification\your-job\config\job-config.yaml -WhatIf
```

Remove `-WhatIf` after reviewing the planned deletes.

The helper removes:

- the private bundle rooted at the selected `job-config.yaml`
- matching generated sources under `target/generated-sources/etl/`
- matching compiled generated classes under `target/classes/` and `target/test-classes/`

For preserved or other non-private bundles, the generalized helper requires explicit `-DeleteSharedBundle` opt-in before it will remove the bundle root.

## Git behavior

The repository tracks this `README.md` so the folder exists for all contributors and the intended pattern stays discoverable.

All other contents under `private-jobs/` are ignored by Git.

That means the expected repository state is:

- this guidance file may be committed
- developer-created bundles, copied configs, private data, logs, outputs, rejects, archives, and SQL files under `private-jobs/` should remain local-only

