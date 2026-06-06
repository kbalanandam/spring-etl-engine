import { createRunLogViewer } from "./run-log-viewer.js";
import { createJobsListUi } from "./jobs-list-ui.js";
import { createRunsListUi } from "./runs-list-ui.js";
import { createRunRecoveryPanel } from "./run-recovery-panel.js";
import { coalesceRunSteps } from "./run-step-dedupe.js";

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
  jobConfig: {
    tab: document.getElementById("tab-jobs"),
    view: document.getElementById("view-job-config"),
    load: loadJobConfig,
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
      byFilter: {},
    },
    jobOptions: [],
    selectedJobKey: "",
    runModeFilter: "",
    recoveryPolicyFilter: "",
    startDate: "",
    timezone: "",
    browserTimezone: "UTC",
    filterText: "",
    sortKey: "startTime",
    sortDirection: "desc",
  },
};

const runLogViewer = createRunLogViewer({
  valueOrDash,
  escapeHtml,
});

const runRecoveryPanel = createRunRecoveryPanel({
  valueOrDash,
});

const jobsListUi = createJobsListUi({
  getState: () => viewState.jobs,
  syncRouteHash: syncListRouteHash,
  escapeHtml,
});

const runsListUi = createRunsListUi({
  getState: () => viewState.runs,
  syncRouteHash: syncListRouteHash,
  renderJobOptions: renderRunsJobOptions,
  formatDateForInput,
  escapeHtml,
});

const SORT_KEYS = {
  jobs: ["jobKey", "displayName", "readinessStatus"],
  runs: ["startTime", "jobExecutionId", "scenario", "status", "runMode", "recoveryPolicy"],
};

window.addEventListener("hashchange", renderRoute);
window.addEventListener("DOMContentLoaded", () => {
  initializeRunsDefaults();
  initializeControls();
  runLogViewer.initializeControls();
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
  const jobConfigMatch = path.match(/^jobs\/([^/]+)\/config$/i);
  const jobDetailMatch = path.match(/^jobs\/([^/]+)$/i);

  if (jobConfigMatch) {
    return { key: "jobConfig", jobExecutionId: null, jobKey: decodeURIComponent(jobConfigMatch[1]), query: parsed.query };
  }

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
      runModeFilter: parsed.query.runMode || "",
      recoveryPolicyFilter: parsed.query.recoveryPolicy || "",
      startDate: parsed.query.startDate || "",
      timezone: parsed.query.timezone || "",
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
    jobsListUi.renderTable();
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
    jobsListUi.renderTable();
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
  const viewConfigLink = document.getElementById("job-detail-view-config-link");
  const jobKeyValue = routeState && routeState.jobKey ? routeState.jobKey : null;

  state.className = "state";
  summary.hidden = true;
  triggerFeedback.hidden = true;
  triggerFeedback.className = "state";
  triggerFeedback.textContent = "";
  triggerButton.disabled = true;
  if (viewConfigLink) {
    viewConfigLink.setAttribute("href", "#/jobs");
  }

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
    if (viewConfigLink) {
      viewConfigLink.setAttribute("href", `#/jobs/${encodeURIComponent(jobKeyValue)}/config`);
    }

    triggerButton.disabled = false;
    triggerButton.onclick = () => requestTriggerNow(jobKeyValue);

    state.textContent = "Job detail loaded.";
    summary.hidden = false;
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load job detail placeholder: ${error.message}`;
  }
}

async function loadJobConfig(routeState) {
  const state = document.getElementById("job-config-state");
  const summary = document.getElementById("job-config-summary");
  const raw = document.getElementById("job-config-raw");
  const backLink = document.getElementById("job-config-back-link");
  const jobKeyValue = routeState && routeState.jobKey ? routeState.jobKey : null;

  state.className = "state";
  summary.hidden = true;
  raw.hidden = true;
  raw.textContent = "";
  if (backLink) {
    backLink.setAttribute("href", "#/jobs");
  }

  if (!jobKeyValue) {
    state.className = "state error";
    state.textContent = "Missing job key in route. Use a job detail link.";
    return;
  }

  state.textContent = `Loading config for ${jobKeyValue}...`;

  try {
    const response = await fetch(`/api/v1/jobs/${encodeURIComponent(jobKeyValue)}/config`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Job config API returned ${response.status}`);
    }
    const payload = await response.json();
    document.getElementById("job-config-key").textContent = payload.jobKey || jobKeyValue;
    document.getElementById("job-config-name").textContent = payload.displayName || "-";
    document.getElementById("job-config-path").textContent = payload.jobConfigPath || "-";
    raw.textContent = payload.rawYaml || "";
    if (backLink) {
      backLink.setAttribute("href", `#/jobs/${encodeURIComponent(jobKeyValue)}`);
    }

    summary.hidden = false;
    raw.hidden = false;
    state.textContent = "Job config loaded.";
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load job config: ${error.message}`;
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
  const selectedRunMode = viewState.runs.runModeFilter || "";
  const selectedRecoveryPolicy = viewState.runs.recoveryPolicyFilter || "";
  const selectedStartDate = viewState.runs.startDate || "";
  const selectedTimezone = viewState.runs.timezone || viewState.runs.browserTimezone || "UTC";
  const loadKey = `${selectedJobKey || "__all__"}|${selectedRunMode || "__all_mode__"}|${selectedRecoveryPolicy || "__all_policy__"}|${selectedStartDate || "__no_date__"}|${selectedTimezone}`;

  if (viewState.runs.loaded && viewState.runs.loadedForKey === loadKey) {
    runsListUi.renderTable();
    return;
  }

  state.className = "state";
  state.textContent = "Loading runs...";
  table.hidden = true;
  body.innerHTML = "";
  runsListUi.clearInstanceOptions();

  try {
    await ensureRunsJobOptions();
    const runs = await fetchRunsForFilters(selectedJobKey, selectedRunMode, selectedRecoveryPolicy, selectedStartDate, selectedTimezone);
    viewState.runs.items = runs;
    viewState.runs.loaded = true;
    viewState.runs.loadedForKey = loadKey;

    if (viewState.runs.items.length === 0) {
      state.textContent = "No runs found for the selected filters.";
      return;
    }
    runsListUi.renderTable();
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load runs: ${error.message}`;
  }
}

