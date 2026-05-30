const routes = {
  jobs: {
    tab: document.getElementById("tab-jobs"),
    view: document.getElementById("view-jobs"),
    load: loadJobs,
  },
  jobDetail: {
    tab: document.getElementById("tab-jobs"),
    view: document.getElementById("view-job-detail"),
    load: loadJobDetailPlaceholder,
  },
  runs: {
    tab: document.getElementById("tab-runs"),
    view: document.getElementById("view-runs"),
    load: loadRuns,
  },
  runDetail: {
    tab: document.getElementById("tab-runs"),
    view: document.getElementById("view-run-detail"),
    load: loadRunDetailPlaceholder,
  },
};

const viewState = {
  jobs: {
    items: [],
    filterText: "",
    sortKey: "jobKey",
    sortDirection: "asc",
  },
  runs: {
    items: [],
    filterText: "",
    sortKey: "startTime",
    sortDirection: "desc",
  },
};

window.addEventListener("hashchange", renderRoute);
window.addEventListener("DOMContentLoaded", () => {
  initializeControls();
  if (!location.hash) {
    location.hash = "#/jobs";
    return;
  }
  renderRoute();
});

function currentRouteState() {
  const hash = location.hash.replace(/^#\/?/, "");
  const normalized = hash.toLowerCase();
  const runDetailMatch = hash.match(/^runs\/(\d+)$/i);
  const jobDetailMatch = hash.match(/^jobs\/([^/]+)$/i);

  if (jobDetailMatch) {
    return { key: "jobDetail", jobExecutionId: null, jobKey: decodeURIComponent(jobDetailMatch[1]) };
  }

  if (runDetailMatch) {
    return { key: "runDetail", jobExecutionId: runDetailMatch[1], jobKey: null };
  }
  if (normalized === "runs") {
    return { key: "runs", jobExecutionId: null, jobKey: null };
  }
  return { key: "jobs", jobExecutionId: null, jobKey: null };
}

function renderRoute() {
  const routeState = currentRouteState();
  const routeKey = routeState.key;

  Object.entries(routes).forEach(([key, route]) => {
    const active = key === routeKey;
    route.tab.classList.toggle("active", active);
    route.view.hidden = !active;
  });

  routes[routeKey].load(routeState);
}

async function loadJobs() {
  const state = document.getElementById("jobs-state");
  const table = document.getElementById("jobs-table");
  const body = document.getElementById("jobs-body");

  state.className = "state";
  state.textContent = "Loading jobs...";
  table.hidden = true;
  body.innerHTML = "";

  try {
    const response = await fetch("/api/v1/jobs", { headers: { Accept: "application/json" } });
    if (!response.ok) {
      throw new Error(`Jobs API returned ${response.status}`);
    }
    const payload = await response.json();
    viewState.jobs.items = Array.isArray(payload.items) ? payload.items : [];

    if (viewState.jobs.items.length === 0) {
      state.textContent = "No job bundles found.";
      return;
    }
    renderJobsTable();
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load jobs: ${error.message}`;
  }
}

async function loadJobDetailPlaceholder(routeState) {
  const state = document.getElementById("job-detail-state");
  const summary = document.getElementById("job-detail-summary");
  const jobKeyValue = routeState && routeState.jobKey ? routeState.jobKey : null;

  state.className = "state";
  summary.hidden = true;

  if (!jobKeyValue) {
    state.className = "state error";
    state.textContent = "Missing job key in route. Use a row in Jobs list.";
    return;
  }

  state.textContent = `Loading job ${jobKeyValue}...`;

  try {
    const response = await fetch(`/api/v1/jobs/${encodeURIComponent(jobKeyValue)}`, { headers: { Accept: "application/json" } });
    if (!response.ok) {
      throw new Error(`Job detail API returned ${response.status}`);
    }
    const payload = await response.json();
    const job = payload.job || {};

    document.getElementById("job-detail-key").textContent = job.jobKey || jobKeyValue;
    document.getElementById("job-detail-name").textContent = job.displayName || "-";
    document.getElementById("job-detail-readiness").textContent = job.readinessStatus || "-";
    document.getElementById("job-detail-recent-run-count").textContent = String(Array.isArray(payload.recentRuns) ? payload.recentRuns.length : 0);
    document.getElementById("job-detail-trigger-count").textContent = String(Array.isArray(payload.triggerEvents) ? payload.triggerEvents.length : 0);

    state.textContent = "U3 job detail route is wired. Trigger actions remain deferred to U3.";
    summary.hidden = false;
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load job detail placeholder: ${error.message}`;
  }
}

async function loadRuns() {
  const state = document.getElementById("runs-state");
  const table = document.getElementById("runs-table");
  const body = document.getElementById("runs-body");

  state.className = "state";
  state.textContent = "Loading runs...";
  table.hidden = true;
  body.innerHTML = "";

  try {
    const response = await fetch("/api/v1/runs?limit=25", { headers: { Accept: "application/json" } });
    if (!response.ok) {
      throw new Error(`Runs API returned ${response.status}`);
    }
    const payload = await response.json();
    viewState.runs.items = Array.isArray(payload.items) ? payload.items : [];

    if (viewState.runs.items.length === 0) {
      state.textContent = "No recent runs found.";
      return;
    }
    renderRunsTable();
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load runs: ${error.message}`;
  }
}

function initializeControls() {
  const jobsFilter = document.getElementById("jobs-filter-input");
  const jobsSort = document.getElementById("jobs-sort-select");
  const jobsDirection = document.getElementById("jobs-sort-dir-btn");

  jobsFilter.addEventListener("input", (event) => {
    viewState.jobs.filterText = event.target.value || "";
    renderJobsTable();
  });
  jobsSort.addEventListener("change", (event) => {
    viewState.jobs.sortKey = event.target.value;
    renderJobsTable();
  });
  jobsDirection.addEventListener("click", () => {
    viewState.jobs.sortDirection = toggleDirection(viewState.jobs.sortDirection);
    jobsDirection.textContent = labelDirection(viewState.jobs.sortDirection);
    renderJobsTable();
  });

  const runsFilter = document.getElementById("runs-filter-input");
  const runsSort = document.getElementById("runs-sort-select");
  const runsDirection = document.getElementById("runs-sort-dir-btn");

  runsFilter.addEventListener("input", (event) => {
    viewState.runs.filterText = event.target.value || "";
    renderRunsTable();
  });
  runsSort.addEventListener("change", (event) => {
    viewState.runs.sortKey = event.target.value;
    renderRunsTable();
  });
  runsDirection.addEventListener("click", () => {
    viewState.runs.sortDirection = toggleDirection(viewState.runs.sortDirection);
    runsDirection.textContent = labelDirection(viewState.runs.sortDirection);
    renderRunsTable();
  });
}

function renderJobsTable() {
  const state = document.getElementById("jobs-state");
  const table = document.getElementById("jobs-table");
  const body = document.getElementById("jobs-body");

  const filtered = viewState.jobs.items.filter((job) => {
    const haystack = `${job.jobKey || ""} ${job.displayName || ""} ${job.readinessStatus || ""}`.toLowerCase();
    return haystack.includes(viewState.jobs.filterText.trim().toLowerCase());
  });
  const sorted = sortItems(filtered, viewState.jobs.sortKey, viewState.jobs.sortDirection);

  body.innerHTML = "";
  if (sorted.length === 0) {
    state.textContent = "No job bundles match the current filter.";
    table.hidden = true;
    return;
  }

  sorted.forEach((job) => {
    const row = document.createElement("tr");
    const jobKey = job.jobKey;
    if (jobKey) {
      row.className = "clickable-row";
      row.title = "Open job detail placeholder";
      row.addEventListener("click", () => {
        location.hash = `#/jobs/${encodeURIComponent(jobKey)}`;
      });
    }
    row.innerHTML = `
      <td>${escapeHtml(job.jobKey || "-")}</td>
      <td>${escapeHtml(job.displayName || "-")}</td>
      <td>${escapeHtml(job.readinessStatus || "-")}</td>`;
    body.appendChild(row);
  });

  state.textContent = `Showing ${sorted.length} of ${viewState.jobs.items.length} job bundle(s).`;
  table.hidden = false;
}

