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
    loadedForKey: "",
    items: [],
    cache: {
      all: null,
      byJob: {},
    },
    jobOptions: [],
    selectedJobKey: "",
    filterText: "",
    sortKey: "startTime",
    sortDirection: "desc",
  },
  runLog: {
    currentRunId: null,
    lines: [],
    truncated: false,
    searchText: "",
    structuredOnly: false,
    compact: true,
  },
};

const SORT_KEYS = {
  jobs: ["jobKey", "displayName", "readinessStatus"],
  runs: ["startTime", "jobExecutionId", "scenario", "status"],
};

window.addEventListener("hashchange", renderRoute);
window.addEventListener("DOMContentLoaded", () => {
  initializeControls();
  initializeRunLogControls();
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
      selectedJobKey: parsed.query.job || "",
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
  const triggerButton = document.getElementById("job-detail-trigger-now-btn");
  const triggerFeedback = document.getElementById("job-detail-trigger-feedback");
  const jobKeyValue = routeState && routeState.jobKey ? routeState.jobKey : null;

  state.className = "state";
  summary.hidden = true;
  triggerFeedback.hidden = true;
  triggerFeedback.className = "state";
  triggerFeedback.textContent = "";
  triggerButton.disabled = true;

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

    triggerButton.disabled = false;
    triggerButton.onclick = () => requestTriggerNow(jobKeyValue);

    state.textContent = "Job detail loaded.";
    summary.hidden = false;
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load job detail placeholder: ${error.message}`;
  }
}

async function requestTriggerNow(jobKeyValue) {
  const triggerButton = document.getElementById("job-detail-trigger-now-btn");
  const triggerFeedback = document.getElementById("job-detail-trigger-feedback");
  const triggerCount = document.getElementById("job-detail-trigger-count");

  if (!jobKeyValue) {
    triggerFeedback.className = "state error";
    triggerFeedback.textContent = "Unable to trigger: missing job key.";
    triggerFeedback.hidden = false;
    return;
  }

  const confirmed = window.confirm(
    "Trigger one ad hoc run now? This is operator convenience only and not schedule management."
  );
  if (!confirmed) {
    return;
  }

  triggerButton.disabled = true;
  triggerFeedback.className = "state";
  triggerFeedback.textContent = "Submitting trigger request...";
  triggerFeedback.hidden = false;

  try {
    const response = await fetch(`/api/v1/jobs/${encodeURIComponent(jobKeyValue)}:trigger-now`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({
        reason: "manual_operator_request",
        requestedBy: "operator-ui",
      }),
    });

    const payload = await response.json().catch(() => ({}));
    if (response.ok || response.status === 202) {
      const eventId = valueOrDash(payload.triggerEventId);
      triggerFeedback.className = "state";
      triggerFeedback.textContent = `Trigger accepted. decision=${valueOrDash(payload.decisionStatus)} triggerEventId=${eventId}`;
      triggerFeedback.hidden = false;

      const current = Number(triggerCount.textContent);
      if (!Number.isNaN(current)) {
        triggerCount.textContent = String(current + 1);
      }
      return;
    }

    const category = categorizeTriggerFailure(response.status);
    triggerFeedback.className = "state error";
    triggerFeedback.textContent = `Trigger failed [${category}] status=${response.status}: ${valueOrDash(payload.message)}`;
    triggerFeedback.hidden = false;
  } catch (error) {
    triggerFeedback.className = "state error";
    triggerFeedback.textContent = `Trigger failed [runtime]: ${error.message}`;
    triggerFeedback.hidden = false;
  } finally {
    triggerButton.disabled = false;
  }
}

function categorizeTriggerFailure(statusCode) {
  if (statusCode === 404 || statusCode === 409) {
    return "config";
  }
  if (statusCode >= 400 && statusCode < 500) {
    return "validation";
  }
  return "runtime";
}

async function loadRuns() {
  const state = document.getElementById("runs-state");
  const table = document.getElementById("runs-table");
  const body = document.getElementById("runs-body");
  const selectedJobKey = viewState.runs.selectedJobKey || "";
  const loadKey = selectedJobKey || "__all__";

  if (viewState.runs.loaded && viewState.runs.loadedForKey === loadKey) {
    renderRunsTable();
    return;
  }

  state.className = "state";
  state.textContent = selectedJobKey
    ? `Loading runs for job ${selectedJobKey}...`
    : "Loading runs...";
  table.hidden = true;
  body.innerHTML = "";

  try {
    await ensureRunsJobOptions();
    const runs = await fetchRunsForSelectedJob(selectedJobKey);
    viewState.runs.items = runs;
    viewState.runs.loaded = true;
    viewState.runs.loadedForKey = loadKey;

    if (viewState.runs.items.length === 0) {
      state.textContent = selectedJobKey
        ? `No recent runs found for job '${selectedJobKey}'.`
        : "No recent runs found.";
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
  const runsJobSelect = document.getElementById("runs-job-select");
  const runsSort = document.getElementById("runs-sort-select");
  const runsDirection = document.getElementById("runs-sort-dir-btn");

  runsJobSelect.addEventListener("change", (event) => {
    viewState.runs.selectedJobKey = event.target.value || "";
    viewState.runs.loaded = false;
    syncListRouteHash("runs");
  });
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
  viewState.runs.selectedJobKey = routeState.selectedJobKey || "";
  viewState.runs.sortKey = routeState.sortKey || "startTime";
  viewState.runs.sortDirection = routeState.sortDirection || "desc";

  renderRunsJobOptions();
  document.getElementById("runs-job-select").value = viewState.runs.selectedJobKey;
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
  if (source.selectedJobKey && source.selectedJobKey.trim() !== "") {
    params.set("job", source.selectedJobKey.trim());
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
    state.textContent = viewState.runs.selectedJobKey
      ? `No runs match the current filter for job '${viewState.runs.selectedJobKey}'.`
      : "No runs match the current filter.";
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

  const scopeSuffix = viewState.runs.selectedJobKey
    ? ` for job '${viewState.runs.selectedJobKey}'`
    : "";
  state.textContent = `Showing ${sorted.length} of ${viewState.runs.items.length} run(s)${scopeSuffix}.`;
  table.hidden = false;
}

async function ensureRunsJobOptions() {
  if (viewState.runs.jobOptions.length > 0) {
    return;
  }

  const jobs = viewState.jobs.loaded
    ? viewState.jobs.items
    : await fetchJobsForRunsScope();

  viewState.runs.jobOptions = jobs.map((job) => ({
    jobKey: job.jobKey,
    displayName: job.displayName,
  }));
  renderRunsJobOptions();
}

function renderRunsJobOptions() {
  const select = document.getElementById("runs-job-select");
  if (!select) {
    return;
  }

  const selected = viewState.runs.selectedJobKey || "";
  select.innerHTML = '<option value="">All jobs</option>';

  const options = [...viewState.runs.jobOptions].sort((left, right) =>
    String(left.displayName || left.jobKey || "").localeCompare(String(right.displayName || right.jobKey || ""))
  );

  options.forEach((job) => {
    const option = document.createElement("option");
    option.value = job.jobKey || "";
    option.textContent = job.displayName
      ? `${job.displayName} (${job.jobKey})`
      : (job.jobKey || "-");
    select.appendChild(option);
  });

  select.value = selected;
}

async function fetchJobsForRunsScope() {
  const response = await fetch("/api/v1/jobs", { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`Jobs API returned ${response.status}`);
  }
  const payload = await response.json();
  const jobs = Array.isArray(payload.items) ? payload.items : [];
  viewState.jobs.items = jobs;
  viewState.jobs.loaded = true;
  return jobs;
}

async function fetchRunsForSelectedJob(selectedJobKey) {
  if (!selectedJobKey) {
    if (Array.isArray(viewState.runs.cache.all)) {
      return viewState.runs.cache.all;
    }
    const response = await fetch("/api/v1/runs?limit=25", { headers: { Accept: "application/json" } });
    if (!response.ok) {
      throw new Error(`Runs API returned ${response.status}`);
    }
    const payload = await response.json();
    const items = Array.isArray(payload.items) ? payload.items : [];
    viewState.runs.cache.all = items;
    return items;
  }

  if (Array.isArray(viewState.runs.cache.byJob[selectedJobKey])) {
    return viewState.runs.cache.byJob[selectedJobKey];
  }

  const response = await fetch(`/api/v1/jobs/${encodeURIComponent(selectedJobKey)}`, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(`Job detail API returned ${response.status}`);
  }
  const payload = await response.json();
  const runs = Array.isArray(payload.recentRuns) ? payload.recentRuns : [];
  viewState.runs.cache.byJob[selectedJobKey] = runs;
  return runs;
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
  const logState = document.getElementById("run-detail-log-state");
  const logList = document.getElementById("run-detail-log-list");
  const logEmpty = document.getElementById("run-detail-log-empty");
  const logControls = document.getElementById("run-detail-log-controls");
  const runIdValue = routeState && routeState.jobExecutionId ? routeState.jobExecutionId : null;

  state.className = "state";
  summary.hidden = true;
  logState.hidden = true;
  logList.hidden = true;
  logEmpty.hidden = true;
  logControls.hidden = true;

  if (!runIdValue) {
    state.className = "state error";
    state.textContent = "Missing run id in route. Use a row in Runs list.";
    return;
  }

  resetRunLogContext(runIdValue);

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
    await loadRunScopedLog(runIdValue);

    state.textContent = "Run detail loaded.";
    summary.hidden = false;
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load run detail: ${error.message}`;
  }
}