function initializeRunsDefaults() {
  const browserTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  viewState.runs.browserTimezone = browserTimezone;
  if (!viewState.runs.timezone) {
    viewState.runs.timezone = browserTimezone;
  }
  if (!viewState.runs.startDate) {
    viewState.runs.startDate = formatDateForInput(new Date());
  }
}

function formatDateForInput(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function initializeControls() {
  jobsListUi.initializeControls();
  runsListUi.initializeControls();
}

function applyRouteStateToListView(routeState) {
  if (routeState.key === "jobs") {
    jobsListUi.applyRouteState(routeState);
    return;
  }

  runsListUi.applyRouteState(routeState);
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
  if (routeKey === "runs") {
    if (source.runModeFilter && source.runModeFilter.trim() !== "") {
      params.set("runMode", source.runModeFilter.trim());
    }
    if (source.recoveryPolicyFilter && source.recoveryPolicyFilter.trim() !== "") {
      params.set("recoveryPolicy", source.recoveryPolicyFilter.trim());
    }
  }
  if (routeKey === "runs") {
    if (source.startDate && source.startDate.trim() !== "") {
      params.set("startDate", source.startDate.trim());
    }
    if (source.timezone && source.timezone.trim() !== "") {
      params.set("timezone", source.timezone.trim());
    }
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

async function fetchRunsForFilters(selectedJobKey, runMode, recoveryPolicy, startDate, timezone) {
  const cacheKey = `${selectedJobKey || ""}|${runMode || ""}|${recoveryPolicy || ""}|${startDate || ""}|${timezone || ""}`;
  if (Array.isArray(viewState.runs.cache.byFilter[cacheKey])) {
    return viewState.runs.cache.byFilter[cacheKey];
  }

  const params = new URLSearchParams();
  params.set("limit", "200");
  if (selectedJobKey) {
    params.set("job", selectedJobKey);
  }
  if (runMode) {
    params.set("runMode", runMode);
  }
  if (recoveryPolicy) {
    params.set("recoveryPolicy", recoveryPolicy);
  }
  if (startDate) {
    params.set("startDate", startDate);
  }
  if (timezone) {
    params.set("timezone", timezone);
  }

  const response = await fetch(`/api/v1/runs?${params.toString()}`, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`Runs API returned ${response.status}`);
  }
  const payload = await response.json();
  const items = Array.isArray(payload.items) ? payload.items : [];
  viewState.runs.cache.byFilter[cacheKey] = items;
  return items;
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

  runLogViewer.resetContext(runIdValue);

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
    document.getElementById("run-detail-run-mode").textContent = valueOrDash(run.runMode);
    document.getElementById("run-detail-recovery-policy").textContent = valueOrDash(run.recoveryPolicy);
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

    const recoveryPromise = fetchRunRecovery(runIdValue)
      .then((recovery) => {
        runRecoveryPanel.render(recovery);
      })
      .catch(() => {
        runRecoveryPanel.render(null);
      });

    const persistedRecordsPromise = Promise.all([
      fetchPersistedRunStepRecords(runIdValue),
      fetchPersistedRunArtifactRecords(runIdValue),
    ]).then(([persistedStepRecords, persistedArtifactRecords]) => {
      const stepItems = Array.isArray(persistedStepRecords) && persistedStepRecords.length > 0
        ? mapPersistedStepRecordsToDetailView(persistedStepRecords)
        : payload.steps;
      const artifactItems = Array.isArray(persistedArtifactRecords) && persistedArtifactRecords.length > 0
        ? mapPersistedArtifactRecordsToDetailView(persistedArtifactRecords)
        : payload.artifacts;

      renderRunSteps(stepItems);
      renderRunArtifacts(artifactItems);
    });

    await Promise.all([
      recoveryPromise,
      persistedRecordsPromise,
      runLogViewer.load(runIdValue),
    ]);
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load run detail: ${error.message}`;
  }
}

async function fetchRunRecovery(runIdValue) {
  const response = await fetch(`/api/v1/runs/${encodeURIComponent(runIdValue)}/recovery`, {
    headers: { Accept: "application/json" },
  });
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Run recovery API returned ${response.status}`);
  }
  const payload = await response.json();
  return payload.recovery || null;
}

