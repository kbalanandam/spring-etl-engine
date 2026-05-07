# XML model spike scenario
This folder preserves the first XML model-generation spike for the next architecture direction.
It is not a full runtime scenario for the current 1.4.x engine.
Instead, it provides standalone XML structural model definitions that are consumed by
`XmlStructureClassGenerator` in tests.
Included inputs:
- `simple-source-model.yaml`
- `simple-target-model.yaml`
- `nested-source-model.yaml`
- `nested-target-model.yaml`
- `simple-sample.xml`
- `nested-sample.xml`
Purpose of the spike:
- prove build-time style XML class generation from config
- prove flat XML parity
- prove one nested XML structure case
- keep flattening and business semantics out of the shared XML utility
