import test from "node:test";
import assert from "node:assert/strict";
import {
  buildMissingCompanionWarning,
  buildJobConfigDocuments,
  normalizeDocumentKey,
  pickJobConfigDocument,
} from "../../../src/main/resources/static/operator/job-config-files.js";

test("buildJobConfigDocuments returns read-only config files and missing companion payloads", () => {
  const model = buildJobConfigDocuments({
    rawYaml: [
      "name: sample-job",
      "sourceConfigPath: source-config.yaml",
      "targetConfigPath: target-config.yaml",
      "processorConfigPath: processor-config.yaml",
      "steps:",
      "  - name: customers-step",
      "    source: Customers",
      "    target: CustomersOut",
    ].join("\n"),
    sourceRawYaml: [
      "sources:",
      "  - format: csv",
      "    sourceName: Customers",
      "    filePath: input/customers.csv",
    ].join("\n"),
    targetRawYaml: [
      "targets:",
      "  - format: xml",
      "    targetName: CustomersOut",
      "    filePath: output/customers.xml",
    ].join("\n"),
  });

  assert.equal(model.documents.length, 4);
  assert.equal(model.documents[0].label, "job-config.yaml");
  assert.equal(model.documents[0].path, "job-config.yaml");
  assert.equal(model.documents[1].path, "source-config.yaml");
  assert.equal(model.documents[2].path, "target-config.yaml");
  assert.equal(model.documents[3].path, "processor-config.yaml");
  assert.deepEqual(model.missingCompanionDocuments, ["processor-config.yaml"]);
});

test("buildJobConfigDocuments reports no missing companion payloads when all files are present", () => {
  const model = buildJobConfigDocuments({
    rawYaml: [
      "name: sample-job",
      "sourceConfigPath: source-config.yaml",
      "targetConfigPath: target-config.yaml",
      "processorConfigPath: processor-config.yaml",
    ].join("\n"),
    sourceRawYaml: "sources:\n  - sourceName: Customers\n",
    targetRawYaml: "targets:\n  - targetName: CustomersOut\n",
    processorRawYaml: "mappings:\n  - source: Customers\n    target: CustomersOut\n",
  });

  assert.deepEqual(model.missingCompanionDocuments, []);
});

test("normalizeDocumentKey accepts supported tab keys and falls back to job", () => {
  assert.equal(normalizeDocumentKey("source"), "source");
  assert.equal(normalizeDocumentKey("TARGET"), "target");
  assert.equal(normalizeDocumentKey("processor"), "processor");
  assert.equal(normalizeDocumentKey(""), "job");
  assert.equal(normalizeDocumentKey("unknown"), "job");
});

test("pickJobConfigDocument selects requested tab or defaults to job tab", () => {
  const model = buildJobConfigDocuments({
    rawYaml: "name: sample-job",
    sourceRawYaml: "sources:\n  - sourceName: Customers\n",
    targetRawYaml: "targets:\n  - targetName: CustomersOut\n",
    processorRawYaml: "mappings:\n  - source: Customers\n    target: CustomersOut\n",
  });

  assert.equal(pickJobConfigDocument(model.documents, "source")?.key, "source");
  assert.equal(pickJobConfigDocument(model.documents, "processor")?.key, "processor");
  assert.equal(pickJobConfigDocument(model.documents, "unsupported")?.key, "job");
});

test("buildMissingCompanionWarning returns message only for selected missing companion files", () => {
  const model = buildJobConfigDocuments({
    rawYaml: "name: sample-job\nsourceConfigPath: source-config.yaml\ntargetConfigPath: target-config.yaml\nprocessorConfigPath: processor-config.yaml",
    targetRawYaml: "targets:\n  - targetName: CustomersOut\n",
  });
  const source = pickJobConfigDocument(model.documents, "source");
  const target = pickJobConfigDocument(model.documents, "target");
  const job = pickJobConfigDocument(model.documents, "job");

  assert.match(buildMissingCompanionWarning(source, model.missingCompanionDocuments), /Missing:/);
  assert.equal(buildMissingCompanionWarning(target, model.missingCompanionDocuments), "");
  assert.equal(buildMissingCompanionWarning(job, model.missingCompanionDocuments), "");
});

