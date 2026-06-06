import { labelDirection, sortItems, toggleDirection } from "./list-sort-utils.js";

export function createJobsListUi(options) {
  const getState = options.getState;
  const syncRouteHash = options.syncRouteHash;
  const escapeHtml = options.escapeHtml;
  const loadJobStepNames = typeof options.loadJobStepNames === "function"
    ? options.loadJobStepNames
    : async () => [];
  const getRouteSuffix = typeof options.getRouteSuffix === "function"
    ? options.getRouteSuffix
    : () => "";

  function initializeControls() {
    const jobsFilter = document.getElementById("jobs-filter-input");
    const jobsSort = document.getElementById("jobs-sort-select");
    const jobsDirection = document.getElementById("jobs-sort-dir-btn");
    const jobsPageSize = document.getElementById("jobs-page-size-select");
    const jobsPrev = document.getElementById("jobs-page-prev-btn");
    const jobsNext = document.getElementById("jobs-page-next-btn");
    if (!jobsFilter || !jobsSort || !jobsDirection || !jobsPageSize || !jobsPrev || !jobsNext) {
      return;
    }

    jobsFilter.addEventListener("input", (event) => {
      const state = getState();
      state.filterText = event.target.value || "";
      state.page = 1;
      state.expandedJobKey = "";
      syncRouteHash("jobs");
      renderTable();
    });

    jobsSort.addEventListener("change", (event) => {
      const state = getState();
      state.sortKey = event.target.value;
      state.page = 1;
      state.expandedJobKey = "";
      syncRouteHash("jobs");
      renderTable();
    });

    jobsDirection.addEventListener("click", () => {
      const state = getState();
      state.sortDirection = toggleDirection(state.sortDirection);
      state.page = 1;
      state.expandedJobKey = "";
      jobsDirection.textContent = labelDirection(state.sortDirection);
      syncRouteHash("jobs");
      renderTable();
    });

    jobsPageSize.addEventListener("change", (event) => {
      const state = getState();
      const nextPageSize = Number(event.target.value);
      state.pageSize = Number.isFinite(nextPageSize) && nextPageSize > 0 ? nextPageSize : state.pageSize;
      state.page = 1;
      state.expandedJobKey = "";
      syncRouteHash("jobs");
      renderTable();
    });

    jobsPrev.addEventListener("click", () => {
      const state = getState();
      if (state.page <= 1) {
        return;
      }
      state.page -= 1;
      state.expandedJobKey = "";
      syncRouteHash("jobs");
      renderTable();
    });

    jobsNext.addEventListener("click", () => {
      const state = getState();
      state.page += 1;
      state.expandedJobKey = "";
      syncRouteHash("jobs");
      renderTable();
    });
  }

  function applyRouteState(routeState) {
    const state = getState();
    state.filterText = routeState.filterText || "";
    state.sortKey = routeState.sortKey || "jobKey";
    state.sortDirection = routeState.sortDirection || "asc";
    state.page = routeState.page || 1;
    state.pageSize = routeState.pageSize || state.pageSize || 10;
    state.expandedJobKey = "";

    const filter = document.getElementById("jobs-filter-input");
    const sort = document.getElementById("jobs-sort-select");
    const direction = document.getElementById("jobs-sort-dir-btn");
    const pageSize = document.getElementById("jobs-page-size-select");
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
  }

  function renderTable() {
    const stateElement = document.getElementById("jobs-state");
    const table = document.getElementById("jobs-table");
    const body = document.getElementById("jobs-body");
    const pageStatus = document.getElementById("jobs-page-status");
    const prevButton = document.getElementById("jobs-page-prev-btn");
    const nextButton = document.getElementById("jobs-page-next-btn");
    const pageSize = document.getElementById("jobs-page-size-select");
    if (!stateElement || !table || !body || !pageStatus || !prevButton || !nextButton || !pageSize) {
      return;
    }

    const state = getState();
    if (!state.jobStepPreviewByJobKey || typeof state.jobStepPreviewByJobKey !== "object") {
      state.jobStepPreviewByJobKey = {};
    }
    const filtered = state.items.filter((job) => {
      const haystack = `${job.jobKey || ""} ${job.displayName || ""} ${job.readinessStatus || ""}`.toLowerCase();
      return haystack.includes(state.filterText.trim().toLowerCase());
    });
    const sorted = sortItems(filtered, state.sortKey, state.sortDirection);
    const effectivePageSize = Number.isFinite(Number(state.pageSize)) && Number(state.pageSize) > 0
      ? Number(state.pageSize)
      : 10;
    const totalPages = Math.max(1, Math.ceil(sorted.length / effectivePageSize));
    state.page = Math.min(Math.max(Number(state.page) || 1, 1), totalPages);
    const startIndex = (state.page - 1) * effectivePageSize;
    const visible = sorted.slice(startIndex, startIndex + effectivePageSize);
    const routeSuffix = getRouteSuffix();

    body.innerHTML = "";
    if (sorted.length === 0) {
      stateElement.textContent = "No job bundles match the current filter.";
      pageStatus.textContent = "Page 0 of 0";
      prevButton.disabled = true;
      nextButton.disabled = true;
      table.hidden = true;
      return;
    }

    visible.forEach((job) => {
      const jobKey = String(job.jobKey || "").trim();
      const row = document.createElement("tr");
      const jobKeyCell = jobKey
        ? `<a href="#/jobs/${encodeURIComponent(jobKey)}${routeSuffix}">${escapeHtml(jobKey)}</a>`
        : "-";

      const keyCell = document.createElement("td");
      const keyCellContent = document.createElement("div");
      keyCellContent.className = "jobs-key-cell";
      if (jobKey) {
        const toggleButton = document.createElement("button");
        toggleButton.type = "button";
        toggleButton.className = "jobs-step-toggle-btn";
        const expanded = state.expandedJobKey === jobKey;
        toggleButton.textContent = expanded ? "-" : "+";
        toggleButton.title = expanded ? "Hide steps" : "Show steps";
        toggleButton.addEventListener("click", () => {
          toggleJobSteps(jobKey);
        });
        keyCellContent.appendChild(toggleButton);
      }
      const keyLinkWrapper = document.createElement("span");
      keyLinkWrapper.innerHTML = jobKeyCell;
      keyCellContent.appendChild(keyLinkWrapper);
      if (jobKey) {
        const preview = state.jobStepPreviewByJobKey[jobKey];
        const stepCount = preview && preview.status === "ready" && Array.isArray(preview.steps)
          ? preview.steps.length
          : null;
        const badge = document.createElement("span");
        badge.className = "jobs-step-badge";
        badge.textContent = stepCount === null
          ? "1+ steps"
          : `${stepCount} step${stepCount === 1 ? "" : "s"}`;
        keyCellContent.appendChild(badge);
      }
      keyCell.appendChild(keyCellContent);
      row.appendChild(keyCell);

      const displayNameCell = document.createElement("td");
      displayNameCell.textContent = job.displayName || "-";
      row.appendChild(displayNameCell);

      const readinessCell = document.createElement("td");
      readinessCell.textContent = job.readinessStatus || "-";
      row.appendChild(readinessCell);

      body.appendChild(row);

      if (jobKey && state.expandedJobKey === jobKey) {
        const detailRow = document.createElement("tr");
        detailRow.className = "jobs-step-detail-row";
        const detailCell = document.createElement("td");
        detailCell.colSpan = 3;
        detailCell.className = "jobs-step-detail-cell";
        detailCell.innerHTML = renderStepPreviewMarkup(state.jobStepPreviewByJobKey[jobKey]);
        detailRow.appendChild(detailCell);
        body.appendChild(detailRow);
      }
    });

    pageSize.value = String(effectivePageSize);
    pageStatus.textContent = `Page ${state.page} of ${totalPages}`;
    prevButton.disabled = state.page <= 1;
    nextButton.disabled = state.page >= totalPages;
    stateElement.textContent = `Showing ${visible.length} of ${sorted.length} matching job bundle(s) (${state.items.length} total).`;
    table.hidden = false;
  }

  function renderStepPreviewMarkup(preview) {
    const info = preview || { status: "idle", steps: [] };
    if (info.status === "loading") {
      return '<div class="jobs-step-preview-state">Loading steps...</div>';
    }
    if (info.status === "error") {
      return `<div class="jobs-step-preview-state error">${escapeHtml(info.message || "Unable to load steps.")}</div>`;
    }
    if (!Array.isArray(info.steps) || info.steps.length === 0) {
      return '<div class="jobs-step-preview-state">No steps declared in job config.</div>';
    }
    const items = info.steps.map((stepName) => `<li>${escapeHtml(stepName)}</li>`).join("");
    return `<div class="jobs-step-preview-title">Steps</div><ol class="jobs-step-preview-list">${items}</ol>`;
  }

  async function toggleJobSteps(jobKey) {
    const state = getState();
    if (!jobKey) {
      return;
    }
    if (state.expandedJobKey === jobKey) {
      state.expandedJobKey = "";
      renderTable();
      return;
    }

    state.expandedJobKey = jobKey;
    if (!state.jobStepPreviewByJobKey || typeof state.jobStepPreviewByJobKey !== "object") {
      state.jobStepPreviewByJobKey = {};
    }
    const existing = state.jobStepPreviewByJobKey[jobKey];
    if (existing && (existing.status === "ready" || existing.status === "error")) {
      renderTable();
      return;
    }

    state.jobStepPreviewByJobKey[jobKey] = { status: "loading", steps: [] };
    renderTable();

    try {
      const steps = await loadJobStepNames(jobKey);
      state.jobStepPreviewByJobKey[jobKey] = {
        status: "ready",
        steps: Array.isArray(steps) ? steps : [],
      };
    } catch (error) {
      state.jobStepPreviewByJobKey[jobKey] = {
        status: "error",
        message: error && error.message ? error.message : "Unable to load steps.",
        steps: [],
      };
    }

    if (state.expandedJobKey === jobKey) {
      renderTable();
    }
  }

  return {
    initializeControls,
    applyRouteState,
    renderTable,
  };
}

