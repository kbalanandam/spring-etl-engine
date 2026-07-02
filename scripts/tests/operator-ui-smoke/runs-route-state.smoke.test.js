import test from "node:test";
import assert from "node:assert/strict";

import {
  buildRunsRouteHash,
  formatDateForInput,
  normalizeIsoDate,
  normalizeSupportedFilter,
} from "../../../src/main/resources/static/operator/runs-route-state.js";

test("normalizeSupportedFilter returns empty for unsupported values", () => {
  const supported = new Set(["explicit-job", "demo-fallback"]);
  assert.equal(normalizeSupportedFilter("", supported), "");
  assert.equal(normalizeSupportedFilter("legacy-mode", supported), "");
  assert.equal(normalizeSupportedFilter("explicit-job", supported), "explicit-job");
});

test("normalizeIsoDate accepts valid ISO date and rejects malformed values", () => {
  assert.equal(normalizeIsoDate("2026-06-23"), "2026-06-23");
  assert.equal(normalizeIsoDate("2026-13-23"), "");
  assert.equal(normalizeIsoDate("2026-02-30"), "");
  assert.equal(normalizeIsoDate("2026/06/23"), "");
});

test("formatDateForInput serializes local date components", () => {
  const date = new Date(2026, 5, 23); // local June 23, 2026
  assert.equal(formatDateForInput(date), "2026-06-23");
});

test("buildRunsRouteHash includes only populated filters and always includes sort fields", () => {
  const hash = buildRunsRouteHash({
    filterText: " customer ",
    selectedJobKey: "customer-load",
    runModeFilter: "explicit-job",
    recoveryPolicyFilter: "rerun-from-start",
    startDate: "2026-06-23",
    timezone: "UTC",
    sortKey: "startTime",
    sortDirection: "desc",
    page: 3,
    pageSize: 20,
  }, {
    defaultPageSize: 10,
  });

  assert.match(hash, /^#\/runs\?/);
  assert.match(hash, /f=customer/);
  assert.match(hash, /job=customer-load/);
  assert.match(hash, /runMode=explicit-job/);
  assert.match(hash, /recoveryPolicy=rerun-from-start/);
  assert.match(hash, /startDate=2026-06-23/);
  assert.match(hash, /timezone=UTC/);
  assert.match(hash, /sort=startTime/);
  assert.match(hash, /dir=desc/);
  assert.match(hash, /page=3/);
  assert.match(hash, /pageSize=20/);
});

