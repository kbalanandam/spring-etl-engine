import test from "node:test";
import assert from "node:assert/strict";

import { extractStepNamesFromRawYaml } from "../../../src/main/resources/static/operator/job-step-names.js";

test("extractStepNamesFromRawYaml returns empty list for blank and non-string inputs", () => {
  assert.deepEqual(extractStepNamesFromRawYaml(""), []);
  assert.deepEqual(extractStepNamesFromRawYaml("   \n  \n"), []);
  assert.deepEqual(extractStepNamesFromRawYaml(null), []);
});

test("extractStepNamesFromRawYaml parses quoted and unquoted step names", () => {
  const yaml = [
    "name: customer-load",
    "steps:",
    "  - name: read-customers",
    "  - name: \"transform customers\"",
    "  - name: 'write-customers'",
  ].join("\n");

  assert.deepEqual(extractStepNamesFromRawYaml(yaml), [
    "read-customers",
    "transform customers",
    "write-customers",
  ]);
});

test("extractStepNamesFromRawYaml de-duplicates while preserving first occurrence order", () => {
  const yaml = [
    "steps:",
    "  - name: read",
    "  - name: transform",
    "  - name: read",
    "  - name: write",
  ].join("\n");

  assert.deepEqual(extractStepNamesFromRawYaml(yaml), ["read", "transform", "write"]);
});

test("extractStepNamesFromRawYaml stops parsing after steps block ends", () => {
  const yaml = [
    "steps:",
    "  - name: extract",
    "target:",
    "  - name: should-not-be-read",
  ].join("\n");

  assert.deepEqual(extractStepNamesFromRawYaml(yaml), ["extract"]);
});

test("extractStepNamesFromRawYaml skips comments and non-matching entries", () => {
  const yaml = [
    "steps:",
    "  # comment line",
    "  - kind: custom",
    "  - name: normalize",
    "",
    "  - id: legacy",
    "  - name: publish",
  ].join("\n");

  assert.deepEqual(extractStepNamesFromRawYaml(yaml), ["normalize", "publish"]);
});

