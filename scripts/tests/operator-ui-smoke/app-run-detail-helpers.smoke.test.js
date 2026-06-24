import test from "node:test";
import assert from "node:assert/strict";

import { createAppRunDetailHelpers } from "../../../src/main/resources/static/operator/app-run-detail-helpers.js";

class FakeElement {
  constructor(tagName, id = "") {
    this.tagName = String(tagName || "").toUpperCase();
    this.id = id;
    this.hidden = false;
    this.textContent = "";
    this.innerHTML = "";
    this.children = [];
    this._listeners = new Map();
  }

  addEventListener(type, listener) {
    if (!this._listeners.has(type)) {
      this._listeners.set(type, []);
    }
    this._listeners.get(type).push(listener);
  }

  dispatch(type, overrides = {}) {
    const listeners = this._listeners.get(type) || [];
    const event = {
      preventDefault() {},
      ...overrides,
    };
    listeners.forEach((listener) => listener(event));
  }

  appendChild(child) {
    this.children.push(child);
    return child;
  }
}

function installDom(ids) {
  const previousDocument = globalThis.document;
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

  return {
    elements,
    restore() {
      globalThis.document = previousDocument;
    },
  };
}

function createHelpers(focusSpy = () => {}) {
  return createAppRunDetailHelpers({
    coalesceRunSteps: (steps) => (Array.isArray(steps) ? steps : []),
    escapeHtml: (value) => String(value),
    focusScopedLogViewer: focusSpy,
    valueOrDash: (value) => (value === null || value === undefined || value === "" ? "-" : String(value)),
  });
}

test("run detail helpers render steps and empty state", () => {
  const dom = installDom([
    "run-detail-steps-table",
    "run-detail-steps-body",
    "run-detail-steps-empty",
  ]);
  try {
    const helpers = createHelpers();
    const table = dom.elements.get("run-detail-steps-table");
    const body = dom.elements.get("run-detail-steps-body");
    const empty = dom.elements.get("run-detail-steps-empty");

    helpers.renderRunSteps([]);
    assert.equal(table.hidden, true);
    assert.equal(empty.hidden, false);

    helpers.renderRunSteps([{ stepName: "extract", status: "COMPLETED", readCount: 1, writeCount: 1, rejectedCount: 0 }]);
    assert.equal(table.hidden, false);
    assert.equal(empty.hidden, true);
    assert.equal(body.children.length, 1);
    assert.match(body.children[0].innerHTML, /extract/);
  } finally {
    dom.restore();
  }
});

test("run detail helpers render failure/artifact/evidence sections", () => {
  const dom = installDom([
    "run-detail-failure-empty",
    "run-detail-failure-box",
    "run-detail-failure-category",
    "run-detail-failure-type",
    "run-detail-failure-message",
    "run-detail-artifacts-list",
    "run-detail-artifacts-empty",
    "run-detail-evidence-list",
    "run-detail-evidence-empty",
  ]);

  let focusCalled = 0;
  const helpers = createHelpers(() => {
    focusCalled += 1;
  });

  try {
    helpers.renderRunFailureSummary(null);
    assert.equal(dom.elements.get("run-detail-failure-box").hidden, true);
    assert.equal(dom.elements.get("run-detail-failure-empty").hidden, false);

    helpers.renderRunFailureSummary({ category: "runtime", exceptionType: "X", message: "boom" });
    assert.equal(dom.elements.get("run-detail-failure-box").hidden, false);
    assert.equal(dom.elements.get("run-detail-failure-category").textContent, "runtime");

    helpers.renderRunArtifacts([{ role: "output", path: "out.csv", recordCount: 3 }]);
    assert.equal(dom.elements.get("run-detail-artifacts-list").hidden, false);
    assert.equal(dom.elements.get("run-detail-artifacts-list").children.length, 1);

    helpers.renderRunEvidenceLinks([{ type: "log-file", href: "/logs/x.log", label: "Scenario log" }]);
    const evidenceList = dom.elements.get("run-detail-evidence-list");
    assert.equal(evidenceList.hidden, false);
    assert.equal(evidenceList.children.length, 1);
    const firstItem = evidenceList.children[0];
    assert.equal(firstItem.children[0].tagName, "A");
    firstItem.children[0].dispatch("click");
    assert.equal(focusCalled, 1);
  } finally {
    dom.restore();
  }
});

