import { createRunLogViewer } from "./run-log-viewer.js";
import { createJobsListUi } from "./jobs-list-ui.js";
import { createRunsListUi } from "./runs-list-ui.js";
import { createRunRecoveryPanel } from "./run-recovery-panel.js";
import { coalesceRunSteps } from "./run-step-dedupe.js";
import {
  buildJobConfigDocuments,
  buildMissingCompanionWarning,
  normalizeDocumentKey,
  pickJobConfigDocument,
} from "./job-config-files.js";

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
  schedules: {
    tab: document.getElementById("tab-schedules"),
    view: document.getElementById("view-schedules"),
    load: loadSchedules,
  },
  scheduleDetail: {
    tab: document.getElementById("tab-schedules"),
    view: document.getElementById("view-schedule-detail"),
    load: loadScheduleDetail,
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

const JOBS_PAGE_SIZE_OPTIONS = [8, 10, 15, 20];

const viewState = {
  jobs: {
    loaded: false,
    items: [],
    filterText: "",
    sortKey: "jobKey",
    sortDirection: "asc",
    page: 1,
    pageSize: defaultJobsPageSize(),
    expandedJobKey: "",
    jobStepPreviewByJobKey: {},
    stepNamesByJobKey: {},
  },
  schedules: {
    loaded: false,
    items: [],
    selectedScheduleId: "",
    evidenceRequestId: 0,
  },
  runs: {
    loaded: false,
    loadedForKey: "",
    items: [],
    cache: {
      byFilter: {},
      order: [],
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
  getRouteSuffix: getJobsRouteQuerySuffix,
  loadJobStepNames,
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
  runs: ["startTime", "jobExecutionId", "scenario", "status", "triggerOrigin", "runMode", "recoveryPolicy"],
};

const RUNS_FILTER_CACHE_MAX_ENTRIES = 30;
const RUNS_FILTER_CACHE_TTL_MS = 5 * 60 * 1000;
const TRIGGER_NOW_DUPLICATE_WINDOW_MS = 5 * 1000;
const DEFAULT_SCHEDULE_LOOKUP_LIMIT = 200;
const JOB_DETAIL_RECENT_RUNS_LIMIT = 10;

const loadRequestTracker = {
  jobs: 0,
  jobDetail: 0,
  jobConfig: 0,
  schedules: 0,
  scheduleDetail: 0,
  runs: 0,
  runDetail: 0,
};

const inFlightStepNamesByJobKey = {};
const triggerNowRequestState = {
  inFlightByJobKey: {},
  cooldownUntilByJobKey: {},
};
const scheduleRequestState = {
  inFlightActionByScheduleId: {},
  inFlightTriggerByScheduleId: {},
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
  const scheduleDetailMatch = path.match(/^schedules\/([^/]+)$/i);
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
  if (scheduleDetailMatch) {
    return {
      key: "scheduleDetail",
      jobExecutionId: null,
      jobKey: null,
      query: parsed.query,
      selectedScheduleId: decodeURIComponent(scheduleDetailMatch[1]),
    };
  }
  if (normalized === "schedules") {
    return {
      key: "schedules",
      jobExecutionId: null,
      jobKey: null,
      query: parsed.query,
      selectedScheduleId: parsed.query.scheduleId || "",
    };
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
    page: normalizePositiveInteger(parsed.query.page, 1),
    pageSize: normalizePageSize(parsed.query.pageSize, defaultJobsPageSize()),
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
  if (routeKey === "schedules") {
    viewState.schedules.selectedScheduleId = String(routeState.selectedScheduleId || "").trim() || viewState.schedules.selectedScheduleId;
  }

  Object.entries(routes).forEach(([key, route]) => {
    const active = key === routeKey;
    route.tab.classList.toggle("active", active);
    route.view.hidden = !active;
  });

  routes[routeKey].load(routeState);
}

async function loadJobs() {
  const requestId = ++loadRequestTracker.jobs;
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
    if (!shouldApplyRouteScopedUpdate("jobs", requestId)) {
      return;
    }

    const items = Array.isArray(payload.items) ? payload.items : [];
    const jobsWithScheduleReadiness = await applyScheduledReadiness(items);
    if (!shouldApplyRouteScopedUpdate("jobs", requestId)) {
      return;
    }
    applyJobsItems(jobsWithScheduleReadiness);
    viewState.jobs.loaded = true;

    if (viewState.jobs.items.length === 0) {
      state.textContent = "No job bundles found.";
      return;
    }
    jobsListUi.renderTable();
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("jobs", requestId)) {
      return;
    }
    state.className = "state error";
    state.textContent = `Unable to load jobs: ${error.message}`;
  }
}

async function applyScheduledReadiness(items) {
  const jobs = Array.isArray(items) ? items : [];
  try {
    const response = await fetch(`/api/v1/schedules?limit=${DEFAULT_SCHEDULE_LOOKUP_LIMIT}`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      return jobs;
    }
    const payload = await response.json();
    const schedules = Array.isArray(payload.items) ? payload.items : [];
    const scheduledJobKeys = new Set(
      schedules
        .filter((schedule) => Boolean(schedule?.enabled))
        .map((schedule) => String(schedule?.selectedJobKey || "").trim().toLowerCase())
        .filter((jobKey) => jobKey !== "")
    );

    return jobs.map((job) => {
      const jobKey = String(job?.jobKey || "").trim().toLowerCase();
      if (!scheduledJobKeys.has(jobKey)) {
        return job;
      }

      const readiness = String(job?.readinessStatus || "").trim().toUpperCase();
      if (readiness === "INVALID" || readiness === "INACTIVE") {
        return job;
      }

      return {
        ...job,
        readinessStatus: "SCHEDULED",
      };
    });
  } catch {
    return jobs;
  }
}

async function loadJobStepNames(jobKey) {
  const normalizedJobKey = String(jobKey || "").trim();
  if (!normalizedJobKey) {
    return [];
  }

  if (Array.isArray(viewState.jobs.stepNamesByJobKey[normalizedJobKey])) {
    return viewState.jobs.stepNamesByJobKey[normalizedJobKey];
  }

  if (inFlightStepNamesByJobKey[normalizedJobKey]) {
    return inFlightStepNamesByJobKey[normalizedJobKey];
  }

  const requestPromise = fetch(`/api/v1/jobs/${encodeURIComponent(normalizedJobKey)}/config`, {
    headers: { Accept: "application/json" },
  })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`Job config API returned ${response.status}`);
      }
      return response.json();
    })
    .then((payload) => {
      const stepNames = extractStepNamesFromRawYaml(payload.rawYaml);
      viewState.jobs.stepNamesByJobKey[normalizedJobKey] = stepNames;
      return stepNames;
    })
    .finally(() => {
      if (inFlightStepNamesByJobKey[normalizedJobKey] === requestPromise) {
        delete inFlightStepNamesByJobKey[normalizedJobKey];
      }
    });

  inFlightStepNamesByJobKey[normalizedJobKey] = requestPromise;
  return requestPromise;
}

