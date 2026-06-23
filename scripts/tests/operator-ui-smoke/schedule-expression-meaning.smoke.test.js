import test from "node:test";
import assert from "node:assert/strict";

import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

async function loadDescribeScheduleExpression() {
  const appPath = resolve(process.cwd(), "src/main/resources/static/operator/app.js");
  const source = await readFile(appPath, "utf8");

  const startToken = "function describeScheduleExpression(expressionValue) {";
  const endToken = "\nfunction updateScheduleEditorExpressionValidation()";
  const startIndex = source.indexOf(startToken);
  const endIndex = source.indexOf(endToken, startIndex);

  assert.notEqual(startIndex, -1, "Expected describeScheduleExpression() in operator app.");
  assert.notEqual(endIndex, -1, "Expected updateScheduleEditorExpressionValidation() after describeScheduleExpression().");

  const functionSource = source.slice(startIndex, endIndex).trim();
  return new Function(`${functionSource}\nreturn describeScheduleExpression;`)();
}

test("describeScheduleExpression renders common cron schedules with friendly wording", async () => {
  const describeScheduleExpression = await loadDescribeScheduleExpression();

  assert.equal(describeScheduleExpression("* * * * *"), "Runs every minute.");
  assert.equal(describeScheduleExpression("*/1 * * * *"), "Runs every minute.");
  assert.equal(describeScheduleExpression("0 * * * * *"), "Runs every minute.");
  assert.equal(describeScheduleExpression("* * * * * *"), "Runs every second.");
  assert.equal(describeScheduleExpression("*/5 * * * *"), "Runs every 5 minute(s).");
  assert.equal(describeScheduleExpression("0 9 * * 1"), "Runs weekly on selected weekday(s) at 09:00.");
  assert.equal(describeScheduleExpression("15 10 3 1 *"), "Runs monthly on day 3 at 10:15.");
  assert.equal(describeScheduleExpression("1 2 3 4 5"), "Runs on a custom cron pattern (1 2 3 4 5).");
});

test("describeScheduleExpression avoids misleading higher-level summaries when wildcard seconds repeat within the same minute", async () => {
  const describeScheduleExpression = await loadDescribeScheduleExpression();

  assert.equal(
    describeScheduleExpression("* 0 * * * *"),
    "Runs on a custom cron pattern (* 0 * * * *).",
  );
  assert.equal(
    describeScheduleExpression("* 30 8 * * *"),
    "Runs on a custom cron pattern (* 30 8 * * *).",
  );
});

