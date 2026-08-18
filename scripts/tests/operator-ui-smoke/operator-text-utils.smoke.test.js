import test from "node:test";
import assert from "node:assert/strict";

import {
  categorizeTriggerFailure,
  escapeHtml,
  formatDateTimeSeconds,
  formatJobDetailRecentRunLabel,
  resolveBrowserTimeZoneLabel,
  valueOrDash,
} from "../../../src/main/resources/static/operator/operator-text-utils.js";

test("valueOrDash maps empty-like values to dash", () => {
  assert.equal(valueOrDash(null), "-");
  assert.equal(valueOrDash(undefined), "-");
  assert.equal(valueOrDash(""), "-");
  assert.equal(valueOrDash(0), "0");
});

test("escapeHtml encodes unsafe characters", () => {
  assert.equal(escapeHtml("<tag a=\"1\">'x' & y</tag>"), "&lt;tag a=&quot;1&quot;&gt;&#39;x&#39; &amp; y&lt;/tag&gt;");
});

test("formatJobDetailRecentRunLabel composes run summary with safe fallbacks", () => {
  assert.equal(
    formatJobDetailRecentRunLabel({ jobExecutionId: 11, status: "COMPLETED", startTime: "2026-06-23T10:00:00Z" }),
    "runId=11 | status=COMPLETED | start=2026-06-23T10:00:00Z"
  );
  assert.equal(formatJobDetailRecentRunLabel({}), "runId=- | status=- | start=-");
});

test("formatDateTimeSeconds trims to local seconds and preserves local date-time inputs", () => {
  assert.equal(formatDateTimeSeconds("2026-07-20T10:02:44.845"), "2026-07-20 10:02:44");
  assert.equal(formatDateTimeSeconds(""), "-");
});

test("resolveBrowserTimeZoneLabel returns a non-empty label", () => {
  const label = resolveBrowserTimeZoneLabel();
  assert.equal(typeof label, "string");
  assert.notEqual(label.trim(), "");
});

test("categorizeTriggerFailure classifies status buckets", () => {
  assert.equal(categorizeTriggerFailure(404), "config");
  assert.equal(categorizeTriggerFailure(409), "config");
  assert.equal(categorizeTriggerFailure(400), "validation");
  assert.equal(categorizeTriggerFailure(499), "validation");
  assert.equal(categorizeTriggerFailure(500), "runtime");
});