function extractStepNamesFromRawYaml(rawYaml) {
  const text = typeof rawYaml === "string" ? rawYaml : "";
  if (text.trim() === "") {
    return [];
  }

  const lines = text.split(/\r?\n/);
  const stepNames = [];
  let inStepsBlock = false;
  let stepsIndent = 0;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === "" || trimmed.startsWith("#")) {
      continue;
    }

    if (!inStepsBlock) {
      const stepsMatch = line.match(/^(\s*)steps\s*:\s*$/);
      if (stepsMatch) {
        inStepsBlock = true;
        stepsIndent = stepsMatch[1].length;
      }
      continue;
    }

    const indent = line.length - line.trimStart().length;
    if (indent <= stepsIndent && !line.trimStart().startsWith("-")) {
      break;
    }

    const stepNameMatch = line.match(/^\s*-\s*name\s*:\s*(.+)\s*$/);
    if (!stepNameMatch) {
      continue;
    }

    let stepName = stepNameMatch[1].trim();
    if ((stepName.startsWith('"') && stepName.endsWith('"')) || (stepName.startsWith("'") && stepName.endsWith("'"))) {
      stepName = stepName.substring(1, stepName.length - 1).trim();
    }
    if (stepName !== "") {
      stepNames.push(stepName);
    }
  }

  return Array.from(new Set(stepNames));
}

async function loadJobDetailPlaceholder(routeState) {
  const requestId = ++loadRequestTracker.jobDetail;
  const state = document.getElementById("job-detail-state");
  const summary = document.getElementById("job-detail-summary");
  const triggerButton = document.getElementById("job-detail-trigger-now-btn");
  const triggerFeedback = document.getElementById("job-detail-trigger-feedback");
  const scheduleState = document.getElementById("job-detail-schedule-state");
  const scheduleSummary = document.getElementById("job-detail-schedule-summary");
  const scheduleActionButton = document.getElementById("job-detail-schedule-action-btn");
  const scheduleFeedback = document.getElementById("job-detail-schedule-feedback");
  const recentRunsState = document.getElementById("job-detail-recent-runs-state");
  const recentRunsList = document.getElementById("job-detail-recent-runs-list");
  const viewConfigLink = document.getElementById("job-detail-view-config-link");
  const backLink = document.getElementById("job-detail-back-link");
  const jobKeyValue = routeState && routeState.jobKey ? routeState.jobKey : null;
  const navigationSource = String(routeState?.query?.from || "").trim().toLowerCase();
  const sourceScheduleId = String(routeState?.query?.scheduleId || "").trim();
  const jobsRouteQuerySuffix = getQuerySuffix(routeState && routeState.query);

  state.className = "state";
  summary.hidden = true;
  triggerFeedback.hidden = true;
  triggerFeedback.className = "state";
  triggerFeedback.textContent = "";
  triggerButton.disabled = true;
  if (scheduleState) {
    scheduleState.className = "state";
    scheduleState.textContent = "Loading native schedule...";
  }
  if (scheduleSummary) {
    scheduleSummary.hidden = true;
  }
  if (scheduleActionButton) {
    scheduleActionButton.disabled = true;
    scheduleActionButton.textContent = "Pause schedule";
    scheduleActionButton.onclick = null;
  }
  if (scheduleFeedback) {
    scheduleFeedback.hidden = true;
    scheduleFeedback.className = "state";
    scheduleFeedback.textContent = "";
  }
  if (viewConfigLink) {
    viewConfigLink.setAttribute("href", `#/jobs${jobsRouteQuerySuffix}`);
  }
  if (recentRunsState) {
    recentRunsState.className = "state";
    recentRunsState.textContent = "Loading recent runs...";
  }
  if (recentRunsList) {
    recentRunsList.hidden = true;
    recentRunsList.innerHTML = "";
  }
  if (backLink) {
    if (navigationSource === "schedule" && sourceScheduleId !== "") {
      backLink.setAttribute("href", `#/schedules/${encodeURIComponent(sourceScheduleId)}`);
      backLink.textContent = "Back to schedules";
    } else {
      backLink.setAttribute("href", `#/jobs${jobsRouteQuerySuffix}`);
      backLink.textContent = "Back to jobs list";
    }
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
    if (!shouldApplyRouteScopedUpdate("jobDetail", requestId, jobKeyValue)) {
      return;
    }

    const job = payload.job || {};

    document.getElementById("job-detail-key").textContent = job.jobKey || jobKeyValue;
    document.getElementById("job-detail-name").textContent = job.displayName || "-";
    document.getElementById("job-detail-readiness").textContent = job.readinessStatus || "-";
    document.getElementById("job-detail-recent-run-count").textContent = String(Array.isArray(payload.recentRuns) ? payload.recentRuns.length : 0);
    document.getElementById("job-detail-trigger-count").textContent = String(Array.isArray(payload.triggerEvents) ? payload.triggerEvents.length : 0);
    renderJobDetailRecentRuns(payload.recentRuns, jobKeyValue, routeState?.query);
    if (viewConfigLink) {
      viewConfigLink.setAttribute("href", `#/jobs/${encodeURIComponent(jobKeyValue)}/config${jobsRouteQuerySuffix}`);
    }

    triggerButton.disabled = false;
    triggerButton.onclick = () => requestTriggerNow(jobKeyValue);
    await loadJobDetailSchedulePanel(jobKeyValue, requestId);

    state.textContent = "Job detail loaded.";
    summary.hidden = false;
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("jobDetail", requestId, jobKeyValue)) {
      return;
    }
    state.className = "state error";
    state.textContent = `Unable to load job detail placeholder: ${error.message}`;
  }
}

