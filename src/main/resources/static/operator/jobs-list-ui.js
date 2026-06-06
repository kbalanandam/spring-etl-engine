import { labelDirection, sortItems, toggleDirection } from "./list-sort-utils.js";

export function createJobsListUi(options) {
  const getState = options.getState;
  const syncRouteHash = options.syncRouteHash;
  const escapeHtml = options.escapeHtml;
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
      syncRouteHash("jobs");
      renderTable();
    });

    jobsSort.addEventListener("change", (event) => {
      const state = getState();
      state.sortKey = event.target.value;
      state.page = 1;
      syncRouteHash("jobs");
      renderTable();
    });

    jobsDirection.addEventListener("click", () => {
      const state = getState();
      state.sortDirection = toggleDirection(state.sortDirection);
      state.page = 1;
      jobsDirection.textContent = labelDirection(state.sortDirection);
      syncRouteHash("jobs");
      renderTable();
    });

    jobsPageSize.addEventListener("change", (event) => {
      const state = getState();
      const nextPageSize = Number(event.target.value);
      state.pageSize = Number.isFinite(nextPageSize) && nextPageSize > 0 ? nextPageSize : state.pageSize;
      state.page = 1;
      syncRouteHash("jobs");
      renderTable();
    });

    jobsPrev.addEventListener("click", () => {
      const state = getState();
      if (state.page <= 1) {
        return;
      }
      state.page -= 1;
      syncRouteHash("jobs");
      renderTable();
    });

    jobsNext.addEventListener("click", () => {
      const state = getState();
      state.page += 1;
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
      const row = document.createElement("tr");
      const jobKey = job.jobKey;
      const jobKeyCell = jobKey
        ? `<a href="#/jobs/${encodeURIComponent(jobKey)}${routeSuffix}">${escapeHtml(jobKey)}</a>`
        : "-";
      row.innerHTML = `
        <td>${jobKeyCell}</td>
        <td>${escapeHtml(job.displayName || "-")}</td>
        <td>${escapeHtml(job.readinessStatus || "-")}</td>`;
      body.appendChild(row);
    });

    pageSize.value = String(effectivePageSize);
    pageStatus.textContent = `Page ${state.page} of ${totalPages}`;
    prevButton.disabled = state.page <= 1;
    nextButton.disabled = state.page >= totalPages;
    stateElement.textContent = `Showing ${visible.length} of ${sorted.length} matching job bundle(s) (${state.items.length} total).`;
    table.hidden = false;
  }

  return {
    initializeControls,
    applyRouteState,
    renderTable,
  };
}

