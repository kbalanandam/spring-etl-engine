# cust-dept-load

Business scenario for loading both customer and department data in one run.

## Flow

- source: CSV `Customers` and `Department`
- target: XML `Customers` and `Departments`
- processor: default field-to-field mapping for both entities

## Files

- `job-config.yaml`
- `source-config.yaml`
- `target-config.yaml`
- `processor-config.yaml`

## Notes

This scenario keeps both entity flows in one explicit config set, which fits the current single job-config selection design.

