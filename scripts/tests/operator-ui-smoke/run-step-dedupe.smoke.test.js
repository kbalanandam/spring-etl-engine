import test from "node:test";
import assert from "node:assert/strict";

import { coalesceRunSteps } from "../../../src/main/resources/static/operator/run-step-dedupe.js";

test("coalesceRunSteps keeps one row per step and prefers populated counts", () => {
  const result = coalesceRunSteps([
    {
      stepName: "customers-step",
      status: "COMPLETED",
      readCount: 3,
      writeCount: 3,
      rejectedCount: 0,
    },
    {
      stepName: "customers-step",
      status: "COMPLETED",
      readCount: "-",
      writeCount: "-",
      rejectedCount: "-",
    },
  ]);

  assert.equal(result.length, 1);
  assert.equal(result[0].stepName, "customers-step");
  assert.equal(result[0].readCount, 3);
  assert.equal(result[0].writeCount, 3);
  assert.equal(result[0].rejectedCount, 0);
});