function resetRunLogContext(runIdValue) {
  const normalizedRunId = String(runIdValue || "");
  if (viewState.runLog.currentRunId === normalizedRunId) {
    return;
  }

  viewState.runLog.currentRunId = normalizedRunId;
  viewState.runLog.lines = [];
  viewState.runLog.truncated = false;
  viewState.runLog.searchText = "";

  const searchInput = document.getElementById("run-detail-log-search");
  if (searchInput) {
    searchInput.value = "";
  }
}

async function loadRunScopedLog(runIdValue) {
  const logState = document.getElementById("run-detail-log-state");
  const logList = document.getElementById("run-detail-log-list");
  const logEmpty = document.getElementById("run-detail-log-empty");
  const logControls = document.getElementById("run-detail-log-controls");

  logState.className = "state";
  logState.textContent = "Loading run-scoped log lines...";
  logState.hidden = false;
  logList.hidden = true;
  logEmpty.hidden = true;
  logList.innerHTML = "";
  logControls.hidden = true;

  try {
    const response = await fetch(`/api/v1/runs/${encodeURIComponent(runIdValue)}/log?limit=200`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Run log API returned ${response.status}`);
    }
    const payload = await response.json();
    viewState.runLog.lines = Array.isArray(payload.lines) ? payload.lines : [];
    viewState.runLog.truncated = Boolean(payload.truncated);
    logControls.hidden = false;
    renderRunLogLines();
  } catch (error) {
    logState.className = "state error";
    logState.textContent = `Unable to load run-scoped logs: ${error.message}`;
  }
}

function renderRunLogLines() {
  const logState = document.getElementById("run-detail-log-state");
  const logList = document.getElementById("run-detail-log-list");
  const logEmpty = document.getElementById("run-detail-log-empty");
  const allLines = Array.isArray(viewState.runLog.lines) ? viewState.runLog.lines : [];
  const searchTerm = (viewState.runLog.searchText || "").trim().toLowerCase();
  const filtered = allLines.filter((line) => {
    if (viewState.runLog.structuredOnly && !line.structured) {
      return false;
    }
    if (!searchTerm) {
      return true;
    }
    const haystack = `${valueOrDash(line.message)} ${valueOrDash(line.recordType)} ${valueOrDash(line.event)} ${valueOrDash(line.level)}`.toLowerCase();
    return haystack.includes(searchTerm);
  });
  const list = filtered;

  logList.innerHTML = "";
  if (list.length === 0) {
    logState.className = "state";
    logState.hidden = false;
    logState.textContent = allLines.length === 0 ? "No run-scoped log lines returned." : "No run-scoped log lines match the current filter.";
    logList.hidden = true;
    logEmpty.hidden = false;
    return;
  }

  list.forEach((line) => {
    const row = document.createElement("div");
    row.className = `log-line ${line.structured ? "structured" : "raw"}`;

    const level = (line.level || "").toUpperCase();
    const recordType = valueOrDash(line.recordType);
    const event = valueOrDash(line.event);
    const lineNumber = valueOrDash(line.lineNumber);
    const timestamp = valueOrDash(line.loggedAt);

    const fullMessage = valueOrDash(line.message);
    const renderedMessage = viewState.runLog.compact ? truncateForCompact(fullMessage, 240) : fullMessage;
    row.innerHTML = `
      <div class="log-line-meta">
        <span class="log-chip level-${escapeHtml(level.toLowerCase())}">${escapeHtml(level || "RAW")}</span>
        <span class="log-chip">L${escapeHtml(lineNumber)}</span>
        <span class="log-chip">${escapeHtml(recordType)}</span>
        <span class="log-chip">${escapeHtml(event)}</span>
        <span class="log-time">${escapeHtml(timestamp)}</span>
      </div>
      <pre class="log-line-text ${viewState.runLog.compact ? "compact" : ""}" title="${escapeHtml(fullMessage)}">${escapeHtml(renderedMessage)}</pre>`;

    logList.appendChild(row);
  });

  logEmpty.hidden = true;
  logList.hidden = false;
  logState.hidden = false;
  logState.className = "state";
  const baseMessage = viewState.runLog.truncated
    ? `Showing ${list.length} of ${allLines.length} loaded line(s) (source truncated server-side).`
    : `Showing ${list.length} of ${allLines.length} loaded line(s).`;
  logState.textContent = baseMessage;
}

function initializeRunLogControls() {
  const searchInput = document.getElementById("run-detail-log-search");
  const structuredOnly = document.getElementById("run-detail-log-structured-only");
  const compact = document.getElementById("run-detail-log-compact");

  searchInput.addEventListener("input", (event) => {
    viewState.runLog.searchText = event.target.value || "";
    renderRunLogLines();
  });
  structuredOnly.addEventListener("change", (event) => {
    viewState.runLog.structuredOnly = Boolean(event.target.checked);
    renderRunLogLines();
  });
  compact.addEventListener("change", (event) => {
    viewState.runLog.compact = Boolean(event.target.checked);
    renderRunLogLines();
  });
}

function truncateForCompact(value, maxLength) {
  const text = valueOrDash(value);
  if (text.length <= maxLength) {
    return text;
  }
  return `${text.substring(0, maxLength)}...`;
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
    if (href && String(link.type || "").toLowerCase() === "log-file") {
      const scopedAnchor = document.createElement("a");
      scopedAnchor.href = "#";
      scopedAnchor.textContent = "Run log (scoped viewer)";
      scopedAnchor.addEventListener("click", (event) => {
        event.preventDefault();
        focusRunScopedLogViewer();
      });
      item.appendChild(scopedAnchor);

      item.appendChild(document.createTextNode(" | "));

      const rawAnchor = document.createElement("a");
      rawAnchor.href = href;
      rawAnchor.textContent = "Full scenario log (raw file)";
      rawAnchor.target = "_blank";
      rawAnchor.rel = "noreferrer";
      item.appendChild(rawAnchor);
    } else if (href) {
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

function focusRunScopedLogViewer() {
  const controls = document.getElementById("run-detail-log-controls");
  const search = document.getElementById("run-detail-log-search");
  const list = document.getElementById("run-detail-log-list");
  const target = controls.hidden ? list : controls;

  if (target && typeof target.scrollIntoView === "function") {
    target.scrollIntoView({ behavior: "smooth", block: "start" });
  }
  if (search && typeof search.focus === "function") {
    search.focus();
  }
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