function renderJobDetailRecentRuns(recentRuns, jobKeyValue, query) {
  const recentRunsState = document.getElementById("job-detail-recent-runs-state");
  const recentRunsList = document.getElementById("job-detail-recent-runs-list");
  if (!recentRunsState || !recentRunsList) {
    return;
  }

  const list = Array.isArray(recentRuns) ? recentRuns : [];
  const visibleRuns = list.slice(0, JOB_DETAIL_RECENT_RUNS_LIMIT);
  recentRunsList.innerHTML = "";

  if (visibleRuns.length === 0) {
    recentRunsState.className = "state";
    recentRunsState.textContent = "No recent runs found for this job.";
    recentRunsList.hidden = true;
    return;
  }

  visibleRuns.forEach((run) => {
    const item = document.createElement("li");
    const runId = String(run?.jobExecutionId || "").trim();
    if (runId !== "") {
      const anchor = document.createElement("a");
      const params = new URLSearchParams();
      params.set("from", "job");
      params.set("job", String(jobKeyValue || ""));
      const source = String(query?.from || "").trim().toLowerCase();
      const sourceScheduleId = String(query?.scheduleId || "").trim();
      if (source === "schedule" && sourceScheduleId !== "") {
        params.set("scheduleId", sourceScheduleId);
      }
      anchor.href = `#/runs/${encodeURIComponent(runId)}?${params.toString()}`;
      anchor.textContent = formatJobDetailRecentRunLabel(run);
      item.appendChild(anchor);
    } else {
      item.textContent = formatJobDetailRecentRunLabel(run);
    }
    recentRunsList.appendChild(item);
  });

  recentRunsState.className = "state";
  recentRunsState.textContent = `Showing ${visibleRuns.length} recent run(s).`;
  recentRunsList.hidden = false;
}

function formatJobDetailRecentRunLabel(run) {
  const runId = valueOrDash(run?.jobExecutionId);
  const status = valueOrDash(run?.status);
  const start = valueOrDash(run?.startTime);
  return `runId=${runId} | status=${status} | start=${start}`;
}

async function loadJobConfig(routeState) {
  const requestId = ++loadRequestTracker.jobConfig;
  const state = document.getElementById("job-config-state");
  const summary = document.getElementById("job-config-summary");
  const fileState = document.getElementById("job-config-file-state");
  const fileLinks = document.getElementById("job-config-file-links");
  const selectedFile = document.getElementById("job-config-selected-file");
  const raw = document.getElementById("job-config-raw");
  const backLink = document.getElementById("job-config-back-link");
  const jobKeyValue = routeState && routeState.jobKey ? routeState.jobKey : null;
  const jobsRouteQuerySuffix = getQuerySuffix(routeState && routeState.query);

  state.className = "state";
  summary.hidden = true;
  if (fileState) {
    fileState.hidden = true;
    fileState.className = "state";
    fileState.textContent = "";
  }
  if (fileLinks) {
    fileLinks.hidden = true;
    fileLinks.innerHTML = "";
  }
  if (selectedFile) {
    selectedFile.hidden = true;
    selectedFile.textContent = "";
  }
  raw.hidden = true;
  raw.textContent = "";
  if (backLink) {
    backLink.setAttribute("href", `#/jobs${jobsRouteQuerySuffix}`);
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
    if (!shouldApplyRouteScopedUpdate("jobConfig", requestId, jobKeyValue)) {
      return;
    }

    document.getElementById("job-config-key").textContent = payload.jobKey || jobKeyValue;
    document.getElementById("job-config-name").textContent = payload.displayName || "-";
    document.getElementById("job-config-path").textContent = payload.jobConfigPath || "-";
    renderJobConfigFileNavigator(payload, routeState);
    if (backLink) {
      backLink.setAttribute("href", `#/jobs/${encodeURIComponent(jobKeyValue)}${jobsRouteQuerySuffix}`);
    }

    summary.hidden = false;
    raw.hidden = false;
    state.textContent = "Job config loaded.";
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("jobConfig", requestId, jobKeyValue)) {
      return;
    }
    state.className = "state error";
    state.textContent = `Unable to load job config: ${error.message}`;
  }
}

function renderJobConfigFileNavigator(payload, routeState) {
  const fileState = document.getElementById("job-config-file-state");
  const fileLinks = document.getElementById("job-config-file-links");
  const selectedFile = document.getElementById("job-config-selected-file");
  const raw = document.getElementById("job-config-raw");
  if (!fileState || !fileLinks || !selectedFile || !raw) {
    return;
  }

  const { documents: docs, missingCompanionDocuments: missingCompanionDocs } = buildJobConfigDocuments(payload);
  const requestedFileKey = normalizeDocumentKey(routeState?.query?.file);
  const selectedDocument = pickJobConfigDocument(docs, requestedFileKey);

  fileState.hidden = true;
  fileState.className = "state";
  fileState.textContent = "";

  const buttons = [];
  const renderDocument = (doc) => {
    buttons.forEach((button) => button.classList.toggle("active", button.dataset.docKey === doc.key));
    selectedFile.textContent = `${doc.label} | ${doc.path}`;
    selectedFile.hidden = false;
    if (String(doc.content || "").trim() === "") {
      raw.textContent = `No read-only payload was returned for ${doc.label}.`;
    } else {
      raw.textContent = doc.content;
    }
    const warningMessage = buildMissingCompanionWarning(doc, missingCompanionDocs);
    if (warningMessage) {
      fileState.hidden = false;
      fileState.textContent = warningMessage;
    } else {
      fileState.hidden = true;
      fileState.textContent = "";
    }
    raw.hidden = false;
    syncJobConfigFileRouteSelection(routeState?.jobKey, routeState?.query, doc.key);
  };

  fileLinks.innerHTML = "";
  docs.forEach((doc) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "job-config-file-btn";
    button.dataset.docKey = doc.key;
    button.textContent = doc.label;
    button.title = doc.path;
    button.addEventListener("click", () => renderDocument(doc));
    fileLinks.appendChild(button);
    buttons.push(button);
  });

  fileLinks.hidden = false;
  if (selectedDocument) {
    renderDocument(selectedDocument);
  }
}

function syncJobConfigFileRouteSelection(jobKey, currentQuery, selectedFileKey) {
  const normalizedJobKey = String(jobKey || "").trim();
  if (normalizedJobKey === "") {
    return;
  }

  const params = new URLSearchParams();
  Object.entries(currentQuery || {}).forEach(([key, value]) => {
    if (key === "file") {
      return;
    }
    if (value !== null && value !== undefined && String(value) !== "") {
      params.set(key, String(value));
    }
  });

  const normalizedFileKey = normalizeDocumentKey(selectedFileKey);
  if (normalizedFileKey !== "job") {
    params.set("file", normalizedFileKey);
  }

  const nextHash = params.toString()
    ? `#/jobs/${encodeURIComponent(normalizedJobKey)}/config?${params.toString()}`
    : `#/jobs/${encodeURIComponent(normalizedJobKey)}/config`;
  if (location.hash !== nextHash) {
    history.replaceState(null, "", nextHash);
  }
}

