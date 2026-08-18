import test from "node:test";
import assert from "node:assert/strict";

import { createAppRunsBridgeHelpers } from "../../../src/main/resources/static/operator/app-runs-bridge-helpers.js";

function createViewState() {
  return {
    jobs: {
      loaded: false,
      items: [],
    },
    runs: {
      cache: {
        byFilter: {},
        order: [],
      },
    },
  };
}

test("runs bridge delegates normalization and date formatting wrappers", () => {
  const helpers = createAppRunsBridgeHelpers({
    viewState: createViewState(),
    inFlightStepNamesByJobKey: {},
    applyJobsItemsValue: () => [],
    fetchJobsForRunsScopeValue: async () => [],
    fetchRunsForFiltersValue: async () => [],
    normalizeSupportedFilterValue: (value) => String(value || "").toUpperCase(),
    normalizeIsoDateValue: (value) => (value === "ok" ? "ok" : ""),
    formatDateForInputValue: () => "2026-06-23",
  });

  assert.equal(helpers.normalizeSupportedFilter("abc", new Set(["abc"])), "ABC");
  assert.equal(helpers.normalizeIsoDate("ok"), "ok");
  assert.equal(helpers.formatDateForInput(new Date()), "2026-06-23");
});

test("runs bridge fetchJobsForRunsScope applies jobs and marks jobs loaded", async () => {
  const viewState = createViewState();
  const inFlight = {};
  const applied = [];

  const helpers = createAppRunsBridgeHelpers({
    viewState,
    inFlightStepNamesByJobKey: inFlight,
    applyJobsItemsValue: (options) => {
      applied.push(options);
      viewState.jobs.items = options.items;
      return options.items;
    },
    fetchJobsForRunsScopeValue: async () => [{ jobKey: "customer-load" }],
    fetchRunsForFiltersValue: async () => [],
    normalizeSupportedFilterValue: (value) => value,
    normalizeIsoDateValue: (value) => value,
    formatDateForInputValue: () => "",
  });

  const jobs = await helpers.fetchJobsForRunsScope();
  assert.deepEqual(jobs, [{ jobKey: "customer-load" }]);
  assert.equal(viewState.jobs.loaded, true);
  assert.equal(applied.length, 1);
  assert.equal(applied[0].jobsState, viewState.jobs);
  assert.equal(applied[0].inFlightStepNamesByJobKey, inFlight);
});

test("runs bridge fetchRunsForFilters forwards cache-backed options", async () => {
  const viewState = createViewState();
  let forwarded = null;

  const helpers = createAppRunsBridgeHelpers({
    viewState,
    inFlightStepNamesByJobKey: {},
    applyJobsItemsValue: () => [],
    fetchJobsForRunsScopeValue: async () => [],
    fetchRunsForFiltersValue: async (options) => {
      forwarded = options;
      return [{ jobExecutionId: 11 }];
    },
    normalizeSupportedFilterValue: (value) => value,
    normalizeIsoDateValue: (value) => value,
    formatDateForInputValue: () => "",
  });

  const runs = await helpers.fetchRunsForFilters("customer-load", "explicit-job", "rerun-from-start", "2026-06-23", "UTC");
  assert.deepEqual(runs, [{ jobExecutionId: 11 }]);
  assert.equal(forwarded.selectedJobKey, "customer-load");
  assert.equal(forwarded.cache, viewState.runs.cache);
});

