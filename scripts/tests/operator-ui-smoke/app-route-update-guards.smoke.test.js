import test from "node:test";
import assert from "node:assert/strict";

import { createAppRouteUpdateGuards } from "../../../src/main/resources/static/operator/app-route-update-guards.js";

test("route update guards require latest request id", () => {
  const guards = createAppRouteUpdateGuards({
    loadRequestTracker: { jobs: 4 },
    getCurrentRouteState: () => ({ key: "jobs" }),
  });

  assert.equal(guards.isLatestRequest("jobs", 4), true);
  assert.equal(guards.isLatestRequest("jobs", 3), false);
  assert.equal(guards.shouldApplyRouteScopedUpdate("jobs", 4), true);
  assert.equal(guards.shouldApplyRouteScopedUpdate("jobs", 3), false);
});

test("route update guards match scoped job detail and run detail routes", () => {
  const guards = createAppRouteUpdateGuards({
    loadRequestTracker: { jobDetail: 2, runDetail: 7 },
    getCurrentRouteState: () => ({ key: "jobDetail", jobKey: "customer-load" }),
  });

  assert.equal(guards.isActiveRoute("jobDetail", "customer-load"), true);
  assert.equal(guards.isActiveRoute("jobDetail", "orders-load"), false);
  assert.equal(guards.shouldApplyRouteScopedUpdate("jobDetail", 2, "customer-load"), true);
  assert.equal(guards.shouldApplyRouteScopedUpdate("jobDetail", 2, "orders-load"), false);

  const runGuards = createAppRouteUpdateGuards({
    loadRequestTracker: { runDetail: 7 },
    getCurrentRouteState: () => ({ key: "runDetail", jobExecutionId: "42" }),
  });

  assert.equal(runGuards.isActiveRoute("runDetail", "42"), true);
  assert.equal(runGuards.isActiveRoute("runDetail", "77"), false);
  assert.equal(runGuards.shouldApplyRouteScopedUpdate("runDetail", 7, "42"), true);
});

test("route update guards allow non-scoped list routes by key only", () => {
  const guards = createAppRouteUpdateGuards({
    loadRequestTracker: { schedules: 9 },
    getCurrentRouteState: () => ({ key: "schedules" }),
  });

  assert.equal(guards.isActiveRoute("schedules", "ignored"), true);
  assert.equal(guards.shouldApplyRouteScopedUpdate("schedules", 9, "ignored"), true);
  assert.equal(guards.shouldApplyRouteScopedUpdate("runs", 9, "ignored"), false);
});