async function loadJobDetailSchedulePanel(jobKeyValue, requestId) {
  const scheduleState = document.getElementById("job-detail-schedule-state");
  const scheduleSummary = document.getElementById("job-detail-schedule-summary");
  const scheduleActionButton = document.getElementById("job-detail-schedule-action-btn");
  const scheduleFeedback = document.getElementById("job-detail-schedule-feedback");

  if (!scheduleState || !scheduleSummary || !scheduleActionButton || !scheduleFeedback) {
    return;
  }

  scheduleState.className = "state";
  scheduleState.textContent = "Loading native schedule...";
  scheduleSummary.hidden = true;
  scheduleActionButton.disabled = true;
  scheduleActionButton.onclick = null;

  try {
    const response = await fetch(`/api/v1/schedules?limit=${DEFAULT_SCHEDULE_LOOKUP_LIMIT}`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Schedule API returned ${response.status}`);
    }
    const payload = await response.json();
    if (!shouldApplyRouteScopedUpdate("jobDetail", requestId, jobKeyValue)) {
      return;
    }

    const schedules = Array.isArray(payload.items) ? payload.items : [];
    const normalizedJobKey = String(jobKeyValue || "").trim().toLowerCase();
    const matches = schedules.filter((schedule) => String(schedule?.selectedJobKey || "").trim().toLowerCase() === normalizedJobKey);

    if (matches.length === 0) {
      scheduleState.className = "state";
      scheduleState.textContent = "No native schedule is configured for this job.";
      scheduleSummary.hidden = true;
      scheduleActionButton.disabled = true;
      scheduleFeedback.hidden = true;
      return;
    }

    const selectedSchedule = selectScheduleForJobDetail(matches, viewState.schedules.selectedScheduleId);
    viewState.schedules.selectedScheduleId = String(selectedSchedule?.scheduleId || "").trim();
    const scheduleControlState = getScheduleControlState(selectedSchedule);
    const statusLabel = scheduleControlState.statusLabel;
    document.getElementById("job-detail-schedule-status").textContent = statusLabel;
    document.getElementById("job-detail-schedule-id").textContent = valueOrDash(selectedSchedule.scheduleId);
    document.getElementById("job-detail-schedule-key").textContent = valueOrDash(selectedSchedule.scheduleKey);
    document.getElementById("job-detail-schedule-timezone").textContent = valueOrDash(selectedSchedule.timezone);
    document.getElementById("job-detail-schedule-expression").textContent = valueOrDash(selectedSchedule.expression);
    document.getElementById("job-detail-schedule-next-due").textContent = valueOrDash(selectedSchedule.nextDueAt);

    scheduleState.className = "state";
    scheduleState.textContent = "Native schedule loaded.";
    scheduleSummary.hidden = false;

    if (matches.length > 1) {
      scheduleFeedback.className = "state";
      scheduleFeedback.textContent = `Multiple native schedules are configured for this job (${matches.length} found). Managing ${valueOrDash(selectedSchedule.scheduleKey)} in this panel.`;
      scheduleFeedback.hidden = false;
    } else {
      scheduleFeedback.hidden = true;
      scheduleFeedback.className = "state";
      scheduleFeedback.textContent = "";
    }

    if (scheduleControlState.toggleDisabled) {
      scheduleActionButton.textContent = scheduleControlState.detailToggleLabel;
      scheduleActionButton.disabled = true;
      scheduleActionButton.onclick = null;
      scheduleFeedback.className = "state";
      scheduleFeedback.textContent = "Schedule is disabled. Pause/resume controls apply to enabled schedules only.";
      scheduleFeedback.hidden = false;
      return;
    }

    const action = scheduleControlState.toggleAction;
    scheduleActionButton.textContent = scheduleControlState.detailToggleLabel;
    scheduleActionButton.disabled = false;
    scheduleActionButton.onclick = () => requestScheduleStateChange(jobKeyValue, selectedSchedule.scheduleId, action, requestId);
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("jobDetail", requestId, jobKeyValue)) {
      return;
    }
    scheduleState.className = "state error";
    scheduleState.textContent = `Unable to load native schedule: ${error.message}`;
    scheduleSummary.hidden = true;
    scheduleActionButton.disabled = true;
  }
}

async function requestScheduleStateChange(jobKeyValue, scheduleId, action, requestId) {
  const scheduleActionButton = document.getElementById("job-detail-schedule-action-btn");
  const scheduleFeedback = document.getElementById("job-detail-schedule-feedback");
  const normalizedScheduleId = String(scheduleId || "").trim();
  const normalizedAction = String(action || "").trim().toLowerCase();

  if (!scheduleActionButton || !scheduleFeedback || !normalizedScheduleId || (normalizedAction !== "pause" && normalizedAction !== "resume")) {
    return;
  }
  if (scheduleRequestState.inFlightActionByScheduleId[normalizedScheduleId]) {
    scheduleFeedback.className = "state";
    scheduleFeedback.textContent = "Schedule action already in progress. Please wait for the current response.";
    scheduleFeedback.hidden = false;
    return;
  }

  const confirmMessage = normalizedAction === "pause"
    ? "Pause this native schedule? Direct selected-job execution remains available."
    : "Resume this native schedule?";
  if (!window.confirm(confirmMessage)) {
    return;
  }

  scheduleRequestState.inFlightActionByScheduleId[normalizedScheduleId] = true;
  scheduleActionButton.disabled = true;
  scheduleFeedback.className = "state";
  scheduleFeedback.textContent = `Submitting ${normalizedAction} request...`;
  scheduleFeedback.hidden = false;

  try {
    const response = await fetch(`/api/v1/schedules/${encodeURIComponent(normalizedScheduleId)}:${normalizedAction}`, {
      method: "POST",
      headers: { Accept: "application/json" },
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      scheduleFeedback.className = "state error";
      scheduleFeedback.textContent = `Schedule ${normalizedAction} failed: ${valueOrDash(payload.message)}`;
      scheduleFeedback.hidden = false;
      return;
    }

    scheduleFeedback.className = "state";
    scheduleFeedback.textContent = `Schedule ${normalizedAction} accepted for ${normalizedScheduleId}.`;
    scheduleFeedback.hidden = false;
    await loadJobDetailSchedulePanel(jobKeyValue, requestId);
  } catch (error) {
    scheduleFeedback.className = "state error";
    scheduleFeedback.textContent = `Schedule ${normalizedAction} failed [runtime]: ${error.message}`;
    scheduleFeedback.hidden = false;
  } finally {
    delete scheduleRequestState.inFlightActionByScheduleId[normalizedScheduleId];
    if (shouldApplyRouteScopedUpdate("jobDetail", requestId, jobKeyValue)) {
      scheduleActionButton.disabled = false;
    }
  }
}

function pickNewestSchedule(schedules) {
  return [...schedules].sort((left, right) => {
    const leftUpdated = String(left?.updatedAt || "");
    const rightUpdated = String(right?.updatedAt || "");
    return rightUpdated.localeCompare(leftUpdated);
  })[0];
}

function selectScheduleForJobDetail(schedules, preferredScheduleId) {
  const list = Array.isArray(schedules) ? schedules : [];
  const preferredId = String(preferredScheduleId || "").trim();
  if (preferredId !== "") {
    const preferred = list.find((schedule) => String(schedule?.scheduleId || "").trim() === preferredId);
    if (preferred) {
      return preferred;
    }
  }
  return pickNewestSchedule(list);
}

function formatScheduleStatus(schedule) {
  return getScheduleControlState(schedule).statusLabel;
}

function getScheduleControlState(schedule) {
  const enabled = Boolean(schedule?.enabled);
  const paused = enabled && Boolean(schedule?.paused);

  return {
    enabled,
    paused,
    statusLabel: !enabled ? "Disabled" : paused ? "Paused" : "Active",
    toggleAction: enabled ? (paused ? "resume" : "pause") : null,
    toggleLabel: paused ? "Resume" : "Pause",
    detailToggleLabel: paused ? "Resume schedule" : "Pause schedule",
    toggleDisabled: !enabled,
  };
}

async function requestTriggerNow(jobKeyValue) {
  const triggerButton = document.getElementById("job-detail-trigger-now-btn");
  const triggerFeedback = document.getElementById("job-detail-trigger-feedback");
  const triggerCount = document.getElementById("job-detail-trigger-count");
  const normalizedJobKey = String(jobKeyValue || "").trim();
  const now = Date.now();

  if (!normalizedJobKey) {
    triggerFeedback.className = "state error";
    triggerFeedback.textContent = "Unable to trigger: missing job key.";
    triggerFeedback.hidden = false;
    return;
  }

  if (triggerNowRequestState.inFlightByJobKey[normalizedJobKey]) {
    triggerFeedback.className = "state";
    triggerFeedback.textContent = "Trigger request already in progress. Please wait for the current response.";
    triggerFeedback.hidden = false;
    return;
  }

  const cooldownUntil = Number(triggerNowRequestState.cooldownUntilByJobKey[normalizedJobKey] || 0);
  if (cooldownUntil > now) {
    triggerFeedback.className = "state";
    triggerFeedback.textContent = "Trigger already accepted recently. Please wait a few seconds before retrying.";
    triggerFeedback.hidden = false;
    return;
  }

  triggerNowRequestState.inFlightByJobKey[normalizedJobKey] = true;

  const confirmed = window.confirm(
    "Trigger one ad hoc run now? This is operator convenience only and not schedule management."
  );
  if (!confirmed) {
    delete triggerNowRequestState.inFlightByJobKey[normalizedJobKey];
    return;
  }

  triggerButton.disabled = true;
  triggerFeedback.className = "state";
  triggerFeedback.textContent = "Submitting trigger request...";
  triggerFeedback.hidden = false;

  try {
    const response = await fetch(`/api/v1/jobs/${encodeURIComponent(normalizedJobKey)}:trigger-now`, {
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
      const decisionStatus = String(payload.decisionStatus || "").trim();
      const duplicateSuppressed = decisionStatus === "DUPLICATE_SUPPRESSED";
      triggerFeedback.textContent = duplicateSuppressed
        ? `Trigger already accepted recently. decision=${valueOrDash(payload.decisionStatus)} triggerEventId=${eventId}`
        : `Trigger accepted. decision=${valueOrDash(payload.decisionStatus)} triggerEventId=${eventId}`;
      triggerFeedback.hidden = false;
      triggerNowRequestState.cooldownUntilByJobKey[normalizedJobKey] = Date.now() + TRIGGER_NOW_DUPLICATE_WINDOW_MS;

      const current = Number(triggerCount.textContent);
      if (!duplicateSuppressed && !Number.isNaN(current)) {
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
    delete triggerNowRequestState.inFlightByJobKey[normalizedJobKey];
    triggerButton.disabled = false;
  }
}

async function loadSchedules() {
  const requestId = ++loadRequestTracker.schedules;
  const state = document.getElementById("schedules-state");
  const table = document.getElementById("schedules-table");
  const body = document.getElementById("schedules-body");

  if (!state || !table || !body) {
    return;
  }

  state.className = "state";
  state.textContent = "Loading schedules...";
  table.hidden = true;
  body.innerHTML = "";

  try {
    const response = await fetch(`/api/v1/schedules?limit=${DEFAULT_SCHEDULE_LOOKUP_LIMIT}`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Schedule API returned ${response.status}`);
    }
    const payload = await response.json();
    if (!shouldApplyRouteScopedUpdate("schedules", requestId)) {
      return;
    }

    const items = Array.isArray(payload.items) ? payload.items : [];
    viewState.schedules.items = items;
    viewState.schedules.loaded = true;

    if (items.length === 0) {
      state.textContent = "No schedules found.";
      return;
    }

    renderSchedulesTable(items, requestId);
    state.textContent = `Loaded ${items.length} schedule(s).`;
    table.hidden = false;
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("schedules", requestId)) {
      return;
    }
    state.className = "state error";
    state.textContent = `Unable to load schedules: ${error.message}`;
  }
}

