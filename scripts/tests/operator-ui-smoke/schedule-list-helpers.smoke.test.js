import test from "node:test";
import assert from "node:assert/strict";

import {
  filterSchedulesItems,
  sortSchedulesItems,
} from "../../../src/main/resources/static/operator/schedule-list-helpers.js";

function formatScheduleStatus(schedule) {
  if (!schedule?.enabled) {
    return "Disabled";
  }
  return schedule?.paused ? "Paused" : "Active";
}

function normalizeSortKey(routeKey, value, fallback) {
  const allowed = routeKey === "schedules"
    ? new Set(["scheduleKey", "selectedJobKey", "status", "nextDueAt"])
    : new Set();
  return allowed.has(value) ? value : fallback;
}

function normalizeDirection(value, fallback) {
  return value === "desc" || value === "asc" ? value : fallback;
}

test("filterSchedulesItems returns all items for empty filter and matches key/job/status", () => {
  const items = [
    { scheduleKey: "daily-customers", selectedJobKey: "customer-load", enabled: true, paused: false },
    { scheduleKey: "nightly-orders", selectedJobKey: "orders-load", enabled: false, paused: false },
  ];

  assert.equal(filterSchedulesItems(items, "", formatScheduleStatus).length, 2);
  assert.deepEqual(filterSchedulesItems(items, "customer", formatScheduleStatus), [items[0]]);
  assert.deepEqual(filterSchedulesItems(items, "disabled", formatScheduleStatus), [items[1]]);
});

test("sortSchedulesItems sorts by schedule key and selected job key", () => {
  const items = [
    { scheduleKey: "zeta", selectedJobKey: "b-job", enabled: true, paused: false },
    { scheduleKey: "alpha", selectedJobKey: "c-job", enabled: true, paused: false },
    { scheduleKey: "beta", selectedJobKey: "a-job", enabled: true, paused: false },
  ];

  assert.deepEqual(
    sortSchedulesItems(items, "scheduleKey", "asc", { normalizeSortKey, normalizeDirection, formatScheduleStatus }).map((item) => item.scheduleKey),
    ["alpha", "beta", "zeta"]
  );

  assert.deepEqual(
    sortSchedulesItems(items, "selectedJobKey", "asc", { normalizeSortKey, normalizeDirection, formatScheduleStatus }).map((item) => item.selectedJobKey),
    ["a-job", "b-job", "c-job"]
  );
});

test("sortSchedulesItems sorts by next due and status and falls back on invalid sort key", () => {
  const items = [
    { scheduleKey: "beta", selectedJobKey: "job-b", nextDueAt: "2026-06-24T00:00:00Z", enabled: false, paused: false },
    { scheduleKey: "alpha", selectedJobKey: "job-a", nextDueAt: "2026-06-23T00:00:00Z", enabled: true, paused: true },
    { scheduleKey: "gamma", selectedJobKey: "job-c", nextDueAt: "2026-06-25T00:00:00Z", enabled: true, paused: false },
  ];

  assert.deepEqual(
    sortSchedulesItems(items, "nextDueAt", "asc", { normalizeSortKey, normalizeDirection, formatScheduleStatus }).map((item) => item.scheduleKey),
    ["alpha", "beta", "gamma"]
  );

  assert.deepEqual(
    sortSchedulesItems(items, "status", "asc", { normalizeSortKey, normalizeDirection, formatScheduleStatus }).map((item) => item.scheduleKey),
    ["gamma", "beta", "alpha"]
  );

  assert.deepEqual(
    sortSchedulesItems(items, "unexpected", "desc", { normalizeSortKey, normalizeDirection, formatScheduleStatus }).map((item) => item.scheduleKey),
    ["gamma", "beta", "alpha"]
  );
});

