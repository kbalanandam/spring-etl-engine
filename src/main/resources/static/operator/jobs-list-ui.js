import { labelDirection, sortItems, toggleDirection } from "./list-sort-utils.js";

export function createJobsListUi(options) {
  const getState = options.getState;
  const syncRouteHash = options.syncRouteHash;
  const escapeHtml = options.escapeHtml;

  function initializeControls() {
    const jobsFilter = document.getElementById("jobs-filter-input");
    const jobsSort = document.getElementById("jobs-sort-select");
    const jobsDirection = document.getElementById("jobs-sort-dir-btn");
    if (!jobsFilter || !jobsSort || !jobsDirection) {
      return;
    }

    jobsFilter.addEventListener("input", (event) => {
      getState().filterText = event.target.value || "";
      syncRouteHash("jobs");
      renderTable();
    });

    jobsSort.addEventListener("change", (event) => {
      getState().sortKey = event.target.value;
      syncRouteHash("jobs");
      renderTable();
    });

    jobsDirection.addEventListener("click", () => {
      const state = getState();
      state.sortDirection = toggleDirection(state.sortDirection);
      jobsDirection.textContent = labelDirection(state.sortDirection);
      syncRouteHash("jobs");
      renderTable();
    });
  }

  function applyRouteState(routeState) {
    const state = getState();
    state.filterText = routeState.filterText || "";
    state.sortKey = routeState.sortKey || "jobKey";
    state.sortDirection = routeState.sortDirection || "asc";

    const filter = document.getElementById("jobs-filter-input");
    const sort = document.getElementById("jobs-sort-select");
    const direction = document.getElementById("jobs-sort-dir-btn");
    if (filter) {
      filter.value = state.filterText;
    }
    if (sort) {
      sort.value = state.sortKey;
    }
    if (direction) {
      direction.textContent = labelDirection(state.sortDirection);
    }
  }

  function renderTable() {
    const stateElement = document.getElementById("jobs-state");
    const table = document.getElementById("jobs-table");
    const body = document.getElementById("jobs-body");
    if (!stateElement || !table || !body) {
      return;
    }

    const state = getState();
    const filtered = state.items.filter((job) => {
      const haystack = `${job.jobKey || ""} ${job.displayName || ""} ${job.readinessStatus || ""}`.toLowerCase();
      return haystack.includes(state.filterText.trim().toLowerCase());
    });
    const sorted = sortItems(filtered, state.sortKey, state.sortDirection);

    body.innerHTML = "";
    if (sorted.length === 0) {
      stateElement.textContent = "No job bundles match the current filter.";
      table.hidden = true;
      return;
    }

    sorted.forEach((job) => {
      const row = document.createElement("tr");
      const jobKey = job.jobKey;
      const jobKeyCell = jobKey
        ? `<a href="#/jobs/${encodeURIComponent(jobKey)}">${escapeHtml(jobKey)}</a>`
        : "-";
      row.innerHTML = `
        <td>${jobKeyCell}</td>
        <td>${escapeHtml(job.displayName || "-")}</td>
        <td>${escapeHtml(job.readinessStatus || "-")}</td>`;
      body.appendChild(row);
    });

    stateElement.textContent = `Showing ${sorted.length} of ${state.items.length} job bundle(s).`;
    table.hidden = false;
  }

  return {
    initializeControls,
    applyRouteState,
    renderTable,
  };
}