function renderSchedulesTable(items, requestId) {
  const body = document.getElementById("schedules-body");
  if (!body) {
    return;
  }
  body.innerHTML = "";

  items.forEach((schedule) => {
    const row = document.createElement("tr");
    const scheduleControlState = getScheduleControlState(schedule);
    row.className = "clickable-row";
    row.title = "Open schedule detail";
    row.addEventListener("click", () => {
      const scheduleId = String(schedule?.scheduleId || "").trim();
      if (scheduleId === "") {
        return;
      }
      location.hash = `#/schedules/${encodeURIComponent(scheduleId)}`;
    });

    const actions = document.createElement("td");

    const openJobButton = document.createElement("button");
    openJobButton.type = "button";
    openJobButton.textContent = "Open job";
    openJobButton.addEventListener("click", (event) => {
      event.stopPropagation();
      const selectedJobKey = String(schedule?.selectedJobKey || "").trim();
      if (!selectedJobKey) {
        return;
      }
      viewState.schedules.selectedScheduleId = String(schedule?.scheduleId || "").trim();
      const selectedScheduleId = viewState.schedules.selectedScheduleId;
      location.hash = selectedScheduleId === ""
        ? `#/jobs/${encodeURIComponent(selectedJobKey)}`
        : `#/jobs/${encodeURIComponent(selectedJobKey)}?from=schedule&scheduleId=${encodeURIComponent(selectedScheduleId)}`;
    });

    const toggleButton = document.createElement("button");
    toggleButton.type = "button";
    toggleButton.textContent = scheduleControlState.toggleLabel;
    toggleButton.disabled = scheduleControlState.toggleDisabled;
    toggleButton.addEventListener("click", async (event) => {
      event.stopPropagation();
      const action = scheduleControlState.toggleAction;
      if (!action) {
        return;
      }
      await requestScheduleWorkbenchStateChange(schedule, action, requestId);
    });

    const triggerNowButton = document.createElement("button");
    triggerNowButton.type = "button";
    triggerNowButton.textContent = "Trigger now";
    triggerNowButton.addEventListener("click", async (event) => {
      event.stopPropagation();
      await requestScheduleWorkbenchTriggerNow(schedule, requestId);
    });

    actions.append(openJobButton, document.createTextNode(" "), toggleButton, document.createTextNode(" "), triggerNowButton);
    row.innerHTML = `
      <td>${escapeHtml(valueOrDash(schedule.scheduleKey))}</td>
      <td>${escapeHtml(valueOrDash(schedule.selectedJobKey))}</td>
      <td>${escapeHtml(formatScheduleStatus(schedule))}</td>
      <td>${escapeHtml(valueOrDash(schedule.nextDueAt))}</td>`;
    row.appendChild(actions);
    body.appendChild(row);
  });
}

