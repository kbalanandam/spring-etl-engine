import test from "node:test";
import assert from "node:assert/strict";

import { createJobsListUi } from "../../../src/main/resources/static/operator/jobs-list-ui.js";
import { installDom } from "./dom-shim.js";

const IDS = [
  "jobs-filter-input",
  "jobs-sort-select",
  "jobs-sort-dir-btn",
  "jobs-state",
  "jobs-table",
  "jobs-body",
];

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

test("jobs list applies route state and renders rows", () => {
  const { elements, restore } = installDom(IDS);
  try {
    const state = {
      items: [
        { jobKey: "customer-load", displayName: "Customer Load", readinessStatus: "READY" },
        { jobKey: "orders-load", displayName: "Orders Load", readinessStatus: "BLOCKED" },
      ],
      filterText: "",
      sortKey: "jobKey",
      sortDirection: "asc",
    };

    const ui = createJobsListUi({
      getState: () => state,
      syncRouteHash: () => {},
      escapeHtml,
    });

    ui.applyRouteState({
      filterText: "customer",
      sortKey: "displayName",
      sortDirection: "desc",
    });
    ui.renderTable();

    assert.equal(elements.get("jobs-filter-input").value, "customer");
    assert.equal(elements.get("jobs-sort-select").value, "displayName");
    assert.equal(elements.get("jobs-sort-dir-btn").textContent, "Desc");
    assert.equal(elements.get("jobs-body").children.length, 1);
    assert.equal(elements.get("jobs-table").hidden, false);

    const row = elements.get("jobs-body").children[0];
    row.dispatch("click");
    assert.equal(globalThis.location.hash, "#/jobs/customer-load");
  } finally {
    restore();
  }
});

test("jobs controls update state, sync route, and re-render", () => {
  const { elements, restore } = installDom(IDS);
  try {
    const state = {
      items: [{ jobKey: "alpha", displayName: "Alpha", readinessStatus: "READY" }],
      filterText: "",
      sortKey: "jobKey",
      sortDirection: "asc",
    };
    const syncCalls = [];

    const ui = createJobsListUi({
      getState: () => state,
      syncRouteHash: (routeKey) => syncCalls.push(routeKey),
      escapeHtml,
    });

    ui.initializeControls();

    const filter = elements.get("jobs-filter-input");
    const sort = elements.get("jobs-sort-select");
    const direction = elements.get("jobs-sort-dir-btn");

    filter.value = "alp";
    filter.dispatch("input");
    sort.value = "displayName";
    sort.dispatch("change");
    direction.dispatch("click");

    assert.equal(state.filterText, "alp");
    assert.equal(state.sortKey, "displayName");
    assert.equal(state.sortDirection, "desc");
    assert.deepEqual(syncCalls, ["jobs", "jobs", "jobs"]);
    assert.equal(elements.get("jobs-body").children.length, 1);
  } finally {
    restore();
  }
});

