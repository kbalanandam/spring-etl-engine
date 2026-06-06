import test from "node:test";
import assert from "node:assert/strict";

import { createRunLogViewer } from "../../../src/main/resources/static/operator/run-log-viewer.js";
import { installDom } from "./dom-shim.js";

const IDS = [
  "run-detail-log-state",
  "run-detail-log-list",
  "run-detail-log-empty",
  "run-detail-log-controls",
  "run-detail-log-search",
  "run-detail-log-structured-only",
  "run-detail-log-compact",
  "run-detail-log-copy-visible",
];

test("run log viewer shows empty state when scoped log endpoint returns 404", async () => {
  const { elements, restore } = installDom(IDS);
  const previousFetch = globalThis.fetch;

  globalThis.fetch = async () => ({
    status: 404,
    ok: false,
  });

  try {
    const viewer = createRunLogViewer({
      valueOrDash: (value) => (value === null || value === undefined || value === "" ? "-" : String(value)),
      escapeHtml: (value) => String(value),
    });

    await viewer.load(3);

    assert.equal(elements.get("run-detail-log-state").className, "state");
    assert.equal(elements.get("run-detail-log-state").textContent, "No run-scoped log lines returned.");
    assert.equal(elements.get("run-detail-log-empty").hidden, false);
    assert.equal(elements.get("run-detail-log-list").hidden, true);
    assert.equal(elements.get("run-detail-log-controls").hidden, true);
  } finally {
    globalThis.fetch = previousFetch;
    restore();
  }
});