function renderRunsTable() {
  const state = document.getElementById("runs-state");
  const table = document.getElementById("runs-table");
  const body = document.getElementById("runs-body");

  const filtered = viewState.runs.items.filter((run) => {
    const haystack = `${run.scenario || ""} ${run.status || ""} ${run.jobExecutionId || ""}`.toLowerCase();
    return haystack.includes(viewState.runs.filterText.trim().toLowerCase());
  });
  const sorted = sortItems(filtered, viewState.runs.sortKey, viewState.runs.sortDirection);

  body.innerHTML = "";
  if (sorted.length === 0) {
    state.textContent = "No runs match the current filter.";
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
      <td>${escapeHtml(run.startTime || "-")}</td>
      <td>${escapeHtml(String(run.durationSeconds ?? "-"))}</td>
      <td>${escapeHtml(String(run.jobExecutionId ?? "-"))}</td>`;
    body.appendChild(row);
  });

  state.textContent = `Showing ${sorted.length} of ${viewState.runs.items.length} run(s).`;
  table.hidden = false;
}

function sortItems(items, key, direction) {
  const factor = direction === "desc" ? -1 : 1;
  return [...items].sort((a, b) => factor * compareValues(a[key], b[key]));
}

function compareValues(left, right) {
  if (left === right) {
    return 0;
  }
  if (left === null || left === undefined) {
    return -1;
  }
  if (right === null || right === undefined) {
    return 1;
  }

  const leftNumber = Number(left);
  const rightNumber = Number(right);
  const leftIsNumber = !Number.isNaN(leftNumber) && String(left).trim() !== "";
  const rightIsNumber = !Number.isNaN(rightNumber) && String(right).trim() !== "";
  if (leftIsNumber && rightIsNumber) {
    return leftNumber - rightNumber;
  }

  const leftTime = Date.parse(String(left));
  const rightTime = Date.parse(String(right));
  if (!Number.isNaN(leftTime) && !Number.isNaN(rightTime)) {
    return leftTime - rightTime;
  }

  return String(left).localeCompare(String(right));
}

function toggleDirection(direction) {
  return direction === "asc" ? "desc" : "asc";
}

function labelDirection(direction) {
  return direction === "asc" ? "Asc" : "Desc";
}

async function loadRunDetailPlaceholder(routeState) {
  const state = document.getElementById("run-detail-state");
  const summary = document.getElementById("run-detail-summary");
  const runIdValue = routeState && routeState.jobExecutionId ? routeState.jobExecutionId : null;

  state.className = "state";
  summary.hidden = true;

  if (!runIdValue) {
    state.className = "state error";
    state.textContent = "Missing run id in route. Use a row in Runs list.";
    return;
  }

  state.textContent = `Loading run ${runIdValue}...`;

  try {
    const response = await fetch(`/api/v1/runs/${runIdValue}/detail`, { headers: { Accept: "application/json" } });
    if (!response.ok) {
      throw new Error(`Run detail API returned ${response.status}`);
    }
    const payload = await response.json();
    const run = payload.run || {};

    document.getElementById("run-detail-id").textContent = String(run.jobExecutionId ?? runIdValue);
    document.getElementById("run-detail-scenario").textContent = run.scenario || "-";
    document.getElementById("run-detail-status").textContent = run.status || "-";
    document.getElementById("run-detail-step-count").textContent = String(Array.isArray(payload.steps) ? payload.steps.length : 0);
    document.getElementById("run-detail-artifact-count").textContent = String(Array.isArray(payload.artifacts) ? payload.artifacts.length : 0);

    state.textContent = "U2 detail route is wired. Full drill-down remains in U2 scope.";
    summary.hidden = false;
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load run detail placeholder: ${error.message}`;
  }
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#39;");
}



