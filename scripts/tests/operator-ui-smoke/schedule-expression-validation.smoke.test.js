import test from "node:test";
import assert from "node:assert/strict";

import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

async function loadValidateScheduleExpression() {
  const appPath = resolve(process.cwd(), "src/main/resources/static/operator/app.js");
  const source = await readFile(appPath, "utf8");

  const startToken = "function validateScheduleExpression(expressionValue) {";
  const endToken = "\nfunction describeScheduleExpression(expressionValue) {";
  const startIndex = source.indexOf(startToken);
  const endIndex = source.indexOf(endToken, startIndex);

  assert.notEqual(startIndex, -1, "Expected validateScheduleExpression() in operator app.");
  assert.notEqual(endIndex, -1, "Expected describeScheduleExpression() after validateScheduleExpression().");

  const functionSource = source.slice(startIndex, endIndex).trim();
  return new Function(`${functionSource}\nreturn validateScheduleExpression;`)();
}

test("validateScheduleExpression rejects invalid weekday literals", async () => {
  const validateScheduleExpression = await loadValidateScheduleExpression();

  assert.deepEqual(validateScheduleExpression("* * * * d"), {
    valid: false,
    message: "Expression contains unsupported cron value.",
  });
});

test("validateScheduleExpression keeps valid weekday schedules accepted", async () => {
  const validateScheduleExpression = await loadValidateScheduleExpression();

  assert.deepEqual(validateScheduleExpression("* * * * 1"), {
    valid: true,
    message: "",
  });
  assert.deepEqual(validateScheduleExpression("* * * * MON"), {
    valid: true,
    message: "",
  });
});

