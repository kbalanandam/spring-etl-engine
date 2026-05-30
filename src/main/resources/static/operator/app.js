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
    load: loadRunDetail,
  },
};

const viewState = {
  jobs: {
    loaded: false,
    items: [],
    filterText: "",
    sortKey: "jobKey",
    sortDirection: "asc",
  },
  runs: {
    loaded: false,
    items: [],
    filterText: "",
    sortKey: "startTime",
    sortDirection: "desc",
  },
};

const SORT_KEYS = {
  jobs: ["jobKey", "displayName", "readinessStatus"],
  runs: ["startTime", "jobExecutionId", "scenario", "status"],
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
  const parsed = parseHashRoute();
  const path = parsed.path;
  const normalized = path.toLowerCase();
  const runDetailMatch = path.match(/^runs\/(\d+)$/i);
  const jobDetailMatch = path.match(/^jobs\/([^/]+)$/i);

  if (jobDetailMatch) {
    return { key: "jobDetail", jobExecutionId: null, jobKey: decodeURIComponent(jobDetailMatch[1]), query: parsed.query };
  }

  if (runDetailMatch) {
    return { key: "runDetail", jobExecutionId: runDetailMatch[1], jobKey: null, query: parsed.query };
  }
  if (normalized === "runs") {
    return {
      key: "runs",
      jobExecutionId: null,
      jobKey: null,
      query: parsed.query,
      filterText: parsed.query.f || "",
      sortKey: normalizeSortKey("runs", parsed.query.sort, "startTime"),
      sortDirection: normalizeDirection(parsed.query.dir, "desc"),
    };
  }
  return {
    key: "jobs",
    jobExecutionId: null,
    jobKey: null,
    query: parsed.query,
    filterText: parsed.query.f || "",
    sortKey: normalizeSortKey("jobs", parsed.query.sort, "jobKey"),
    sortDirection: normalizeDirection(parsed.query.dir, "asc"),
  };
}

function renderRoute() {
  const routeState = currentRouteState();
  const routeKey = routeState.key;

  if (routeKey === "jobs" || routeKey === "runs") {
    applyRouteStateToListView(routeState);
  }

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

  if (viewState.jobs.loaded) {
    renderJobsTable();
    return;
  }

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
    viewState.jobs.loaded = true;

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

  if (viewState.runs.loaded) {
    renderRunsTable();
    return;
  }

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
    viewState.runs.loaded = true;

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
    syncListRouteHash("jobs");
    renderJobsTable();
  });
  jobsSort.addEventListener("change", (event) => {
    viewState.jobs.sortKey = event.target.value;
    syncListRouteHash("jobs");
    renderJobsTable();
  });
  jobsDirection.addEventListener("click", () => {
    viewState.jobs.sortDirection = toggleDirection(viewState.jobs.sortDirection);
    jobsDirection.textContent = labelDirection(viewState.jobs.sortDirection);
    syncListRouteHash("jobs");
    renderJobsTable();
  });

  const runsFilter = document.getElementById("runs-filter-input");
  const runsSort = document.getElementById("runs-sort-select");
  const runsDirection = document.getElementById("runs-sort-dir-btn");

  runsFilter.addEventListener("input", (event) => {
    viewState.runs.filterText = event.target.value || "";
    syncListRouteHash("runs");
    renderRunsTable();
  });
  runsSort.addEventListener("change", (event) => {
    viewState.runs.sortKey = event.target.value;
    syncListRouteHash("runs");
    renderRunsTable();
  });
  runsDirection.addEventListener("click", () => {
    viewState.runs.sortDirection = toggleDirection(viewState.runs.sortDirection);
    runsDirection.textContent = labelDirection(viewState.runs.sortDirection);
    syncListRouteHash("runs");
    renderRunsTable();
  });
}

function applyRouteStateToListView(routeState) {
  if (routeState.key === "jobs") {
    viewState.jobs.filterText = routeState.filterText || "";
    viewState.jobs.sortKey = routeState.sortKey || "jobKey";
    viewState.jobs.sortDirection = routeState.sortDirection || "asc";

    document.getElementById("jobs-filter-input").value = viewState.jobs.filterText;
    document.getElementById("jobs-sort-select").value = viewState.jobs.sortKey;
    document.getElementById("jobs-sort-dir-btn").textContent = labelDirection(viewState.jobs.sortDirection);
    return;
  }

  viewState.runs.filterText = routeState.filterText || "";
  viewState.runs.sortKey = routeState.sortKey || "startTime";
  viewState.runs.sortDirection = routeState.sortDirection || "desc";

  document.getElementById("runs-filter-input").value = viewState.runs.filterText;
  document.getElementById("runs-sort-select").value = viewState.runs.sortKey;
  document.getElementById("runs-sort-dir-btn").textContent = labelDirection(viewState.runs.sortDirection);
}

function syncListRouteHash(routeKey) {
  const source = routeKey === "jobs" ? viewState.jobs : viewState.runs;
  const params = new URLSearchParams();

  if (source.filterText.trim() !== "") {
    params.set("f", source.filterText.trim());
  }
  params.set("sort", source.sortKey);
  params.set("dir", source.sortDirection);

  const hash = `#/${routeKey}?${params.toString()}`;
  if (location.hash !== hash) {
    location.hash = hash;
  }
}

