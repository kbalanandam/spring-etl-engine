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
  assert.match(source, /Optional native scheduler controls only\./);
});

test("operator app wires schedule lookup and pause\/resume actions", async () => {
  const appPath = resolve(process.cwd(), "src/main/resources/static/operator/app.js");
  const source = await readFile(appPath, "utf8");

  assert.match(source, /DEFAULT_SCHEDULE_LOOKUP_LIMIT\s*=\s*200/);
  assert.match(source, /\/api\/v1\/schedules\?limit=\$\{DEFAULT_SCHEDULE_LOOKUP_LIMIT\}/);
  assert.match(source, /No native schedule is configured for this job\./);
  assert.match(source, /normalizedAction !== "pause" && normalizedAction !== "resume"/);
  assert.match(source, /\/api\/v1\/schedules\/\$\{encodeURIComponent\(normalizedScheduleId\)\}:\$\{normalizedAction\}/);
  assert.match(source, /Schedule action already in progress\. Please wait for the current response\./);
});


