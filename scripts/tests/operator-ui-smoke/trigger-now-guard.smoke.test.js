import test from "node:test";
import assert from "node:assert/strict";

import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

test("operator app trigger-now flow includes in-flight and cooldown guards", async () => {
  const appPath = resolve(process.cwd(), "src/main/resources/static/operator/app.js");
  const source = await readFile(appPath, "utf8");

  assert.match(source, /TRIGGER_NOW_DUPLICATE_WINDOW_MS\s*=\s*5\s*\*\s*1000/);
  assert.match(source, /triggerNowRequestState\s*=\s*\{/);
  assert.match(source, /Trigger request already in progress\. Please wait for the current response\./);
  assert.match(source, /Trigger already accepted recently\. Please wait a few seconds before retrying\./);
  assert.match(source, /decisionStatus === "DUPLICATE_SUPPRESSED"/);
});

