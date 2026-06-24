import test from "node:test";
import assert from "node:assert/strict";
import { describeScheduleExpression } from "../../../src/main/resources/static/operator/schedule-expression.js";

test("describeScheduleExpression renders common cron schedules with friendly wording", async () => {
  assert.equal(describeScheduleExpression("* * * * *"), "Runs every minute.");
  assert.equal(describeScheduleExpression("*/1 * * * *"), "Runs every minute.");
  assert.equal(describeScheduleExpression("0 * * * * *"), "Runs every minute.");
  assert.equal(describeScheduleExpression("* * * * * *"), "Runs every second.");
  assert.equal(describeScheduleExpression("*/5 * * * *"), "Runs every 5 minute(s).");
  assert.equal(describeScheduleExpression("*/15 1 * * *"), "Runs every 15 minute(s) during hour 01 each day.");
  assert.equal(describeScheduleExpression("* * * * 1"), "Runs every minute on selected weekday(s).");
  assert.equal(describeScheduleExpression("*/5 * * * 1"), "Runs every 5 minute(s) on selected weekday(s).");
  assert.equal(describeScheduleExpression("0 9 * * 1"), "Runs weekly on selected weekday(s) at 09:00.");
  assert.equal(describeScheduleExpression("15 10 3 * *"), "Runs monthly on day 3 at 10:15.");
  assert.equal(describeScheduleExpression("1 2 3 4 5"), "Runs on a custom cron pattern (1 2 3 4 5).");
});

test("describeScheduleExpression avoids misleading higher-level summaries when wildcard seconds repeat within the same minute", async () => {
  assert.equal(
    describeScheduleExpression("* 0 * * * *"),
    "Runs on a custom cron pattern (* 0 * * * *).",
  );
  assert.equal(
    describeScheduleExpression("* 30 8 * * *"),
    "Runs on a custom cron pattern (* 30 8 * * *).",
  );
});

test("describeScheduleExpression does not return success meaning for invalid cron values", async () => {

  assert.equal(describeScheduleExpression("* * * * d"), "");
  assert.equal(describeScheduleExpression("* * * * 9"), "");
});







