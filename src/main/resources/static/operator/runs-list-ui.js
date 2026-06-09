import { labelDirection, sortItems, toggleDirection } from "./list-sort-utils.js";

export function createRunsListUi(options) {
  const getState = options.getState;
  const syncRouteHash = options.syncRouteHash;
  const renderJobOptions = options.renderJobOptions;
  const formatDateForInput = options.formatDateForInput;
  const escapeHtml = options.escapeHtml;

  function initializeControls() {
    const runsFilter = document.getElementById("runs-filter-input");
    const runsStartDate = document.getElementById("runs-start-date-input");
    const runsTimezone = document.getElementById("runs-timezone-select");
    const runsJobSelect = document.getElementById("runs-job-select");
    const runsRunModeSelect = document.getElementById("runs-run-mode-select");
    const runsRecoveryPolicySelect = document.getElementById("runs-recovery-policy-select");
    const runsInstanceSelect = document.getElementById("runs-instance-select");
    const runsSort = document.getElementById("runs-sort-select");
    const runsDirection = document.getElementById("runs-sort-dir-btn");

    renderTimezoneOptions();

    if (runsStartDate) {
      runsStartDate.addEventListener("change", (event) => {
        const state = getState();
        state.startDate = event.target.value || "";
        state.loaded = false;
        syncRouteHash("runs");
      });
    }
    if (runsTimezone) {
      runsTimezone.addEventListener("change", (event) => {
        const state = getState();
        state.timezone = event.target.value || state.browserTimezone;
        state.loaded = false;
        syncRouteHash("runs");
      });
    }

    if (runsJobSelect) {
      runsJobSelect.addEventListener("change", (event) => {
        const state = getState();
        state.selectedJobKey = event.target.value || "";
        state.loaded = false;
        syncRouteHash("runs");
      });
    }
    if (runsRunModeSelect) {
      runsRunModeSelect.addEventListener("change", (event) => {
        const state = getState();
        state.runModeFilter = event.target.value || "";
        state.loaded = false;
        syncRouteHash("runs");
      });
    }
    if (runsRecoveryPolicySelect) {
      runsRecoveryPolicySelect.addEventListener("change", (event) => {
        const state = getState();
        state.recoveryPolicyFilter = event.target.value || "";
        state.loaded = false;
        syncRouteHash("runs");
      });
    }
    if (runsInstanceSelect) {
      runsInstanceSelect.addEventListener("change", (event) => {
        const runId = event.target.value || "";
        if (!runId) {
          return;
        }
        location.hash = `#/runs/${encodeURIComponent(runId)}`;
      });
    }
    if (runsFilter) {
      runsFilter.addEventListener("input", (event) => {
        getState().filterText = event.target.value || "";
        syncRouteHash("runs");
        renderTable();
      });
    }
    if (runsSort) {
      runsSort.addEventListener("change", (event) => {
        getState().sortKey = event.target.value;
        syncRouteHash("runs");
        renderTable();
      });
    }
    if (runsDirection) {
      runsDirection.addEventListener("click", () => {
        const state = getState();
        state.sortDirection = toggleDirection(state.sortDirection);
        runsDirection.textContent = labelDirection(state.sortDirection);
        syncRouteHash("runs");
        renderTable();
      });
    }
  }

  function applyRouteState(routeState) {
    const state = getState();
    state.filterText = routeState.filterText || "";
    state.selectedJobKey = routeState.selectedJobKey || "";
    state.runModeFilter = routeState.runModeFilter || "";
    state.recoveryPolicyFilter = routeState.recoveryPolicyFilter || "";
    state.startDate = routeState.startDate || state.startDate || formatDateForInput(new Date());
    state.timezone = routeState.timezone || state.timezone || state.browserTimezone || "UTC";
    state.sortKey = routeState.sortKey || "startTime";
    state.sortDirection = routeState.sortDirection || "desc";

    renderTimezoneOptions();
    renderJobOptions();

    const startDate = document.getElementById("runs-start-date-input");
    const timezone = document.getElementById("runs-timezone-select");
    const jobSelect = document.getElementById("runs-job-select");
    const runModeSelect = document.getElementById("runs-run-mode-select");
    const recoveryPolicySelect = document.getElementById("runs-recovery-policy-select");
    const filter = document.getElementById("runs-filter-input");
    const sort = document.getElementById("runs-sort-select");
    const direction = document.getElementById("runs-sort-dir-btn");

    if (startDate) {
      startDate.value = state.startDate;
    }
    if (timezone) {
      timezone.value = state.timezone;
    }
    if (jobSelect) {
      jobSelect.value = state.selectedJobKey;
    }
    if (runModeSelect) {
      runModeSelect.value = state.runModeFilter;
    }
    if (recoveryPolicySelect) {
      recoveryPolicySelect.value = state.recoveryPolicyFilter;
    }
    if (filter) {
      filter.value = state.filterText;
    }
    if (sort) {
      sort.value = state.sortKey;
    }
    if (direction) {
      direction.textContent = labelDirection(state.sortDirection);
    }
    clearInstanceOptions();
  }

  function renderTimezoneOptions() {
    const select = document.getElementById("runs-timezone-select");
    if (!select) {
      return;
    }

    const state = getState();
    const browserTimezone = state.browserTimezone || "UTC";
    const preferred = [browserTimezone, "UTC", "America/New_York", "Europe/London", "Asia/Kolkata"];
    const uniqueTimezones = Array.from(new Set(preferred.filter(Boolean)));

    select.innerHTML = "";
    uniqueTimezones.forEach((timezone) => {
      const option = document.createElement("option");
      option.value = timezone;
      option.textContent = timezone === browserTimezone ? `${timezone} (browser)` : timezone;
      select.appendChild(option);
    });

    if (!uniqueTimezones.includes(state.timezone)) {
      const option = document.createElement("option");
      option.value = state.timezone;
      option.textContent = state.timezone;
      select.appendChild(option);
    }

    select.value = state.timezone;
  }

  function runFilterSummaryText(totalCount) {
    const state = getState();
    const bits = [];
    if (state.startDate) {
      bits.push(`startDate=${state.startDate}`);
    }
    if (state.timezone) {
      bits.push(`timezone=${state.timezone}`);
    }
    if (state.selectedJobKey) {
      bits.push(`job='${state.selectedJobKey}'`);
    }
    if (bits.length === 0) {
      return `${totalCount} run(s)`;
    }
    return `${totalCount} run(s) for ${bits.join(", ")}`;
  }

  function renderTable() {
    const stateElement = document.getElementById("runs-state");
    const table = document.getElementById("runs-table");
    const body = document.getElementById("runs-body");
    if (!stateElement || !table || !body) {
      return;
    }

    const state = getState();
    const filtered = state.items.filter((run) => {
        const haystack = `${run.scenario || ""} ${run.status || ""} ${run.runMode || ""} ${run.recoveryPolicy || ""} ${run.triggerOrigin || ""} ${run.jobExecutionId || ""}`.toLowerCase();
      return haystack.includes(state.filterText.trim().toLowerCase());
    });
    const sorted = sortItems(filtered, state.sortKey, state.sortDirection);
    renderInstanceOptions(sorted);

    body.innerHTML = "";
    if (sorted.length === 0) {
      stateElement.textContent = "No runs match the current filters.";
      table.hidden = true;
      return;
    }

    sorted.forEach((run) => {
      const row = document.createElement("tr");
      const runId = run.jobExecutionId;
      if (runId !== null && runId !== undefined) {
        row.className = "clickable-row";
        row.title = "Open run detail placeholder";
        row.addEventListener("click", () => {
          location.hash = `#/runs/${runId}`;
        });
      }
      row.innerHTML = `
        <td>${escapeHtml(run.scenario || "-")}</td>
        <td>${escapeHtml(run.status || "-")}</td>
        <td>${escapeHtml(run.triggerOrigin || "MANUAL")}</td>
        <td>${escapeHtml(run.runMode || "-")}</td>
        <td>${escapeHtml(run.recoveryPolicy || "-")}</td>
        <td>${escapeHtml(run.startTime || "-")}</td>
        <td>${escapeHtml(String(run.durationSeconds ?? "-"))}</td>
        <td>${escapeHtml(String(run.jobExecutionId ?? "-"))}</td>`;
      body.appendChild(row);
    });

    stateElement.textContent = `Showing ${sorted.length} of ${runFilterSummaryText(state.items.length)}.`;
    table.hidden = false;
  }

  function clearInstanceOptions() {
    const select = document.getElementById("runs-instance-select");
    if (!select) {
      return;
    }
    select.innerHTML = '<option value="">Select run instance</option>';
    select.disabled = true;
  }

  function renderInstanceOptions(runs) {
    const select = document.getElementById("runs-instance-select");
    if (!select) {
      return;
    }

    const items = Array.isArray(runs)
      ? runs.filter((run) => run && run.jobExecutionId !== null && run.jobExecutionId !== undefined)
      : [];

    select.innerHTML = '<option value="">Select run instance</option>';
    if (items.length === 0) {
      select.disabled = true;
      return;
    }

    items.forEach((run) => {
      const option = document.createElement("option");
      option.value = String(run.jobExecutionId);
      option.textContent = `${run.jobExecutionId} | ${run.status || "-"} | ${run.startTime || "-"}`;
      select.appendChild(option);
    });

    select.disabled = false;
  }

  return {
    initializeControls,
    applyRouteState,
    renderTable,
    renderTimezoneOptions,
    clearInstanceOptions,
  };
}

