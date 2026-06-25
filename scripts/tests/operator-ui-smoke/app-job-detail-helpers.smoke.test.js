import test from "node:test";
import assert from "node:assert/strict";

import { createAppJobDetailHelpers } from "../../../src/main/resources/static/operator/app-job-detail-helpers.js";

class FakeElement {
  constructor(tagName, id = "") {
    this.tagName = String(tagName || "").toUpperCase();
    this.id = id;
    this.hidden = false;
    this.textContent = "";
    this.innerHTML = "";
    this.children = [];
    this.title = "";
    const classValues = new Set();
    this.classList = {
      add: (...tokens) => tokens.forEach((token) => classValues.add(String(token))),
      contains: (token) => classValues.has(String(token)),
    };
  }

  appendChild(child) {
    this.children.push(child);
    return child;
  }
}

function installDom(ids) {
  const previousDocument = globalThis.document;
  const previousUrlSearchParams = globalThis.URLSearchParams;
  const elements = new Map();
  ids.forEach((id) => {
    elements.set(id, new FakeElement("div", id));
  });

  globalThis.document = {
    getElementById(id) {
      return elements.get(id) || null;
    },
    createElement(tagName) {
      return new FakeElement(tagName);
    },
    createTextNode(text) {
      return { nodeType: 3, textContent: String(text) };
    },
  };
  globalThis.URLSearchParams = URLSearchParams;

  return {
    elements,
    restore() {
      globalThis.document = previousDocument;
      globalThis.URLSearchParams = previousUrlSearchParams;
    },
  };
}

function createHelpers() {
  return createAppJobDetailHelpers({
    valueOrDash: (value) => (value === null || value === undefined || value === "" ? "-" : String(value)),
    formatTriggerOriginToken: (value) => {
      const normalized = String(value || "").trim().toUpperCase();
      return normalized === "EVENT" ? "EVENT" : normalized === "SCHEDULE" ? "SCHEDULE" : "MANUAL";
    },
  });
}

test("job detail helpers render empty trigger evidence state", () => {
  const dom = installDom([
    "job-detail-trigger-events-state",
    "job-detail-trigger-events-list",
  ]);

  try {
    const helpers = createHelpers();
    const state = dom.elements.get("job-detail-trigger-events-state");
    const list = dom.elements.get("job-detail-trigger-events-list");

    helpers.renderJobTriggerEvents([], "customer-load", {});

    assert.equal(state.textContent, "No recent trigger events found for this job.");
    assert.equal(list.hidden, true);
  } finally {
    dom.restore();
  }
});

test("job detail helpers render trigger evidence with launched run link and message title", () => {
  const dom = installDom([
    "job-detail-trigger-events-state",
    "job-detail-trigger-events-list",
  ]);

  try {
    const helpers = createHelpers();
    const state = dom.elements.get("job-detail-trigger-events-state");
    const list = dom.elements.get("job-detail-trigger-events-list");

    helpers.renderJobTriggerEvents([
      {
        requestedAt: "2026-06-24T09:15:00Z",
        triggerOrigin: "MANUAL",
        decisionStatus: "ACCEPTED",
        reason: "manual_operator_request",
        requestedBy: "operator-ui",
        triggerEventId: "te-123",
        launchedRunId: "42",
        message: "Accepted trigger",
      },
    ], "customer-load", { from: "schedule", scheduleId: "sch-1", scheduleListQuery: "sort=scheduleKey" });

    assert.equal(state.textContent, "Showing 1 recent trigger event(s).");
    assert.equal(list.hidden, false);
    assert.equal(list.children.length, 1);
    const item = list.children[0];
    assert.equal(item.title, "Accepted trigger");
    const decisionChip = item.children[0];
    assert.equal(decisionChip.tagName, "SPAN");
    assert.equal(decisionChip.textContent, "ACCEPTED");
    assert.equal(decisionChip.className, "decision-chip");
    assert.equal(decisionChip.classList.contains("decision-chip-success"), true);
    assert.match(item.children[2].textContent, /origin=MANUAL/);
    const runLink = item.children[3];
    assert.equal(runLink.tagName, "A");
    assert.equal(runLink.textContent, "42");
    assert.match(runLink.href, /#\/runs\/42\?from=job&job=customer-load&scheduleId=sch-1&scheduleListQuery=sort%3DscheduleKey/);
  } finally {
    dom.restore();
  }
});



