import { labelDirection, sortItems, toggleDirection } from "./list-sort-utils.js";

const RUNS_PAGE_SIZE_OPTIONS = [8, 10, 15, 20];

export function createRunsListUi(options) {
  const getState = options.getState;
  const syncRouteHash = options.syncRouteHash;
  const renderJobOptions = options.renderJobOptions;
  const renderTriggerSourceOptions = options.renderTriggerSourceOptions;
  const formatDateForInput = options.formatDateForInput;
  const escapeHtml = options.escapeHtml;

  function initializeControls() {
    const runsFilter = document.getElementById("runs-filter-input");
    const runsStartDate = document.getElementById("runs-start-date-input");
    const runsTimezone = document.getElementById("runs-timezone-select");
    const runsJobSelect = document.getElementById("runs-job-select");
    const runsRunModeSelect = document.getElementById("runs-run-mode-select");
    const runsRecoveryPolicySelect = document.getElementById("runs-recovery-policy-select");
    const runsTriggerSourceSelect = document.getElementById("runs-trigger-source-select");
    const runsInstanceSelect = document.getElementById("runs-instance-select");
    const runsSort = document.getElementById("runs-sort-select");
    const runsDirection = document.getElementById("runs-sort-dir-btn");
    const runsPageSize = document.getElementById("runs-page-size-select");
    const runsPagePrev = document.getElementById("runs-page-prev-btn");
    const runsPageNext = document.getElementById("runs-page-next-btn");

    renderTimezoneOptions();

    if (runsStartDate) {
      runsStartDate.addEventListener("change", (event) => {
        const state = getState();
        state.startDate = event.target.value || "";
        state.page = 1;
        state.loaded = false;
        syncRouteHash("runs");
      });
    }
    if (runsTimezone) {
      runsTimezone.addEventListener("change", (event) => {
        const state = getState();
        state.timezone = event.target.value || state.browserTimezone;
        state.page = 1;
        state.loaded = false;
        syncRouteHash("runs");
      });
    }

    if (runsJobSelect) {
      runsJobSelect.addEventListener("change", (event) => {
        const state = getState();
        state.selectedJobKey = event.target.value || "";
        state.page = 1;
        state.loaded = false;
        syncRouteHash("runs");
      });
    }
    if (runsRunModeSelect) {
      runsRunModeSelect.addEventListener("change", (event) => {
        const state = getState();
        state.runModeFilter = event.target.value || "";
        state.page = 1;
        state.loaded = false;
        syncRouteHash("runs");
      });
    }
    if (runsRecoveryPolicySelect) {
      runsRecoveryPolicySelect.addEventListener("change", (event) => {
        const state = getState();
        state.recoveryPolicyFilter = event.target.value || "";
        state.page = 1;
        state.loaded = false;
        syncRouteHash("runs");
      });
    }
    if (runsTriggerSourceSelect) {
      runsTriggerSourceSelect.addEventListener("change", (event) => {
        const state = getState();
        state.triggerSourceFilter = event.target.value || "";
        state.page = 1;
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
        getState().page = 1;
        syncRouteHash("runs");
        renderTable();
      });
    }
    if (runsSort) {
      runsSort.addEventListener("change", (event) => {
        getState().sortKey = event.target.value;
        getState().page = 1;
        syncRouteHash("runs");
        renderTable();
      });
    }
    if (runsDirection) {
      runsDirection.addEventListener("click", () => {
        const state = getState();
        state.sortDirection = toggleDirection(state.sortDirection);
        state.page = 1;
        runsDirection.textContent = labelDirection(state.sortDirection);
        syncRouteHash("runs");
        renderTable();
      });
    }
    if (runsPageSize) {
      runsPageSize.addEventListener("change", (event) => {
        const state = getState();
        state.pageSize = normalizeRunsPageSize(event.target.value, state.pageSize || 10);
        state.page = 1;
        syncRouteHash("runs");
        renderTable();
      });
    }
    if (runsPagePrev) {
      runsPagePrev.addEventListener("click", () => {
        const state = getState();
        if ((Number(state.page) || 1) <= 1) {
          return;
        }
        state.page = (Number(state.page) || 1) - 1;
        syncRouteHash("runs");
        renderTable();
      });
    }
    if (runsPageNext) {
      runsPageNext.addEventListener("click", () => {
        const state = getState();
        state.page = (Number(state.page) || 1) + 1;
        syncRouteHash("runs");
        renderTable();
      });
    }
  }

  function applyRouteState(routeState) {
    const state = getState();
    state.filterText = String(routeState.filterText || "").trim();
    state.startDate = normalizeDateInput(
      routeState.startDate,
      state.startDate || formatDateForInput(new Date())
    );
    state.timezone = normalizeToken(routeState.timezone)
      || normalizeToken(state.timezone)
      || normalizeToken(state.browserTimezone)
      || "UTC";
    state.sortKey = routeState.sortKey || "startTime";
    state.sortDirection = routeState.sortDirection || "desc";
    state.page = Math.max(1, Number(routeState.page) || 1);
    state.pageSize = normalizeRunsPageSize(routeState.pageSize, state.pageSize || 10);

    renderTimezoneOptions();
    renderJobOptions();
    renderTriggerSourceOptions();

    const startDate = document.getElementById("runs-start-date-input");
    const timezone = document.getElementById("runs-timezone-select");
    const jobSelect = document.getElementById("runs-job-select");
    const runModeSelect = document.getElementById("runs-run-mode-select");
    const recoveryPolicySelect = document.getElementById("runs-recovery-policy-select");
    const triggerSourceSelect = document.getElementById("runs-trigger-source-select");
    const filter = document.getElementById("runs-filter-input");
    const sort = document.getElementById("runs-sort-select");
    const direction = document.getElementById("runs-sort-dir-btn");
    const pageSize = document.getElementById("runs-page-size-select");

    state.selectedJobKey = normalizeSelectValue(routeState.selectedJobKey, jobSelect);
    state.runModeFilter = normalizeSelectValue(routeState.runModeFilter, runModeSelect);
    state.recoveryPolicyFilter = normalizeSelectValue(routeState.recoveryPolicyFilter, recoveryPolicySelect);
    state.triggerSourceFilter = normalizeSelectValue(routeState.triggerSourceFilter, triggerSourceSelect);
    state.timezone = normalizeSelectValue(state.timezone, timezone)
      || normalizeToken(state.browserTimezone)
      || "UTC";

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
    if (triggerSourceSelect) {
      triggerSourceSelect.value = state.triggerSourceFilter;
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
    if (pageSize) {
      pageSize.value = String(state.pageSize);
    }
    clearInstanceOptions();
  }

  function normalizeToken(value) {
    return String(value || "").trim();
  }

  function normalizeDateInput(value, fallback) {
    const candidate = normalizeToken(value);
    if (isIsoDate(candidate)) {
      return candidate;
    }
    return isIsoDate(fallback) ? fallback : "";
  }

  function isIsoDate(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(value || ""))) {
      return false;
    }
    const [yearText, monthText, dayText] = String(value).split("-");
    const year = Number.parseInt(yearText, 10);
    const month = Number.parseInt(monthText, 10);
    const day = Number.parseInt(dayText, 10);
    if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
      return false;
    }
    const date = new Date(Date.UTC(year, month - 1, day));
    return date.getUTCFullYear() === year
      && date.getUTCMonth() + 1 === month
      && date.getUTCDate() === day;
  }

  function normalizeSelectValue(value, selectElement) {
    const candidate = normalizeToken(value);
    if (!candidate) {
      return "";
    }
    if (!selectElement || !Array.isArray(selectElement.children)) {
      return candidate;
    }
    const optionValues = selectElement.children.map((child) => normalizeToken(child?.value)).filter(Boolean);
    return optionValues.includes(candidate) ? candidate : "";
  }

  function normalizeRunsPageSize(value, fallback) {
    const parsed = Number.parseInt(value, 10);
    if (Number.isFinite(parsed) && RUNS_PAGE_SIZE_OPTIONS.includes(parsed)) {
      return parsed;
    }
    return RUNS_PAGE_SIZE_OPTIONS.includes(fallback) ? fallback : 10;
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
    if (state.selectedJobKey) {
      bits.push(`job ${state.selectedJobKey}`);
    }
    if (state.triggerSourceFilter) {
      bits.push(`source ${state.triggerSourceFilter}`);
    }
    if (state.startDate) {
      bits.push(`from ${state.startDate}`);
    }
    if (bits.length === 0) {
      return `${totalCount} run(s)`;
    }
    return `${totalCount} run(s), ${bits.join(", ")}`;
  }

  function buildRunSummaryCell(run) {
    const scenario = escapeHtml(run?.scenario || "-");
    const runMode = escapeHtml(run?.runMode || "-");
    const recoveryPolicy = escapeHtml(run?.recoveryPolicy || "-");
    return `
      <div class="runs-summary-cell">
        <div class="runs-summary-title">${scenario}</div>
        <div class="runs-summary-meta">${runMode} | ${recoveryPolicy}</div>
      </div>`;
  }

  function renderTable() {
    const stateElement = document.getElementById("runs-state");
    const table = document.getElementById("runs-table");
    const body = document.getElementById("runs-body");
    const pageStatus = document.getElementById("runs-page-status");
    const pagePrev = document.getElementById("runs-page-prev-btn");
    const pageNext = document.getElementById("runs-page-next-btn");
    const pageSizeSelect = document.getElementById("runs-page-size-select");
    if (!stateElement || !table || !body || !pageStatus || !pagePrev || !pageNext || !pageSizeSelect) {
      return;
    }

    const state = getState();
    const filtered = state.items.filter((run) => {
        const haystack = `${run.scenario || ""} ${run.status || ""} ${run.runMode || ""} ${run.recoveryPolicy || ""} ${run.triggerOrigin || ""} ${run.jobExecutionId || ""}`.toLowerCase();
      return haystack.includes(state.filterText.trim().toLowerCase());
    });
    const sorted = sortItems(filtered, state.sortKey, state.sortDirection);
    const pageSize = normalizeRunsPageSize(state.pageSize, 10);
    const totalPages = Math.max(1, Math.ceil(sorted.length / pageSize));
    state.page = Math.min(Math.max(Number(state.page) || 1, 1), totalPages);
    state.pageSize = pageSize;
    const startIndex = (state.page - 1) * pageSize;
    const visible = sorted.slice(startIndex, startIndex + pageSize);
    renderInstanceOptions(visible);

    body.innerHTML = "";
    if (sorted.length === 0) {
      stateElement.textContent = "No runs match the current filters.";
      pageStatus.textContent = "Page 0 of 0";
      pagePrev.disabled = true;
      pageNext.disabled = true;
      table.hidden = true;
      return;
    }

    visible.forEach((run) => {
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
        <td>${buildRunSummaryCell(run)}</td>
        <td>${escapeHtml(run.status || "-")}</td>
        <td>${escapeHtml(run.triggerOrigin || "MANUAL")}</td>
        <td>${escapeHtml(run.startTime || "-")}</td>
        <td>${escapeHtml(String(run.durationSeconds ?? "-"))}</td>
        <td>${escapeHtml(String(run.jobExecutionId ?? "-"))}</td>`;
      body.appendChild(row);
    });

    pageSizeSelect.value = String(pageSize);
    pageStatus.textContent = `Page ${state.page} of ${totalPages}`;
    pagePrev.disabled = state.page <= 1;
    pageNext.disabled = state.page >= totalPages;
    stateElement.textContent = `Showing ${visible.length} of ${sorted.length} matching run(s) (${runFilterSummaryText(state.items.length)}).`;
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

