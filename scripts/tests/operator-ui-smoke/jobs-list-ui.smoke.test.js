import test from "node:test";
import assert from "node:assert/strict";

import { createJobsListUi } from "../../../src/main/resources/static/operator/jobs-list-ui.js";
import { installDom } from "./dom-shim.js";

const IDS = [
  "jobs-filter-input",
  "jobs-sort-select",
  "jobs-sort-dir-btn",
  "jobs-page-size-select",
  "jobs-page-prev-btn",
  "jobs-page-next-btn",
  "jobs-page-status",
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
      page: 1,
      pageSize: 10,
      sortKey: "jobKey",
      sortDirection: "asc",
    };

    const ui = createJobsListUi({
      getState: () => state,
      syncRouteHash: () => {},
      getRouteSuffix: () => "?f=customer&page=1&pageSize=10&sort=displayName&dir=desc",
      escapeHtml,
    });

    ui.applyRouteState({
      filterText: "customer",
      page: 1,
      pageSize: 10,
      sortKey: "displayName",
      sortDirection: "desc",
    });
    ui.renderTable();

    assert.equal(elements.get("jobs-filter-input").value, "customer");
    assert.equal(elements.get("jobs-sort-select").value, "displayName");
    assert.equal(elements.get("jobs-sort-dir-btn").textContent, "Desc");
    assert.equal(elements.get("jobs-page-size-select").value, "10");
    assert.equal(elements.get("jobs-page-status").textContent, "Page 1 of 1");
    assert.equal(elements.get("jobs-body").children.length, 1);
    assert.equal(elements.get("jobs-table").hidden, false);

    const row = elements.get("jobs-body").children[0];
    assert.match(row.innerHTML, /href="#\/jobs\/customer-load\?f=customer&page=1&pageSize=10&sort=displayName&dir=desc"/);
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
      page: 1,
      pageSize: 10,
      sortKey: "jobKey",
      sortDirection: "asc",
    };
    const syncCalls = [];

    const ui = createJobsListUi({
      getState: () => state,
      syncRouteHash: (routeKey) => syncCalls.push(routeKey),
      getRouteSuffix: () => "",
      escapeHtml,
    });

    ui.initializeControls();

    const filter = elements.get("jobs-filter-input");
    const sort = elements.get("jobs-sort-select");
    const direction = elements.get("jobs-sort-dir-btn");
    const pageSize = elements.get("jobs-page-size-select");

    filter.value = "alp";
    filter.dispatch("input");
    sort.value = "displayName";
    sort.dispatch("change");
    direction.dispatch("click");
    pageSize.value = "15";
    pageSize.dispatch("change");

    assert.equal(state.filterText, "alp");
    assert.equal(state.sortKey, "displayName");
    assert.equal(state.sortDirection, "desc");
    assert.equal(state.page, 1);
    assert.equal(state.pageSize, 15);
    assert.deepEqual(syncCalls, ["jobs", "jobs", "jobs", "jobs"]);
    assert.equal(elements.get("jobs-body").children.length, 1);
  } finally {
    restore();
  }
});

test("jobs pagination limits visible rows and navigates pages", () => {
  const { elements, restore } = installDom(IDS);
  try {
    const state = {
      items: [
        { jobKey: "job-1", displayName: "Job 1", readinessStatus: "READY" },
        { jobKey: "job-2", displayName: "Job 2", readinessStatus: "READY" },
        { jobKey: "job-3", displayName: "Job 3", readinessStatus: "READY" },
      ],
      filterText: "",
      page: 1,
      pageSize: 2,
      sortKey: "jobKey",
      sortDirection: "asc",
    };
    const syncCalls = [];

    const ui = createJobsListUi({
      getState: () => state,
      syncRouteHash: (routeKey) => syncCalls.push(routeKey),
      getRouteSuffix: () => "?page=1&pageSize=2&sort=jobKey&dir=asc",
      escapeHtml,
    });

    ui.initializeControls();
    ui.renderTable();

    assert.equal(elements.get("jobs-body").children.length, 2);
    assert.equal(elements.get("jobs-page-status").textContent, "Page 1 of 2");
    assert.equal(elements.get("jobs-page-prev-btn").disabled, true);
    assert.equal(elements.get("jobs-page-next-btn").disabled, false);

    elements.get("jobs-page-next-btn").dispatch("click");

    assert.equal(state.page, 2);
    assert.equal(elements.get("jobs-body").children.length, 1);
    assert.equal(elements.get("jobs-page-status").textContent, "Page 2 of 2");
    assert.equal(elements.get("jobs-page-prev-btn").disabled, false);
    assert.equal(elements.get("jobs-page-next-btn").disabled, true);
    assert.deepEqual(syncCalls, ["jobs"]);
  } finally {
    restore();
  }
});