async function fetchPersistedRunStepRecords(runIdValue) {
  try {
    const response = await fetch(`/api/v1/runs/${encodeURIComponent(runIdValue)}/step-records?limit=200`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      return null;
    }
    const payload = await response.json();
    return Array.isArray(payload.items) ? payload.items : [];
  } catch (error) {
    return null;
  }
}

async function fetchPersistedRunArtifactRecords(runIdValue) {
  try {
    const response = await fetch(`/api/v1/runs/${encodeURIComponent(runIdValue)}/artifact-records?limit=200`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      return null;
    }
    const payload = await response.json();
    return Array.isArray(payload.items) ? payload.items : [];
  } catch (error) {
    return null;
  }
}

function mapPersistedStepRecordsToDetailView(records) {
  return records.map((record) => ({
    stepName: record.stepName,
    status: record.stepStatus,
    readCount: record.readCount,
    writeCount: record.writeCount,
    rejectedCount: record.rejectedCount,
  }));
}

function mapPersistedArtifactRecordsToDetailView(records) {
  return records.map((record) => ({
    role: record.artifactRole,
    path: record.artifactPath,
    recordCount: null,
  }));
}

function focusRunScopedLogViewer() {
  runLogViewer.focus();
}

function renderRunSteps(steps) {
  const table = document.getElementById("run-detail-steps-table");
  const body = document.getElementById("run-detail-steps-body");
  const empty = document.getElementById("run-detail-steps-empty");
  const list = coalesceRunSteps(steps);

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
