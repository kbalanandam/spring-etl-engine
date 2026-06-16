import test from "node:test";
import assert from "node:assert/strict";

import { createRunsListUi } from "../../../src/main/resources/static/operator/runs-list-ui.js";
import { installDom } from "./dom-shim.js";

const IDS = [
  "runs-filter-input",
  "runs-start-date-input",
  "runs-timezone-select",
  "runs-job-select",
  "runs-run-mode-select",
  "runs-recovery-policy-select",
  "runs-instance-select",
  "runs-sort-select",
  "runs-sort-dir-btn",
  "runs-state",
  "runs-table",
  "runs-body",
];

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function populateRunsSelectOptions(elements) {
  const jobSelect = elements.get("runs-job-select");
  const runModeSelect = elements.get("runs-run-mode-select");
  const recoveryPolicySelect = elements.get("runs-recovery-policy-select");

  jobSelect.innerHTML = "";
  const allJobs = globalThis.document.createElement("option");
  allJobs.value = "";
  jobSelect.appendChild(allJobs);
  const customerJob = globalThis.document.createElement("option");
  customerJob.value = "customer-load";
  jobSelect.appendChild(customerJob);

  runModeSelect.innerHTML = "";
  const allModes = globalThis.document.createElement("option");
  allModes.value = "";
  runModeSelect.appendChild(allModes);
  const explicitMode = globalThis.document.createElement("option");
  explicitMode.value = "explicit-job";
  runModeSelect.appendChild(explicitMode);
  const demoMode = globalThis.document.createElement("option");
  demoMode.value = "demo-fallback";
  runModeSelect.appendChild(demoMode);

  recoveryPolicySelect.innerHTML = "";
  const allPolicies = globalThis.document.createElement("option");
  allPolicies.value = "";
  recoveryPolicySelect.appendChild(allPolicies);
  const rerun = globalThis.document.createElement("option");
  rerun.value = "rerun-from-start";
  recoveryPolicySelect.appendChild(rerun);
}

test("runs list applies route state and renders sorted table", () => {
  const { elements, restore } = installDom(IDS);
  try {
    const state = {
      items: [
        {
          scenario: "customer-load",
          status: "COMPLETED",
          triggerOrigin: "SCHEDULE",
          runMode: "explicit-job",
          recoveryPolicy: "rerun-from-start",
          startTime: "2026-06-03T10:00:00",
          durationSeconds: 25,
          jobExecutionId: 11,
        },
        {
          scenario: "orders-load",
          status: "FAILED",
          triggerOrigin: "MANUAL",
          runMode: "demo-fallback",
          recoveryPolicy: "rerun-from-start",
          startTime: "2026-06-03T11:00:00",
          durationSeconds: 12,
          jobExecutionId: 12,
        },
      ],
      loaded: true,
      filterText: "",
      selectedJobKey: "",
      runModeFilter: "",
      recoveryPolicyFilter: "",
      startDate: "",
      timezone: "",
      browserTimezone: "UTC",
      sortKey: "startTime",
      sortDirection: "desc",
    };

    const ui = createRunsListUi({
      getState: () => state,
      syncRouteHash: () => {},
      renderJobOptions: () => populateRunsSelectOptions(elements),
      formatDateForInput: () => "2026-06-03",
      escapeHtml,
    });

    ui.applyRouteState({
      filterText: "load",
      selectedJobKey: "customer-load",
      runModeFilter: "explicit-job",
      recoveryPolicyFilter: "rerun-from-start",
      startDate: "2026-06-03",
      timezone: "UTC",
      sortKey: "jobExecutionId",
      sortDirection: "asc",
    });
    ui.renderTable();

    assert.equal(elements.get("runs-filter-input").value, "load");
    assert.equal(elements.get("runs-sort-select").value, "jobExecutionId");
    assert.equal(elements.get("runs-sort-dir-btn").textContent, "Asc");
    assert.equal(elements.get("runs-body").children.length, 2);

    const firstRow = elements.get("runs-body").children[0];
    assert.match(firstRow.innerHTML, /SCHEDULE/);
    assert.match(firstRow.innerHTML, /explicit-job/);
    assert.match(firstRow.innerHTML, /rerun-from-start/);
    firstRow.dispatch("click");
    assert.equal(globalThis.location.hash, "#/runs/11");
    assert.equal(elements.get("runs-table").hidden, false);
    assert.equal(elements.get("runs-instance-select").disabled, false);
  } finally {
    restore();
  }
});