async function requestScheduleWorkbenchStateChange(schedule, action, requestId) {
  const state = document.getElementById("schedules-state");
  const scheduleId = String(schedule?.scheduleId || "").trim();
  if (!state || !scheduleId) {
    return;
  }
  if (scheduleRequestState.inFlightActionByScheduleId[scheduleId]) {
    state.className = "state";
    state.textContent = "Schedule action already in progress. Please wait for the current response.";
    return;
  }

  scheduleRequestState.inFlightActionByScheduleId[scheduleId] = true;
  state.className = "state";
  state.textContent = `Submitting schedule ${action} request...`;

  try {
    const response = await fetch(`/api/v1/schedules/${encodeURIComponent(scheduleId)}:${action}`, {
      method: "POST",
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      state.className = "state error";
      state.textContent = `Schedule ${action} failed: ${valueOrDash(payload.message)}`;
      return;
    }
    if (!shouldApplyRouteScopedUpdate("schedules", requestId)) {
      return;
    }
    viewState.schedules.selectedScheduleId = scheduleId;
    await loadSchedules();
  } catch (error) {
    state.className = "state error";
    state.textContent = `Schedule ${action} failed [runtime]: ${error.message}`;
  } finally {
    delete scheduleRequestState.inFlightActionByScheduleId[scheduleId];
  }
}

async function requestScheduleWorkbenchTriggerNow(schedule, requestId) {
  const state = document.getElementById("schedules-state");
  const scheduleId = String(schedule?.scheduleId || "").trim();
  const selectedJobKey = String(schedule?.selectedJobKey || "").trim();
  if (!state || !scheduleId || !selectedJobKey) {
    return;
  }
  if (scheduleRequestState.inFlightTriggerByScheduleId[scheduleId]) {
    state.className = "state";
    state.textContent = "Trigger request already in progress for this schedule. Please wait for the current response.";
    return;
  }

  scheduleRequestState.inFlightTriggerByScheduleId[scheduleId] = true;
  state.className = "state";
  state.textContent = `Submitting trigger now request for ${selectedJobKey}...`;

  try {
    const response = await fetch(`/api/v1/jobs/${encodeURIComponent(selectedJobKey)}:trigger-now`, {
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
    if (!response.ok && response.status !== 202) {
      state.className = "state error";
      state.textContent = `Trigger now failed: ${valueOrDash(payload.message)}`;
      return;
    }
    state.className = "state";
    state.textContent = `Trigger now accepted for ${selectedJobKey}. decision=${valueOrDash(payload.decisionStatus)} triggerEventId=${valueOrDash(payload.triggerEventId)}`;
    if (!shouldApplyRouteScopedUpdate("schedules", requestId)) {
      return;
    }
    viewState.schedules.selectedScheduleId = scheduleId;
    await loadSchedules();
  } catch (error) {
    state.className = "state error";
    state.textContent = `Trigger now failed [runtime]: ${error.message}`;
  } finally {
    delete scheduleRequestState.inFlightTriggerByScheduleId[scheduleId];
  }
}

async function loadScheduleDetail(routeState) {
  const requestId = ++loadRequestTracker.scheduleDetail;
  const state = document.getElementById("schedule-detail-state");
  const summary = document.getElementById("schedule-detail-summary");
  const triggerState = document.getElementById("schedule-detail-triggers-state");
  const triggerList = document.getElementById("schedule-detail-triggers-list");
  const backLink = document.getElementById("schedule-detail-back-link");
  const scheduleId = String(routeState?.selectedScheduleId || "").trim();

  if (!state || !summary || !triggerState || !triggerList || !backLink) {
    return;
  }

  backLink.setAttribute("href", "#/schedules");
  state.className = "state";
  state.textContent = "Loading schedule detail...";
  summary.hidden = true;
  triggerState.className = "state";
  triggerState.textContent = "Loading triggers...";
  triggerList.hidden = true;
  triggerList.innerHTML = "";

  if (scheduleId === "") {
    state.className = "state error";
    state.textContent = "Missing schedule id in route.";
    return;
  }

  viewState.schedules.selectedScheduleId = scheduleId;

  try {
    const response = await fetch(`/api/v1/schedules?limit=${DEFAULT_SCHEDULE_LOOKUP_LIMIT}`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Schedule API returned ${response.status}`);
    }
    const payload = await response.json();
    if (!shouldApplyRouteScopedUpdate("scheduleDetail", requestId)) {
      return;
    }

    const items = Array.isArray(payload.items) ? payload.items : [];
    const selected = items.find((item) => String(item?.scheduleId || "").trim() === scheduleId);
    if (!selected) {
      state.className = "state error";
      state.textContent = `Schedule ${scheduleId} was not found.`;
      return;
    }

    document.getElementById("schedule-detail-id").textContent = valueOrDash(selected.scheduleId);
    document.getElementById("schedule-detail-key").textContent = valueOrDash(selected.scheduleKey);
    document.getElementById("schedule-detail-job").textContent = valueOrDash(selected.selectedJobKey);
    document.getElementById("schedule-detail-status").textContent = formatScheduleStatus(selected);
    document.getElementById("schedule-detail-timezone").textContent = valueOrDash(selected.timezone);
    document.getElementById("schedule-detail-expression").textContent = valueOrDash(selected.expression);
    document.getElementById("schedule-detail-next-due").textContent = valueOrDash(selected.nextDueAt);
    summary.hidden = false;

    const triggerResponse = await fetch(`/api/v1/schedules/${encodeURIComponent(scheduleId)}/trigger-events?limit=20`, {
      headers: { Accept: "application/json" },
    });
    if (!triggerResponse.ok) {
      throw new Error(`Trigger events API returned ${triggerResponse.status}`);
    }
    const triggerPayload = await triggerResponse.json();
    if (!shouldApplyRouteScopedUpdate("scheduleDetail", requestId)) {
      return;
    }

    const triggerItems = Array.isArray(triggerPayload.items) ? triggerPayload.items : [];
    if (triggerItems.length === 0) {
      triggerState.textContent = "No trigger events recorded for this schedule yet.";
    } else {
      triggerItems.forEach((item) => {
        triggerList.appendChild(buildScheduleTriggerEventLine(item, scheduleId));
      });
      triggerState.textContent = `Showing ${triggerItems.length} trigger event(s).`;
      triggerList.hidden = false;
    }

    state.textContent = "Schedule detail loaded.";
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("scheduleDetail", requestId)) {
      return;
    }
    state.className = "state error";
    state.textContent = `Unable to load schedule detail: ${error.message}`;
  }
}

function buildScheduleTriggerEventLine(item, scheduleId) {
  const line = document.createElement("li");
  const requestedAt = valueOrDash(item?.requestedAt);
  const origin = formatScheduleTriggerOriginToken(item?.triggerOrigin);
  const decision = valueOrDash(item?.decisionStatus);
  const triggerEventId = valueOrDash(item?.triggerEventId);
  const launchedRunId = String(item?.launchedRunId || "").trim();

  line.appendChild(document.createTextNode(`${requestedAt} | origin=${origin} | decision=${decision} | triggerEventId=${triggerEventId} | launchedRunId=`));

  if (launchedRunId !== "") {
    const runLink = document.createElement("a");
    runLink.href = `#/runs/${encodeURIComponent(launchedRunId)}?from=schedule&scheduleId=${encodeURIComponent(scheduleId)}`;
    runLink.textContent = launchedRunId;
    line.appendChild(runLink);
  } else {
    line.appendChild(document.createTextNode("-"));
  }

  return line;
}

// schedule detail now uses a dedicated route (`#/schedules/{scheduleId}`), so
// list-selection hash syncing is intentionally not used.

function formatTriggerOriginToken(token) {
  const normalized = String(token || "").trim().toUpperCase();
  if (normalized === "SCHEDULE") {
    return "SCHEDULE";
  }
  if (normalized === "EVENT") {
    return "EVENT";
  }
  return "MANUAL";
}

function formatScheduleTriggerOriginToken(token) {
  const normalized = String(token || "").trim().toUpperCase();
  if (normalized === "EVENT") {
    return "EVENT";
  }
  if (normalized === "MANUAL") {
    return "MANUAL";
  }
  return "SCHEDULE";
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
  const requestId = ++loadRequestTracker.runs;
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
    if (!shouldApplyRouteScopedUpdate("runs", requestId)) {
      return;
    }

    viewState.runs.items = runs;
    viewState.runs.loaded = true;
    viewState.runs.loadedForKey = loadKey;

    if (viewState.runs.items.length === 0) {
      state.textContent = "No runs found for the selected filters.";
      return;
    }
    runsListUi.renderTable();
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("runs", requestId)) {
      return;
    }
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
  initializeScheduleControls();
}

function initializeScheduleControls() {
  const refreshButton = document.getElementById("schedules-refresh-btn");
  if (!refreshButton) {
    return;
  }
  refreshButton.addEventListener("click", () => {
    viewState.schedules.loaded = false;
    if (currentRouteState().key === "schedules") {
      loadSchedules();
    }
  });
}

function applyRouteStateToListView(routeState) {
  if (routeState.key === "jobs") {
    jobsListUi.applyRouteState(routeState);
    return;
  }

  runsListUi.applyRouteState(routeState);
}

function syncListRouteHash(routeKey) {
  const hash = routeKey === "jobs"
    ? getJobsRouteHash()
    : getRunsRouteHash();
  if (location.hash !== hash) {
    location.hash = hash;
  }
}

function getJobsRouteHash() {
  const query = buildJobsRouteQuery(viewState.jobs);
  return query ? `#/jobs?${query}` : "#/jobs";
}

function getJobsRouteQuerySuffix() {
  const query = buildJobsRouteQuery(viewState.jobs);
  return query ? `?${query}` : "";
}

function buildJobsRouteQuery(source) {
  const params = new URLSearchParams();
  if (source.filterText.trim() !== "") {
    params.set("f", source.filterText.trim());
  }
  if ((source.page || 1) > 1) {
    params.set("page", String(source.page));
  }
  if ((source.pageSize || defaultJobsPageSize()) !== defaultJobsPageSize()) {
    params.set("pageSize", String(source.pageSize));
  }
  params.set("sort", source.sortKey);
  params.set("dir", source.sortDirection);
  return params.toString();
}

function getRunsRouteHash() {
  const source = viewState.runs;
  const params = new URLSearchParams();

  if (source.filterText.trim() !== "") {
    params.set("f", source.filterText.trim());
  }
  if (source.selectedJobKey && source.selectedJobKey.trim() !== "") {
    params.set("job", source.selectedJobKey.trim());
  }
  if (source.runModeFilter && source.runModeFilter.trim() !== "") {
    params.set("runMode", source.runModeFilter.trim());
  }
  if (source.recoveryPolicyFilter && source.recoveryPolicyFilter.trim() !== "") {
    params.set("recoveryPolicy", source.recoveryPolicyFilter.trim());
  }
  if (source.startDate && source.startDate.trim() !== "") {
    params.set("startDate", source.startDate.trim());
  }
  if (source.timezone && source.timezone.trim() !== "") {
    params.set("timezone", source.timezone.trim());
  }
  params.set("sort", source.sortKey);
  params.set("dir", source.sortDirection);
  return `#/runs?${params.toString()}`;
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

function isLatestRequest(scope, requestId) {
  return loadRequestTracker[scope] === requestId;
}

function shouldApplyRouteScopedUpdate(routeKey, requestId, routeValue) {
  return isLatestRequest(routeKey, requestId) && isActiveRoute(routeKey, routeValue);
}

function isActiveRoute(routeKey, routeValue) {
  const routeState = currentRouteState();
  if (routeState.key !== routeKey) {
    return false;
  }
  if (routeKey === "jobDetail" || routeKey === "jobConfig") {
    return String(routeState.jobKey || "") === String(routeValue || "");
  }
  if (routeKey === "runDetail") {
    return String(routeState.jobExecutionId || "") === String(routeValue || "");
  }
  return true;
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

function normalizePositiveInteger(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  if (Number.isFinite(parsed) && parsed > 0) {
    return parsed;
  }
  return fallback;
}

function normalizePageSize(value, fallback) {
  const parsed = normalizePositiveInteger(value, fallback);
  return JOBS_PAGE_SIZE_OPTIONS.includes(parsed) ? parsed : fallback;
}

function defaultJobsPageSize() {
  const viewportHeight = typeof window !== "undefined" && Number.isFinite(window.innerHeight)
    ? window.innerHeight
    : 900;
  if (viewportHeight >= 1200) {
    return 15;
  }
  if (viewportHeight >= 900) {
    return 10;
  }
  return 8;
}

function getQuerySuffix(query) {
  const params = new URLSearchParams();
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined && String(value) !== "") {
      params.set(key, String(value));
    }
  });
  const serialized = params.toString();
  return serialized ? `?${serialized}` : "";
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
  applyJobsItems(jobs);
  viewState.jobs.loaded = true;
  return jobs;
}

function applyJobsItems(items) {
  const jobs = Array.isArray(items) ? items : [];
  const validKeys = new Set(
    jobs
      .map((job) => String(job?.jobKey || "").trim())
      .filter((jobKey) => jobKey !== "")
  );

  viewState.jobs.items = jobs;
  reconcileJobsScopedCaches(validKeys);
}

function reconcileJobsScopedCaches(validJobKeys) {
  const validKeys = validJobKeys instanceof Set ? validJobKeys : new Set();

  if (!validKeys.has(viewState.jobs.expandedJobKey)) {
    viewState.jobs.expandedJobKey = "";
  }

  pruneObjectKeys(viewState.jobs.jobStepPreviewByJobKey, validKeys);
  pruneObjectKeys(viewState.jobs.stepNamesByJobKey, validKeys);
  pruneObjectKeys(inFlightStepNamesByJobKey, validKeys);
}

function pruneObjectKeys(source, validKeys) {
  if (!source || typeof source !== "object") {
    return;
  }

  Object.keys(source).forEach((key) => {
    if (!validKeys.has(key)) {
      delete source[key];
    }
  });
}

async function fetchRunsForFilters(selectedJobKey, runMode, recoveryPolicy, startDate, timezone) {
  const cacheKey = `${selectedJobKey || ""}|${runMode || ""}|${recoveryPolicy || ""}|${startDate || ""}|${timezone || ""}`;
  const cached = getCachedRunsByFilter(cacheKey);
  if (cached) {
    return cached;
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
  setCachedRunsByFilter(cacheKey, items);
  return items;
}

function getCachedRunsByFilter(cacheKey) {
  const cache = viewState.runs.cache;
  const entry = cache.byFilter[cacheKey];
  if (!entry) {
    return null;
  }

  if (Array.isArray(entry)) {
    return entry;
  }

  if (!Array.isArray(entry.items) || !Number.isFinite(entry.cachedAt)) {
    delete cache.byFilter[cacheKey];
    cache.order = cache.order.filter((key) => key !== cacheKey);
    return null;
  }

  if (Date.now() - entry.cachedAt > RUNS_FILTER_CACHE_TTL_MS) {
    delete cache.byFilter[cacheKey];
    cache.order = cache.order.filter((key) => key !== cacheKey);
    return null;
  }

  cache.order = cache.order.filter((key) => key !== cacheKey);
  cache.order.push(cacheKey);
  return entry.items;
}

function setCachedRunsByFilter(cacheKey, items) {
  const cache = viewState.runs.cache;
  cache.byFilter[cacheKey] = {
    items,
    cachedAt: Date.now(),
  };

  cache.order = cache.order.filter((key) => key !== cacheKey);
  cache.order.push(cacheKey);

  while (cache.order.length > RUNS_FILTER_CACHE_MAX_ENTRIES) {
    const staleKey = cache.order.shift();
    if (staleKey) {
      delete cache.byFilter[staleKey];
    }
  }
}

async function loadRunDetail(routeState) {
  const requestId = ++loadRequestTracker.runDetail;
  const state = document.getElementById("run-detail-state");
  const summary = document.getElementById("run-detail-summary");
  const backLink = document.getElementById("run-detail-back-link");
  const runIdValue = routeState && routeState.jobExecutionId ? routeState.jobExecutionId : null;
  const source = String(routeState?.query?.from || "").trim().toLowerCase();
  const sourceJobKey = String(routeState?.query?.job || "").trim();
  const sourceScheduleId = String(routeState?.query?.scheduleId || "").trim();

  state.className = "state";
  summary.hidden = true;

  if (backLink) {
    if (source === "job" && sourceJobKey !== "") {
      const returnHash = sourceScheduleId !== ""
        ? `#/jobs/${encodeURIComponent(sourceJobKey)}?from=schedule&scheduleId=${encodeURIComponent(sourceScheduleId)}`
        : `#/jobs/${encodeURIComponent(sourceJobKey)}`;
      backLink.setAttribute("href", returnHash);
      backLink.textContent = "Back to job detail";
    } else if (source === "schedule" && sourceScheduleId !== "") {
      backLink.setAttribute("href", `#/schedules/${encodeURIComponent(sourceScheduleId)}`);
      backLink.textContent = "Back to schedules";
    } else {
      backLink.setAttribute("href", "#/runs");
      backLink.textContent = "Back to runs list";
    }
  }

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
    if (!shouldApplyRouteScopedUpdate("runDetail", requestId, runIdValue)) {
      return;
    }

    const run = payload.run || {};

    document.getElementById("run-detail-id").textContent = String(run.jobExecutionId ?? runIdValue);
    document.getElementById("run-detail-scenario").textContent = run.scenario || "-";
    document.getElementById("run-detail-status").textContent = run.status || "-";
    document.getElementById("run-detail-trigger-origin").textContent = formatTriggerOriginToken(run.triggerOrigin);
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
        if (!shouldApplyRouteScopedUpdate("runDetail", requestId, runIdValue)) {
          return;
        }
        runRecoveryPanel.render(recovery);
      })
      .catch(() => {
        if (!shouldApplyRouteScopedUpdate("runDetail", requestId, runIdValue)) {
          return;
        }
        runRecoveryPanel.render(null);
      });

    const persistedRecordsPromise = Promise.all([
      fetchPersistedRunStepRecords(runIdValue),
      fetchPersistedRunArtifactRecords(runIdValue),
    ]).then(([persistedStepRecords, persistedArtifactRecords]) => {
      if (!shouldApplyRouteScopedUpdate("runDetail", requestId, runIdValue)) {
        return;
      }

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
    ]);

    if (!shouldApplyRouteScopedUpdate("runDetail", requestId, runIdValue)) {
      return;
    }

    await runLogViewer.load(runIdValue);
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("runDetail", requestId, runIdValue)) {
      return;
    }
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
