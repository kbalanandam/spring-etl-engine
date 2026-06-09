import test from "node:test";
import assert from "node:assert/strict";

import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

test("operator job detail includes native schedule panel markup", async () => {
  const htmlPath = resolve(process.cwd(), "src/main/resources/static/operator/index.html");
  const source = await readFile(htmlPath, "utf8");

  assert.match(source, /id="job-detail-schedule-state"/);
  assert.match(source, /id="job-detail-schedule-summary"/);
  assert.match(source, /id="job-detail-schedule-action-btn"/);
  assert.match(source, /Native scheduler controls only\./);
});

test("operator includes schedule workbench and trigger evidence markup", async () => {
  const htmlPath = resolve(process.cwd(), "src/main/resources/static/operator/index.html");
  const source = await readFile(htmlPath, "utf8");

  assert.match(source, /id="tab-schedules"/);
  assert.match(source, /id="view-schedules"/);
  assert.match(source, /id="view-schedule-detail"/);
  assert.match(source, /id="schedule-detail-triggers-list"/);
  assert.match(source, /Trigger origin/);
});

test("operator app wires schedule lookup and pause\/resume actions", async () => {
  const appPath = resolve(process.cwd(), "src/main/resources/static/operator/app.js");
  const source = await readFile(appPath, "utf8");

  assert.match(source, /DEFAULT_SCHEDULE_LOOKUP_LIMIT\s*=\s*200/);
  assert.match(source, /\/api\/v1\/schedules\?limit=\$\{DEFAULT_SCHEDULE_LOOKUP_LIMIT\}/);
  assert.match(source, /No native schedule is configured for this job\./);
  assert.match(source, /function selectScheduleForJobDetail\(schedules, preferredScheduleId\)/);
  assert.match(source, /Multiple native schedules are configured for this job \(\$\{matches\.length\} found\)\. Managing \$\{valueOrDash\(selectedSchedule\.scheduleKey\)\} in this panel\./);
  assert.match(source, /viewState\.schedules\.selectedScheduleId = String\(schedule\?\.scheduleId \|\| ""\)\.trim\(\);/);
  assert.match(source, /normalizedAction !== "pause" && normalizedAction !== "resume"/);
  assert.match(source, /\/api\/v1\/schedules\/\$\{encodeURIComponent\(normalizedScheduleId\)\}:\$\{normalizedAction\}/);
  assert.match(source, /Schedule action already in progress\. Please wait for the current response\./);
  assert.match(source, /\/api\/v1\/schedules\/\$\{encodeURIComponent\(scheduleId\)\}\/trigger-events\?limit=20/);
  assert.match(source, /function loadScheduleDetail\(routeState\)/);
  assert.match(source, /#\/schedules\/\$\{encodeURIComponent\(scheduleId\)\}/);
  assert.match(source, /Back to schedules/);
  assert.match(source, /function formatTriggerOriginToken\(token\)/);
  assert.match(source, /function getScheduleControlState\(schedule\)/);
  assert.match(source, /const scheduleControlState = getScheduleControlState\(selectedSchedule\);/);
  assert.match(source, /const scheduleControlState = getScheduleControlState\(schedule\);/);
  assert.match(source, /toggleButton\.textContent = scheduleControlState\.toggleLabel/);
  assert.match(source, /scheduleActionButton\.textContent = scheduleControlState\.detailToggleLabel/);
});