test("runs controls update state and request route sync", () => {
  const { elements, restore } = installDom(IDS);
  try {
    const state = {
      items: [{ scenario: "customer-load", status: "COMPLETED", triggerOrigin: "MANUAL", runMode: "explicit-job", recoveryPolicy: "rerun-from-start", startTime: "2026-06-03T10:00:00", durationSeconds: 25, jobExecutionId: 11 }],
      loaded: true,
      filterText: "",
      selectedJobKey: "",
      runModeFilter: "",
      recoveryPolicyFilter: "",
      startDate: "2026-06-03",
      timezone: "UTC",
      browserTimezone: "UTC",
      sortKey: "startTime",
      sortDirection: "desc",
    };
    const syncCalls = [];

    const ui = createRunsListUi({
      getState: () => state,
      syncRouteHash: (routeKey) => syncCalls.push(routeKey),
      renderJobOptions: () => populateRunsSelectOptions(elements),
      formatDateForInput: () => "2026-06-03",
      escapeHtml,
    });

    ui.initializeControls();

    const startDate = elements.get("runs-start-date-input");
    const timezone = elements.get("runs-timezone-select");
    const filter = elements.get("runs-filter-input");
    const runMode = elements.get("runs-run-mode-select");
    const recoveryPolicy = elements.get("runs-recovery-policy-select");
    const sort = elements.get("runs-sort-select");
    const direction = elements.get("runs-sort-dir-btn");
    const instance = elements.get("runs-instance-select");

    startDate.value = "2026-06-01";
    startDate.dispatch("change");
    timezone.value = "Europe/London";
    timezone.dispatch("change");
    filter.value = "customer";
    filter.dispatch("input");
    runMode.value = "explicit-job";
    runMode.dispatch("change");
    recoveryPolicy.value = "rerun-from-start";
    recoveryPolicy.dispatch("change");
    sort.value = "status";
    sort.dispatch("change");
    direction.dispatch("click");
    instance.value = "11";
    instance.dispatch("change");

    assert.equal(state.startDate, "2026-06-01");
    assert.equal(state.timezone, "Europe/London");
    assert.equal(state.filterText, "customer");
    assert.equal(state.runModeFilter, "explicit-job");
    assert.equal(state.recoveryPolicyFilter, "rerun-from-start");
    assert.equal(state.sortKey, "status");
    assert.equal(state.sortDirection, "asc");
    assert.equal(globalThis.location.hash, "#/runs/11");
    assert.deepEqual(syncCalls, ["runs", "runs", "runs", "runs", "runs", "runs", "runs"]);

    filter.value = "explicit-job";
    filter.dispatch("input");
    assert.equal(elements.get("runs-body").children.length, 1);
  } finally {
    restore();
  }
});

test("runs list clears stale route filters that are not present in UI options", () => {
  const { elements, restore } = installDom(IDS);
  try {
    const state = {
      items: [],
      loaded: false,
      filterText: "",
      selectedJobKey: "",
      runModeFilter: "",
      recoveryPolicyFilter: "",
      startDate: "",
      timezone: "UTC",
      browserTimezone: "UTC",
      sortKey: "startTime",
      sortDirection: "desc",
    };

    const ui = createRunsListUi({
      getState: () => state,
      syncRouteHash: () => {},
      renderJobOptions: () => populateRunsSelectOptions(elements),
      formatDateForInput: () => "2026-06-16",
      escapeHtml,
    });

    ui.applyRouteState({
      selectedJobKey: "missing-job",
      runModeFilter: "legacy-mode",
      recoveryPolicyFilter: "legacy-policy",
      startDate: "202606-16",
      timezone: "Asia/Kolkata",
      sortKey: "startTime",
      sortDirection: "desc",
    });

    assert.equal(state.selectedJobKey, "");
    assert.equal(state.runModeFilter, "");
    assert.equal(state.recoveryPolicyFilter, "");
    assert.equal(state.startDate, "2026-06-16");
    assert.equal(state.timezone, "Asia/Kolkata");
  } finally {
    restore();
  }
});

