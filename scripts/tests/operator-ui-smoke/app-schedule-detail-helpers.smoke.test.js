import test from "node:test";
import assert from "node:assert/strict";

import { createAppScheduleDetailHelpers } from "../../../src/main/resources/static/operator/app-schedule-detail-helpers.js";

class FakeElement {
  constructor(tagName, id = "") {
    this.tagName = String(tagName || "").toUpperCase();
    this.id = id;
    this.hidden = false;
    this.textContent = "";
    this.href = "";
    this.children = [];
    this.className = "";
    const classes = new Set();
    this.classList = {
      add: (...tokens) => tokens.forEach((token) => classes.add(String(token))),
      contains: (token) => classes.has(String(token)),
    };
  }

  appendChild(child) {
    this.children.push(child);
    return child;
  }
}

function installScheduleDetailDom() {
  const previousDocument = globalThis.document;
  const elements = new Map();
  const toggle = new FakeElement("button", "schedule-detail-triggers-toggle-btn");
  const panel = new FakeElement("div", "schedule-detail-triggers-panel");
  elements.set(toggle.id, toggle);
  elements.set(panel.id, panel);

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

  return {
    toggle,
    panel,
    restore() {
      globalThis.document = previousDocument;
    },
  };
}

test("schedule detail helpers toggle trigger panel expanded state", () => {
  const dom = installScheduleDetailDom();
  try {
    const helpers = createAppScheduleDetailHelpers({
      valueOrDash: (value) => (value === null || value === undefined || value === "" ? "-" : String(value)),
      formatScheduleTriggerOriginToken: (token) => String(token || "").toUpperCase() || "SCHEDULE",
    });

    helpers.setScheduleDetailTriggersExpanded(true);
    assert.equal(dom.panel.hidden, false);
    assert.equal(dom.toggle.textContent, "Hide recent triggers");

    helpers.setScheduleDetailTriggersExpanded(false);
    assert.equal(dom.panel.hidden, true);
    assert.equal(dom.toggle.textContent, "Show recent triggers");
  } finally {
    dom.restore();
  }
});

test("schedule detail helpers build trigger event line with run link and schedule query", () => {
  const dom = installScheduleDetailDom();
  try {
    const helpers = createAppScheduleDetailHelpers({
      valueOrDash: (value) => (value === null || value === undefined || value === "" ? "-" : String(value)),
      formatDateTimeSeconds: (value) => {
        const parsed = new Date(value);
        return Number.isNaN(parsed.getTime()) ? "-" : parsed.toISOString().replace("T", " ").slice(0, 19);
      },
      formatScheduleTriggerOriginToken: (token) => {
        const normalized = String(token || "").toUpperCase();
        return normalized === "EVENT" ? "EVENT" : "SCHEDULE";
      },
    });

    const line = helpers.buildScheduleTriggerEventLine(
      {
        requestedAt: "2026-06-23T10:00:00Z",
        triggerOrigin: "event",
        decisionStatus: "ACCEPTED",
        triggerEventId: "e-1",
        launchedRunId: "42",
      },
      "sched-1",
      "f=nightly"
    );

    assert.equal(line.tagName, "LI");
    assert.equal(line.className, "job-trigger-event-item");
    assert.equal(line.children.length, 4);
    assert.equal(line.children[0].tagName, "SPAN");
    assert.equal(line.children[0].className, "decision-chip");
    assert.equal(line.children[0].classList.contains("decision-chip-success"), true);
    assert.match(line.children[2].textContent, /^2026-06-23 10:00:00 \|/);
    assert.match(line.children[2].textContent, /origin=EVENT/);
    assert.equal(line.children[3].tagName, "A");
    assert.equal(line.children[3].textContent, "42");
    assert.match(line.children[3].href, /^#\/runs\/42\?/);
    assert.match(line.children[3].href, /from=schedule/);
    assert.match(line.children[3].href, /scheduleId=sched-1/);
    assert.match(line.children[3].href, /scheduleListQuery=f%3Dnightly/);
  } finally {
    dom.restore();
  }
});