function parseHashRoute() {
  const raw = location.hash.replace(/^#\/?/, "");
  const separatorIndex = raw.indexOf("?");

  if (separatorIndex < 0) {
    return { path: raw, query: {} };
  }

  const path = raw.substring(0, separatorIndex);
  const queryString = raw.substring(separatorIndex + 1);
  const query = {};
  const params = new URLSearchParams(queryString);
  params.forEach((value, key) => {
    query[key] = value;
  });
  return { path, query };
}

function normalizeSortKey(routeKey, value, fallback) {
  if (!value) {
    return fallback;
  }
  return SORT_KEYS[routeKey].includes(value) ? value : fallback;
}

function normalizeDirection(value, fallback) {
  if (value === "asc" || value === "desc") {
    return value;
  }
  return fallback;
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

async function loadRunDetail(routeState) {
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
    document.getElementById("run-detail-start-time").textContent = valueOrDash(run.startTime);
    document.getElementById("run-detail-end-time").textContent = valueOrDash(run.endTime);
    document.getElementById("run-detail-duration").textContent = String(run.durationSeconds ?? "-");
    document.getElementById("run-detail-counts").textContent = `${valueOrDash(run.sourceCount)} / ${valueOrDash(run.writtenCount)} / ${valueOrDash(run.rejectedCount)}`;

    renderRunSteps(payload.steps);
    renderRunFailureSummary(payload.failureSummary);
    renderRunArtifacts(payload.artifacts);
    renderRunEvidenceLinks(payload.evidenceLinks);

    state.textContent = "Run detail loaded.";
    summary.hidden = false;
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load run detail: ${error.message}`;
  }
}

function renderRunSteps(steps) {
  const table = document.getElementById("run-detail-steps-table");
  const body = document.getElementById("run-detail-steps-body");
  const empty = document.getElementById("run-detail-steps-empty");
  const list = Array.isArray(steps) ? steps : [];

  body.innerHTML = "";
  if (list.length === 0) {
    table.hidden = true;
    empty.hidden = false;
    return;
  }

  list.forEach((step) => {
    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${escapeHtml(step.stepName || "-")}</td>
      <td>${escapeHtml(step.status || "-")}</td>
      <td>${escapeHtml(valueOrDash(step.readCount))}</td>
      <td>${escapeHtml(valueOrDash(step.writeCount))}</td>
      <td>${escapeHtml(valueOrDash(step.rejectedCount))}</td>`;
    body.appendChild(row);
  });

  empty.hidden = true;
  table.hidden = false;
}

function renderRunFailureSummary(failureSummary) {
  const empty = document.getElementById("run-detail-failure-empty");
  const box = document.getElementById("run-detail-failure-box");

  if (!failureSummary) {
    box.hidden = true;
    empty.hidden = false;
    return;
  }

  document.getElementById("run-detail-failure-category").textContent = valueOrDash(failureSummary.category);
  document.getElementById("run-detail-failure-type").textContent = valueOrDash(failureSummary.exceptionType);
  document.getElementById("run-detail-failure-message").textContent = valueOrDash(failureSummary.message);

  empty.hidden = true;
  box.hidden = false;
}

function renderRunArtifacts(artifacts) {
  const listElement = document.getElementById("run-detail-artifacts-list");
  const empty = document.getElementById("run-detail-artifacts-empty");
  const list = Array.isArray(artifacts) ? artifacts : [];

  listElement.innerHTML = "";
  if (list.length === 0) {
    listElement.hidden = true;
    empty.hidden = false;
    return;
  }

  list.forEach((artifact) => {
    const item = document.createElement("li");
    const parts = [valueOrDash(artifact.role), valueOrDash(artifact.path)];
    if (artifact.recordCount !== null && artifact.recordCount !== undefined) {
      parts.push(`records=${artifact.recordCount}`);
    }
    item.textContent = parts.join(" | ");
    listElement.appendChild(item);
  });

  empty.hidden = true;
  listElement.hidden = false;
}

function renderRunEvidenceLinks(evidenceLinks) {
  const listElement = document.getElementById("run-detail-evidence-list");
  const empty = document.getElementById("run-detail-evidence-empty");
  const list = Array.isArray(evidenceLinks) ? evidenceLinks : [];

  listElement.innerHTML = "";
  if (list.length === 0) {
    listElement.hidden = true;
    empty.hidden = false;
    return;
  }

  list.forEach((link) => {
    const item = document.createElement("li");
    const href = (link.href || "").trim();
    if (href) {
      const anchor = document.createElement("a");
      anchor.href = href;
      anchor.textContent = `${valueOrDash(link.label)} (${valueOrDash(link.type)})`;
      anchor.target = "_blank";
      anchor.rel = "noreferrer";
      item.appendChild(anchor);
    } else {
      item.textContent = `${valueOrDash(link.label)} (${valueOrDash(link.type)}) - no link target`;
    }
    listElement.appendChild(item);
  });

  empty.hidden = true;
  listElement.hidden = false;
}

function valueOrDash(value) {
  return value === null || value === undefined || value === "" ? "-" : String(value);
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#39;");
}



