import test from "node:test";
import assert from "node:assert/strict";

import {
  formatScheduleStatus,
  formatScheduleTriggerOriginToken,
  formatTriggerOriginToken,
  getScheduleControlState,
  pickNewestSchedule,
  selectScheduleForJobDetail,
} from "../../../src/main/resources/static/operator/schedule-ui-helpers.js";

test("pickNewestSchedule prefers the most recently updated schedule", () => {
  const selected = pickNewestSchedule([
    { scheduleId: "older", updatedAt: "2026-06-20T10:00:00" },
    { scheduleId: "newer", updatedAt: "2026-06-21T10:00:00" },
  ]);

  assert.equal(selected.scheduleId, "newer");
});

test("selectScheduleForJobDetail prefers requested schedule id when present", () => {
  const schedules = [
    { scheduleId: "alpha", updatedAt: "2026-06-21T10:00:00" },
    { scheduleId: "beta", updatedAt: "2026-06-20T10:00:00" },
  ];

  assert.equal(selectScheduleForJobDetail(schedules, "beta").scheduleId, "beta");
  assert.equal(selectScheduleForJobDetail(schedules, "missing").scheduleId, "alpha");
});

test("getScheduleControlState and formatScheduleStatus reflect enabled and paused combinations", () => {
  assert.deepEqual(getScheduleControlState({ enabled: false, paused: true }), {
    enabled: false,
    paused: false,
    statusLabel: "Disabled",
    enableDisableAction: "enable",
    enableDisableLabel: "Enable",
    pauseResumeAction: null,
    pauseResumeLabel: "Pause",
    detailPauseResumeLabel: "Pause schedule",
    pauseResumeDisabled: true,
  });

  assert.equal(formatScheduleStatus({ enabled: true, paused: false }), "Active");
  assert.equal(formatScheduleStatus({ enabled: true, paused: true }), "Paused");
});

test("trigger origin helpers normalize unknown and recognized values", () => {
  assert.equal(formatTriggerOriginToken("schedule"), "SCHEDULE");
  assert.equal(formatTriggerOriginToken("event"), "EVENT");
  assert.equal(formatTriggerOriginToken("manual"), "MANUAL");
  assert.equal(formatTriggerOriginToken("unexpected"), "MANUAL");

  assert.equal(formatScheduleTriggerOriginToken("event"), "EVENT");
  assert.equal(formatScheduleTriggerOriginToken("manual"), "MANUAL");
  assert.equal(formatScheduleTriggerOriginToken("unexpected"), "SCHEDULE");
});

