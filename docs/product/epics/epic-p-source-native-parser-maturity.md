# Epic P — Source-native parser maturity

## Summary

Epic P captures the parser-specific product track that should now be planned explicitly and then held steady around CSV/XML-first maturity. Its first responsibility is to prove that the existing Java runtime works on a few real-file business scenarios, then grow source-native parser capability where real scenarios need it while preserving the boundary that parsing stops at runtime-record creation and does not absorb processor, orchestration, or target-publication work.

## Scope

This epic is the home for work that:

- proves the existing Java runtime on preserved real-file scenarios before reopening broader parser-scope pressure
- strengthens CSV and XML source-native parsing on the active read path
- adds parser-layer strictness only for tokens, fragments, malformed-source categorization, and pre-record source validation
- proves parser maturity through preserved scenario bundles and verification evidence
- keeps parser planning intentionally focused on CSV/XML before opening new parser-family scope

This epic is **not** the place for processor transforms, business-rule validation, duplicate winner selection, scheduling/control-plane work, or target-specific write policy.

JSON source parsing is intentionally **out of scope for the active epic backlog** until a concrete source contract and preserved scenario justify opening that track.

Future native-parser adoptability also belongs here only as a **boundary-preserving parser-readiness topic**: any C/C++ or other native parser direction must remain behind the Java reader seam and start with a narrow CSV-first sidecar shape rather than a parser-centered runtime redesign.

The most important real-file proof anchors for this epic are the preserved bundles that already exist today, such as `xml-to-csv-events`, `xml-to-json-events`, `csv-to-sqlserver`, and the explicit multi-step nested XML roundtrip bundles.

## Related backlog items

- [`P1 — Freeze parser roadmap around CSV and XML source-native maturity`](../backlog-items/P1-freeze-parser-roadmap-around-csv-and-xml-maturity.md)
- [`P2 — Expand CSV parser strictness and malformed-row categorization on the read path`](../backlog-items/P2-expand-csv-parser-strictness-and-malformed-row-categorization.md)
- [`P3 — Expand XML parser maturity for namespace-aware and fragment-contract scenarios`](../backlog-items/P3-expand-xml-parser-maturity-for-namespace-and-fragment-contracts.md)
- [`P4 — Prove CSV and XML parser maturity through preserved scenarios and verification`](../backlog-items/P4-prove-csv-and-xml-parser-maturity-through-preserved-scenarios-and-verification.md)
- [`P5 — Define native parser adoptability and CSV-first sidecar integration readiness`](../backlog-items/P5-native-parser-adoptability-and-sidecar-integration-readiness.md)

## Related docs

- [`../../architecture/oneflow-file-parser-capabilities.md`](../../architecture/oneflow-file-parser-capabilities.md)
- [`../../architecture/native-parser-adoptability.md`](../../architecture/native-parser-adoptability.md)
- [`../../architecture/csv-native-parser-sidecar-protocol.md`](../../architecture/csv-native-parser-sidecar-protocol.md)
- [`../../architecture/java-native-parser-reader-adapter-contract.md`](../../architecture/java-native-parser-reader-adapter-contract.md)
- [`../../architecture/etl-product-evolution-roadmap.md`](../../architecture/etl-product-evolution-roadmap.md)
- [`../../architecture/extension-points.md`](../../architecture/extension-points.md)
- [`../../config/source/csv-source.md`](../../config/source/csv-source.md)
- [`../../config/source/xml-source.md`](../../config/source/xml-source.md)

## Maintenance note

Use [`../product-backlog.md`](../product-backlog.md) for execution-board status. Use this page for the shared parser boundary, CSV/XML-first planning stance, and explicit JSON-later scope decision that span the `P*` items.


