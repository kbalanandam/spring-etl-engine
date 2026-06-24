import test from "node:test";
import assert from "node:assert/strict";

import { createAppRouteHelpers } from "../../../src/main/resources/static/operator/app-route-helpers.js";

function createViewState() {
  return {
    jobs: {
      filterText: "customer",
      sortKey: "displayName",
      sortDirection: "desc",
      page: 2,
      pageSize: 15,
    },
    schedules: {
      filterText: "nightly",
      sortKey: "status",
      sortDirection: "desc",
      page: 1,
      pageSize: 20,
      selectedScheduleId: "sched-1",
    },
    runs: {
      filterText: "failed",
      selectedJobKey: "customer-load",
      runModeFilter: "explicit-job",
      recoveryPolicyFilter: "rerun-from-start",
      startDate: "2026-06-23",
      timezone: "UTC",
      sortKey: "startTime",
      sortDirection: "desc",
    },
  };
}

function createHelpers(viewState) {
  return createAppRouteHelpers({
    viewState,
    sortKeys: {
      jobs: ["jobKey", "displayName", "readinessStatus"],
      schedules: ["scheduleKey", "selectedJobKey", "status", "nextDueAt"],
      runs: ["startTime", "jobExecutionId", "scenario", "status", "triggerOrigin", "runMode", "recoveryPolicy"],
    },
    jobsPageSizeOptions: [8, 10, 15, 20],
  });
}

test("app route helpers build list hashes and query suffixes", () => {
  const viewState = createViewState();
  const helpers = createHelpers(viewState);

  const jobsHash = helpers.getJobsRouteHash();
  assert.match(jobsHash, /^#\/jobs\?/);
  assert.match(jobsHash, /f=customer/);
  assert.match(jobsHash, /sort=displayName/);

  const schedulesHash = helpers.getSchedulesRouteHash();
  assert.match(schedulesHash, /^#\/schedules\?/);
  assert.match(schedulesHash, /f=nightly/);
  assert.match(schedulesHash, /scheduleId=sched-1/);

  const runsHash = helpers.getRunsRouteHash();
  assert.match(runsHash, /^#\/runs\?/);
  assert.match(runsHash, /job=customer-load/);

  const suffix = helpers.getSchedulesRouteQuerySuffix();
  assert.match(suffix, /^\?/);
  assert.doesNotMatch(suffix, /scheduleId=/);
});

test("app route helpers normalize values and build selected schedule hashes", () => {
  const viewState = createViewState();
  const helpers = createHelpers(viewState);

  assert.equal(helpers.normalizeSortKey("schedules", "status", "scheduleKey"), "status");
  assert.equal(helpers.normalizeSortKey("schedules", "unknown", "scheduleKey"), "scheduleKey");
  assert.equal(helpers.normalizeDirection("desc", "asc"), "desc");
  assert.equal(helpers.normalizeDirection("sideways", "asc"), "asc");
  assert.equal(helpers.normalizePositiveInteger("4", 1), 4);
  assert.equal(helpers.normalizePositiveInteger("0", 1), 1);

  const listHash = helpers.buildSchedulesListHash("sched-42", "f=nightly");
  assert.match(listHash, /^#\/schedules\?/);
  assert.match(listHash, /scheduleId=sched-42/);

  const editHash = helpers.buildSchedulesEditHash("sched-42", "f=nightly");
  assert.match(editHash, /editScheduleId=sched-42/);

  const detailHash = helpers.buildScheduleDetailHash("sched-42", "f=nightly");
  assert.equal(detailHash, "#/schedules/sched-42?f=nightly");
});

test("app route helpers syncListRouteHash updates location hash", () => {
  const viewState = createViewState();
  const helpers = createHelpers(viewState);
  const originalLocation = globalThis.location;

  try {
    globalThis.location = { hash: "#/jobs" };
    helpers.syncListRouteHash("schedules");
    assert.match(globalThis.location.hash, /^#\/schedules\?/);

    const first = globalThis.location.hash;
    helpers.syncListRouteHash("schedules");
    assert.equal(globalThis.location.hash, first);
  } finally {
    globalThis.location = originalLocation;
  }
});

