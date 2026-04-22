# department-load

Business scenario for loading only department data.

## Flow

- source: CSV `Department`
- target: XML `Departments`
- processor: default field-to-field mapping

## Files

- `job-config.yaml`
- `source-config.yaml`
- `target-config.yaml`
- `processor-config.yaml`

## Run example

Set `etl.config.job` to this scenario's `job-config.yaml`.

