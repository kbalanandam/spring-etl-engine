import test from "node:test";
import assert from "node:assert/strict";
import { validateScheduleExpression } from "../../../src/main/resources/static/operator/schedule-expression.js";

test("validateScheduleExpression rejects invalid weekday literals", async () => {
  assert.deepEqual(validateScheduleExpression("* * * * d"), {
    valid: false,
    message: "Expression contains unsupported cron value.",
  });
  assert.deepEqual(validateScheduleExpression("* * * * 9"), {
    valid: false,
    message: "Expression contains unsupported cron value.",
  });
});

test("validateScheduleExpression keeps valid weekday schedules accepted", async () => {

  assert.deepEqual(validateScheduleExpression("* * * * 1"), {
    valid: true,
    message: "",
  });
  assert.deepEqual(validateScheduleExpression("* * * * MON"), {
    valid: true,
    message: "",
  });
});


