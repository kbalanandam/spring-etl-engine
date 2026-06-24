import test from "node:test";
import assert from "node:assert/strict";

import {
  applyJobsItems,
  pruneObjectKeys,
  reconcileJobsScopedCaches,
} from "../../../src/main/resources/static/operator/jobs-scoped-state.js";

test("pruneObjectKeys removes keys outside the valid set", () => {
  const source = {
    "customer-load": { preview: true },
    "orders-load": { preview: true },
  };

  pruneObjectKeys(source, new Set(["customer-load"]));
  assert.deepEqual(source, { "customer-load": { preview: true } });
});

test("reconcileJobsScopedCaches clears stale expanded key and prunes scoped caches", () => {
  const jobsState = {
    expandedJobKey: "orders-load",
    jobStepPreviewByJobKey: {
      "customer-load": { steps: 1 },
      "orders-load": { steps: 2 },
    },
    stepNamesByJobKey: {
      "customer-load": ["read"],
      "orders-load": ["read", "write"],
    },
  };
  const inFlightStepNamesByJobKey = {
    "customer-load": true,
    "orders-load": true,
  };

  reconcileJobsScopedCaches({
    jobsState,
    inFlightStepNamesByJobKey,
    validJobKeys: new Set(["customer-load"]),
  });

  assert.equal(jobsState.expandedJobKey, "");
  assert.deepEqual(jobsState.jobStepPreviewByJobKey, { "customer-load": { steps: 1 } });
  assert.deepEqual(jobsState.stepNamesByJobKey, { "customer-load": ["read"] });
  assert.deepEqual(inFlightStepNamesByJobKey, { "customer-load": true });
});

test("applyJobsItems normalizes non-array input and preserves valid expanded key", () => {
  const jobsState = {
    items: [{ jobKey: "old-job" }],
    expandedJobKey: "customer-load",
    jobStepPreviewByJobKey: {
      "customer-load": { steps: 1 },
      stale: { steps: 2 },
    },
    stepNamesByJobKey: {
      "customer-load": ["extract"],
      stale: ["obsolete"],
    },
  };
  const inFlightStepNamesByJobKey = {
    "customer-load": true,
    stale: true,
  };

  const jobs = applyJobsItems({
    items: [
      { jobKey: "customer-load", displayName: "Customer Load" },
      { jobKey: "orders-load", displayName: "Orders Load" },
    ],
    jobsState,
    inFlightStepNamesByJobKey,
  });

  assert.deepEqual(jobs, [
    { jobKey: "customer-load", displayName: "Customer Load" },
    { jobKey: "orders-load", displayName: "Orders Load" },
  ]);
  assert.equal(jobsState.expandedJobKey, "customer-load");
  assert.deepEqual(jobsState.items, jobs);
  assert.deepEqual(jobsState.jobStepPreviewByJobKey, { "customer-load": { steps: 1 } });
  assert.deepEqual(jobsState.stepNamesByJobKey, { "customer-load": ["extract"] });
  assert.deepEqual(inFlightStepNamesByJobKey, { "customer-load": true });
});

test("applyJobsItems returns empty array when state is missing", () => {
  assert.deepEqual(applyJobsItems({ items: [{ jobKey: "customer-load" }] }), []);
});

