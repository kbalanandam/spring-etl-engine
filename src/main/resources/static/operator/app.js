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
import {
  extractStepNamesFromRawYaml,
} from "./job-step-names.js";
import {
  createAppRouteHelpers,
} from "./app-route-helpers.js";
import {
  createAppRouteUpdateGuards,
} from "./app-route-update-guards.js";
import {
  createAppRunsBridgeHelpers,
} from "./app-runs-bridge-helpers.js";
import {
  createAppScheduleDetailHelpers,
} from "./app-schedule-detail-helpers.js";
import {
  createAppRunDetailHelpers,
} from "./app-run-detail-helpers.js";
import {
  createAppJobDetailHelpers,
} from "./app-job-detail-helpers.js";
import {
  createAppScheduleEditorHelpers,
} from "./app-schedule-editor-helpers.js";
import {
  defaultJobsPageSize as defaultJobsPageSizeValue,
  defaultSchedulesPageSize as defaultSchedulesPageSizeValue,
} from "./route-state.js";
import {
  describeScheduleExpression,
  validateScheduleExpression,
} from "./schedule-expression.js";
import {
  filterSchedulesItems,
  sortSchedulesItems,
} from "./schedule-list-helpers.js";
import {
  formatScheduleStatus,
  formatScheduleTriggerOriginToken,
  formatTriggerOriginToken,
  getScheduleControlState,
  selectScheduleForJobDetail,
} from "./schedule-ui-helpers.js";
import {
  formatDateForInput as formatDateForInputValue,
  normalizeIsoDate as normalizeIsoDateValue,
  normalizeSupportedFilter as normalizeSupportedFilterValue,
} from "./runs-route-state.js";
import {
  fetchTriggerSourceOptions as fetchTriggerSourceOptionsValue,
  fetchRunsForFilters as fetchRunsForFiltersValue,
} from "./runs-data.js";
import {
  fetchJobsForRunsScope as fetchJobsForRunsScopeValue,
  mapJobsToRunsJobOptions,
} from "./runs-jobs-data.js";
import {
  applyJobsItems as applyJobsItemsValue,
} from "./jobs-scoped-state.js";
import {
  categorizeTriggerFailure,
  escapeHtml,
  formatJobDetailRecentRunLabel,
  valueOrDash,
} from "./operator-text-utils.js";

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
    filterText: "",
    sortKey: "scheduleKey",
    sortDirection: "asc",
    page: 1,
    pageSize: defaultSchedulesPageSize(),
    selectedScheduleId: "",
    pendingEditScheduleId: "",
    editCancelReturnHash: "",
    refreshDetailInPlace: false,
    triggersExpanded: false,
    evidenceRequestId: 0,
    editorMode: "create",
    editingScheduleId: "",
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
    triggerSourceOptions: [],
    selectedJobKey: "",
    runModeFilter: "",
    recoveryPolicyFilter: "",
    triggerSourceFilter: "",
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
  renderTriggerSourceOptions: renderRunsTriggerSourceOptions,
  formatDateForInput,
  escapeHtml,
});

const SORT_KEYS = {
  jobs: ["jobKey", "displayName", "readinessStatus"],
  schedules: ["scheduleKey", "selectedJobKey", "status", "nextDueAt"],
  runs: ["startTime", "jobExecutionId", "scenario", "status", "triggerOrigin", "runMode", "recoveryPolicy"],
};

const routeHelpers = createAppRouteHelpers({
  viewState,
  sortKeys: SORT_KEYS,
  jobsPageSizeOptions: JOBS_PAGE_SIZE_OPTIONS,
});

const SUPPORTED_RUN_MODES = new Set(["explicit-job", "demo-fallback"]);
const SUPPORTED_RECOVERY_POLICIES = new Set(["rerun-from-start", "resume-from-checkpoint"]);
const TRIGGER_NOW_DUPLICATE_WINDOW_MS = 5 * 1000;
const DEFAULT_SCHEDULE_LOOKUP_LIMIT = 200;
const JOB_DETAIL_RECENT_RUNS_LIMIT = 10;
const SCHEDULE_STATE_CHANGE_ACTIONS = new Set(["enable", "disable", "pause", "resume"]);

const loadRequestTracker = {
  jobs: 0,
  jobDetail: 0,
  jobConfig: 0,
  schedules: 0,
  scheduleDetail: 0,
  runs: 0,
  runDetail: 0,
};

const routeUpdateGuards = createAppRouteUpdateGuards({
  loadRequestTracker,
  getCurrentRouteState: currentRouteState,
});

const inFlightStepNamesByJobKey = {};
const runsBridgeHelpers = createAppRunsBridgeHelpers({
  viewState,
  inFlightStepNamesByJobKey,
  applyJobsItemsValue,
  fetchJobsForRunsScopeValue,
  fetchRunsForFiltersValue,
  normalizeSupportedFilterValue,
  normalizeIsoDateValue,
  formatDateForInputValue,
});
const triggerNowRequestState = {
  inFlightByJobKey: {},
  cooldownUntilByJobKey: {},
};
const scheduleRequestState = {
  inFlightActionByScheduleId: {},
  inFlightTriggerByScheduleId: {},
};
const scheduleDetailHelpers = createAppScheduleDetailHelpers({
  valueOrDash,
  formatScheduleTriggerOriginToken,
});
const runDetailHelpers = createAppRunDetailHelpers({
  coalesceRunSteps,
  escapeHtml,
  focusScopedLogViewer: () => runLogViewer.focus(),
  valueOrDash,
});
const jobDetailHelpers = createAppJobDetailHelpers({
  valueOrDash,
  formatTriggerOriginToken,
});
const scheduleEditorHelpers = createAppScheduleEditorHelpers({
  describeScheduleExpression,
  validateScheduleExpression,
  valueOrDash,
  viewState,
});

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
      filterText: parsed.query.f || "",
      page: normalizePositiveInteger(parsed.query.page, 1),
      pageSize: normalizePageSize(parsed.query.pageSize, defaultSchedulesPageSize()),
      sortKey: normalizeSortKey("schedules", parsed.query.sort, "scheduleKey"),
      sortDirection: normalizeDirection(parsed.query.dir, "asc"),
      selectedScheduleId: parsed.query.scheduleId || "",
      editScheduleId: parsed.query.editScheduleId || "",
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
      triggerSourceFilter: parsed.query.triggerSource || "",
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

  if (routeKey === "jobs" || routeKey === "runs" || routeKey === "schedules") {
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
  const triggerEventsState = document.getElementById("job-detail-trigger-events-state");
  const triggerEventsList = document.getElementById("job-detail-trigger-events-list");
  const viewConfigLink = document.getElementById("job-detail-view-config-link");
  const backLink = document.getElementById("job-detail-back-link");
  const jobKeyValue = routeState && routeState.jobKey ? routeState.jobKey : null;
  const navigationSource = String(routeState?.query?.from || "").trim().toLowerCase();
  const sourceScheduleId = String(routeState?.query?.scheduleId || "").trim();
  const sourceScheduleListQuery = String(routeState?.query?.scheduleListQuery || "").trim();
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
  if (triggerEventsState) {
    triggerEventsState.className = "state";
    triggerEventsState.textContent = "Loading recent trigger events...";
  }
  if (triggerEventsList) {
    triggerEventsList.hidden = true;
    triggerEventsList.innerHTML = "";
  }
  if (backLink) {
    if (navigationSource === "schedule" && sourceScheduleId !== "") {
      backLink.setAttribute("href", buildSchedulesListHash(sourceScheduleId, sourceScheduleListQuery));
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
    document.getElementById("job-detail-trigger-count").textContent = String(Array.isArray(payload.recentTriggerEvents) ? payload.recentTriggerEvents.length : 0);
    renderJobDetailRecentRuns(payload.recentRuns, jobKeyValue, routeState?.query);
    jobDetailHelpers.renderJobTriggerEvents(payload.recentTriggerEvents, jobKeyValue, routeState?.query);
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

async function refreshJobDetailRecentTriggerEvents(jobKeyValue, query) {
  const normalizedJobKey = String(jobKeyValue || "").trim();
  const triggerCount = document.getElementById("job-detail-trigger-count");
  const triggerEventsState = document.getElementById("job-detail-trigger-events-state");
  const triggerEventsList = document.getElementById("job-detail-trigger-events-list");
  if (normalizedJobKey === "" || !triggerCount || !triggerEventsState || !triggerEventsList) {
    return;
  }

  try {
    const response = await fetch(`/api/v1/jobs/${encodeURIComponent(normalizedJobKey)}/trigger-events?limit=${DEFAULT_TRIGGER_EVENT_LIMIT}`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Trigger events API returned ${response.status}`);
    }
    const payload = await response.json();
    const items = Array.isArray(payload.items) ? payload.items : [];
    triggerCount.textContent = String(items.length);
    jobDetailHelpers.renderJobTriggerEvents(items, normalizedJobKey, query);
  } catch (error) {
    triggerEventsState.className = "state error";
    triggerEventsState.textContent = `Unable to refresh trigger events: ${error.message}`;
    triggerEventsList.hidden = true;
  }
}

async function refreshJobDetailRecentRuns(jobKeyValue, query) {
  const normalizedJobKey = String(jobKeyValue || "").trim();
  const runCount = document.getElementById("job-detail-recent-run-count");
  const recentRunsState = document.getElementById("job-detail-recent-runs-state");
  const recentRunsList = document.getElementById("job-detail-recent-runs-list");
  if (normalizedJobKey === "" || !runCount || !recentRunsState || !recentRunsList) {
    return;
  }

  try {
    const response = await fetch(`/api/v1/jobs/${encodeURIComponent(normalizedJobKey)}`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Job detail API returned ${response.status}`);
    }
    const payload = await response.json();
    const recentRuns = Array.isArray(payload.recentRuns) ? payload.recentRuns : [];
    runCount.textContent = String(recentRuns.length);
    renderJobDetailRecentRuns(recentRuns, normalizedJobKey, query);
  } catch (error) {
    recentRunsState.className = "state error";
    recentRunsState.textContent = `Unable to refresh recent runs: ${error.message}`;
    recentRunsList.hidden = true;
  }
}

function invalidateRunsState() {
  viewState.runs.loaded = false;
  viewState.runs.loadedForKey = "";
  viewState.runs.cache.byFilter = {};
  viewState.runs.cache.order = [];
}

async function refreshRunsAfterTriggerAccepted() {
  invalidateRunsState();
  if (currentRouteState().key === "runs") {
    await loadRuns({ forceRefresh: true });
  }
}

function scheduleFollowUpRunsRefresh() {
  // Runs projection can lag initial launch; staggered refresh helps surface new runs.
  [2000, 5000, 9000, 15000].forEach((delayMs) => {
    window.setTimeout(() => {
      refreshRunsAfterTriggerAccepted().catch(() => {
        // Primary feedback already shown; ignore follow-up refresh failures.
      });
    }, delayMs);
  });
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
      const sourceScheduleListQuery = String(query?.scheduleListQuery || "").trim();
      if (source === "schedule" && sourceScheduleId !== "") {
        params.set("scheduleId", sourceScheduleId);
        if (sourceScheduleListQuery !== "") {
          params.set("scheduleListQuery", sourceScheduleListQuery);
        }
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

    if (scheduleControlState.pauseResumeDisabled) {
      scheduleActionButton.textContent = scheduleControlState.detailPauseResumeLabel;
      scheduleActionButton.disabled = true;
      scheduleActionButton.onclick = null;
      scheduleFeedback.className = "state";
      scheduleFeedback.textContent = "Schedule is disabled. Pause/resume controls apply to enabled schedules only.";
      scheduleFeedback.hidden = false;
      return;
    }

    const action = scheduleControlState.pauseResumeAction;
    scheduleActionButton.textContent = scheduleControlState.detailPauseResumeLabel;
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
    triggerFeedback.className = "state state-warning";
    triggerFeedback.textContent = "Trigger already accepted recently in this browser tab. Please wait a few seconds before retrying.";
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
      const decisionStatus = String(payload.decisionStatus || "").trim();
      const duplicateSuppressed = decisionStatus === "DUPLICATE_SUPPRESSED";
      const launchSkipped = decisionStatus === "LAUNCH_SKIPPED";
      triggerFeedback.className = duplicateSuppressed || launchSkipped ? "state state-warning" : "state state-success";
      triggerFeedback.textContent = duplicateSuppressed
        ? `Trigger already accepted recently. decision=${valueOrDash(payload.decisionStatus)} triggerEventId=${eventId}`
        : launchSkipped
          ? `Trigger accepted but launch skipped. decision=${valueOrDash(payload.decisionStatus)} triggerEventId=${eventId}. ${valueOrDash(payload.message)}`
          : `Trigger accepted. decision=${valueOrDash(payload.decisionStatus)} triggerEventId=${eventId}`;
      triggerFeedback.hidden = false;
      if (!launchSkipped) {
        triggerNowRequestState.cooldownUntilByJobKey[normalizedJobKey] = Date.now() + TRIGGER_NOW_DUPLICATE_WINDOW_MS;
      }

      await refreshJobDetailRecentTriggerEvents(normalizedJobKey, currentRouteState()?.query);
      if (!launchSkipped) {
        await refreshJobDetailRecentRuns(normalizedJobKey, currentRouteState()?.query);
        await refreshRunsAfterTriggerAccepted();
        scheduleFollowUpRunsRefresh();
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
  syncSchedulesControlsFromState();

  try {
    await ensureScheduleEditorJobOptions();
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
    if (viewState.schedules.editingScheduleId) {
      const editingExists = items.some((item) => String(item?.scheduleId || "").trim() === viewState.schedules.editingScheduleId);
      if (!editingExists && viewState.schedules.editorMode === "edit") {
        closeScheduleEditor({ navigateToReturnHash: false });
      }
    }

    if (items.length === 0) {
      state.textContent = "No schedules found.";
      renderSchedulesTable([], requestId);
      table.hidden = false;
      return;
    }

    const visibleCount = renderSchedulesTable(items, requestId);
    state.textContent = visibleCount === 0
      ? `Loaded ${items.length} schedule(s). No schedules match the current filters.`
      : `Loaded ${items.length} schedule(s).`;
    table.hidden = false;
    focusSelectedScheduleRow();
    consumePendingScheduleEditIntent(items);
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("schedules", requestId)) {
      return;
    }
    state.className = "state error";
    state.textContent = `Unable to load schedules: ${error.message}`;
  }
}

function consumePendingScheduleEditIntent(items) {
  const pendingEditScheduleId = String(viewState.schedules.pendingEditScheduleId || "").trim();
  if (pendingEditScheduleId === "") {
    return;
  }

  viewState.schedules.pendingEditScheduleId = "";
  const routeState = currentRouteState();
  const editReturn = String(routeState?.query?.editReturn || "").trim().toLowerCase();
  const scheduleListQuery = buildSchedulesListQueryFromRouteQuery(routeState?.query);
  viewState.schedules.editCancelReturnHash = editReturn === "detail"
    ? buildScheduleDetailHash(pendingEditScheduleId, scheduleListQuery)
    : "";

  const schedule = (Array.isArray(items) ? items : []).find((item) => String(item?.scheduleId || "").trim() === pendingEditScheduleId);
  if (schedule) {
    openScheduleEditor({ mode: "edit", schedule });
  }

  if (routeState.key === "schedules" && String(routeState?.query?.editScheduleId || "").trim() !== "") {
    syncListRouteHash("schedules");
  }
}

function renderSchedulesTable(items, requestId) {
  const body = document.getElementById("schedules-body");
  if (!body) {
    return;
  }
  body.innerHTML = "";
  const pageStatus = document.getElementById("schedules-page-status");
  const prevButton = document.getElementById("schedules-page-prev-btn");
  const nextButton = document.getElementById("schedules-page-next-btn");

  const sorted = sortSchedulesItems(
    filterSchedulesItems(items, viewState.schedules.filterText, formatScheduleStatus),
    viewState.schedules.sortKey,
    viewState.schedules.sortDirection,
    {
      normalizeSortKey,
      normalizeDirection,
      formatScheduleStatus,
    }
  );
  const pageSize = normalizePageSize(viewState.schedules.pageSize, defaultSchedulesPageSize());
  const totalPages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const page = Math.min(Math.max(1, viewState.schedules.page), totalPages);
  viewState.schedules.page = page;
  viewState.schedules.pageSize = pageSize;

  const startIndex = (page - 1) * pageSize;
  const pageItems = sorted.slice(startIndex, startIndex + pageSize);

  if (pageStatus) {
    pageStatus.textContent = `Page ${page} of ${totalPages}`;
  }
  if (prevButton) {
    prevButton.disabled = page <= 1;
  }
  if (nextButton) {
    nextButton.disabled = page >= totalPages;
  }

  pageItems.forEach((schedule) => {
    const row = document.createElement("tr");
    const scheduleId = String(schedule?.scheduleId || "").trim();
    row.className = "clickable-row";
    row.dataset.scheduleId = scheduleId;
    row.title = "Open schedule detail";
    row.addEventListener("click", () => {
      if (scheduleId === "") {
        return;
      }
      const scheduleQuerySuffix = getSchedulesRouteQuerySuffix();
      location.hash = scheduleQuerySuffix === ""
        ? `#/schedules/${encodeURIComponent(scheduleId)}`
        : `#/schedules/${encodeURIComponent(scheduleId)}${scheduleQuerySuffix}`;
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
      const scheduleListQuery = buildSchedulesRouteQuery(viewState.schedules, { includeSelectedScheduleId: false });
      location.hash = selectedScheduleId === ""
        ? `#/jobs/${encodeURIComponent(selectedJobKey)}`
        : scheduleListQuery === ""
          ? `#/jobs/${encodeURIComponent(selectedJobKey)}?from=schedule&scheduleId=${encodeURIComponent(selectedScheduleId)}`
          : `#/jobs/${encodeURIComponent(selectedJobKey)}?from=schedule&scheduleId=${encodeURIComponent(selectedScheduleId)}&scheduleListQuery=${encodeURIComponent(scheduleListQuery)}`;
    });

    const triggerNowButton = document.createElement("button");
    triggerNowButton.type = "button";
    triggerNowButton.textContent = "Trigger now";
    triggerNowButton.addEventListener("click", async (event) => {
      event.stopPropagation();
      await requestScheduleWorkbenchTriggerNow(schedule, requestId);
    });

    actions.append(
      openJobButton,
      document.createTextNode(" "),
      triggerNowButton
    );
    row.innerHTML = `
      <td>${escapeHtml(valueOrDash(schedule.scheduleKey))}</td>
      <td>${escapeHtml(valueOrDash(schedule.selectedJobKey))}</td>
      <td>${escapeHtml(formatScheduleStatus(schedule))}</td>
      <td>${escapeHtml(valueOrDash(schedule.nextDueAt))}</td>`;
    row.appendChild(actions);
    body.appendChild(row);
  });

  return pageItems.length;
}

function focusSelectedScheduleRow() {
  const selectedScheduleId = String(viewState.schedules.selectedScheduleId || "").trim();
  const body = document.getElementById("schedules-body");
  if (!body) {
    return;
  }

  const rows = Array.from(body.querySelectorAll("tr[data-schedule-id]"));
  if (rows.length === 0) {
    return;
  }

  let selectedRow = null;
  rows.forEach((row) => {
    const rowScheduleId = String(row.dataset.scheduleId || "").trim();
    const isSelected = selectedScheduleId !== "" && rowScheduleId === selectedScheduleId;
    row.classList.toggle("schedule-selected-row", isSelected);
    if (isSelected) {
      selectedRow = row;
    }
  });

  if (!selectedRow) {
    return;
  }

  if (typeof selectedRow.scrollIntoView === "function") {
    selectedRow.scrollIntoView({ block: "center", behavior: "smooth" });
  }
}

async function ensureScheduleEditorJobOptions() {
  const select = document.getElementById("schedules-editor-job-select");
  if (!select) {
    return;
  }
  await ensureRunsJobOptions();

  const currentValue = String(select.value || "").trim();
  select.innerHTML = '<option value="">Select job</option>';

  const options = [...viewState.runs.jobOptions].sort((left, right) =>
    String(left.displayName || left.jobKey || "").localeCompare(String(right.displayName || right.jobKey || ""))
  );

  options.forEach((job) => {
    const option = document.createElement("option");
    option.value = String(job.jobKey || "").trim();
    option.textContent = job.displayName
      ? `${job.displayName} (${job.jobKey})`
      : valueOrDash(job.jobKey);
    select.appendChild(option);
  });

  if (currentValue !== "") {
    select.value = currentValue;
  }

  updateScheduleEditorExpressionValidation();
}

function updateScheduleEditorExpressionValidation() {
  return scheduleEditorHelpers.updateScheduleEditorExpressionValidation();
}

function openScheduleEditor({ mode, schedule } = {}) {
  const prepared = scheduleEditorHelpers.prepareScheduleEditorForOpen({ mode, schedule });
  if (!prepared) {
    return;
  }

  const { editor, jobSelect, normalizedMode, currentSchedule } = prepared;
  updateScheduleEditorExpressionValidation();

  ensureScheduleEditorJobOptions().then(() => {
    if (normalizedMode === "edit") {
      jobSelect.value = String(currentSchedule?.selectedJobKey || "");
    }
    updateScheduleEditorExpressionValidation();
  });

  if (typeof editor.scrollIntoView === "function") {
    editor.scrollIntoView({ block: "start", behavior: "smooth" });
  }
}

function closeScheduleEditor(options = {}) {
  const navigateToReturnHash = options.navigateToReturnHash !== false;
  const returnHash = String(viewState.schedules.editCancelReturnHash || "").trim();
  if (!scheduleEditorHelpers.closeScheduleEditorUi()) {
    return;
  }

  if (navigateToReturnHash && returnHash !== "" && location.hash !== returnHash) {
    location.hash = returnHash;
  }
}

async function submitScheduleEditor() {
  const state = document.getElementById("schedules-editor-state");
  const saveButton = document.getElementById("schedules-editor-save-btn");
  const keyInput = document.getElementById("schedules-editor-key-input");
  const jobSelect = document.getElementById("schedules-editor-job-select");
  const expressionInput = document.getElementById("schedules-editor-expression-input");
  const timezoneInput = document.getElementById("schedules-editor-timezone-input");
  const descriptionInput = document.getElementById("schedules-editor-description-input");
  const enabledInput = document.getElementById("schedules-editor-enabled-input");
  if (!state || !saveButton || !keyInput || !jobSelect || !expressionInput || !timezoneInput || !descriptionInput || !enabledInput) {
    return;
  }

  const mode = viewState.schedules.editorMode === "edit" ? "edit" : "create";
  const scheduleKey = String(keyInput.value || "").trim();
  const selectedJobKey = String(jobSelect.value || "").trim();
  const expression = String(expressionInput.value || "").trim();
  const timezone = String(timezoneInput.value || "").trim() || "UTC";
  const description = String(descriptionInput.value || "").trim();
  const enabled = Boolean(enabledInput.checked);

  if (mode === "create" && scheduleKey === "") {
    state.className = "state error";
    state.textContent = "Schedule key is required for create.";
    return;
  }
  const expressionCheck = validateScheduleExpression(expression);
  if (selectedJobKey === "" || !expressionCheck.valid) {
    state.className = "state error";
    state.textContent = selectedJobKey === "" ? "Job is required." : expressionCheck.message;
    updateScheduleEditorExpressionValidation();
    return;
  }

  const requestBody = {
    selectedJobKey,
    expression,
    timezone,
    enabled,
    description: description === "" ? null : description,
  };
  if (mode === "create") {
    requestBody.scheduleKey = scheduleKey;
  }

  const editingScheduleId = String(viewState.schedules.editingScheduleId || "").trim();
  const editCancelReturnHash = String(viewState.schedules.editCancelReturnHash || "").trim();
  const returnToDetailAfterSave = mode === "edit" && editCancelReturnHash !== "";
  const method = mode === "edit" ? "PUT" : "POST";
  const target = mode === "edit"
    ? `/api/v1/schedules/${encodeURIComponent(editingScheduleId)}`
    : "/api/v1/schedules";

  saveButton.disabled = true;
  state.className = "state";
  state.textContent = mode === "edit" ? "Updating schedule..." : "Creating schedule...";

  try {
    const response = await fetch(target, {
      method,
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(requestBody),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      state.className = "state error";
      state.textContent = `${mode === "edit" ? "Update" : "Create"} failed: ${valueOrDash(payload.message)}`;
      return;
    }

    state.className = "state";
    state.textContent = mode === "edit" ? "Schedule updated." : "Schedule created.";
    closeScheduleEditor({ navigateToReturnHash: returnToDetailAfterSave });
    viewState.schedules.loaded = false;
    if (returnToDetailAfterSave) {
      return;
    }
    const activeRoute = currentRouteState();
    if (activeRoute.key === "scheduleDetail") {
      await loadScheduleDetail(activeRoute);
      return;
    }
    await loadSchedules();
  } catch (error) {
    state.className = "state error";
    state.textContent = `${mode === "edit" ? "Update" : "Create"} failed [runtime]: ${error.message}`;
  } finally {
    saveButton.disabled = false;
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
    const response = await fetch(`/api/v1/schedules/${encodeURIComponent(scheduleId)}:trigger-now`, {
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
      const backendMessage = String(payload?.message || "").trim();
      const detail = backendMessage !== "" ? backendMessage : `status=${response.status}`;
      const endpointHint = response.status === 404
        ? "Schedule trigger-now endpoint is unavailable in the running backend. Restart the app with the latest build."
        : "";
      state.className = "state error";
      state.textContent = endpointHint === ""
        ? `Trigger now failed: ${detail}`
        : `Trigger now failed: ${detail}. ${endpointHint}`;
      return;
    }
    const decisionStatus = String(payload.decisionStatus || "").trim();
    const launchSkipped = decisionStatus === "LAUNCH_SKIPPED";
    state.className = (decisionStatus === "DUPLICATE_SUPPRESSED" || launchSkipped) ? "state state-warning" : "state state-success";
    state.textContent = launchSkipped
      ? `Trigger now accepted but launch skipped for ${selectedJobKey}. decision=${valueOrDash(payload.decisionStatus)} triggerEventId=${valueOrDash(payload.triggerEventId)}. ${valueOrDash(payload.message)}`
      : `Trigger now accepted for ${selectedJobKey}. decision=${valueOrDash(payload.decisionStatus)} triggerEventId=${valueOrDash(payload.triggerEventId)}`;
    if (!shouldApplyRouteScopedUpdate("schedules", requestId)) {
      return;
    }
    viewState.schedules.selectedScheduleId = scheduleId;
    if (!launchSkipped) {
      await refreshRunsAfterTriggerAccepted();
      scheduleFollowUpRunsRefresh();
    }
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
  const actionState = document.getElementById("schedule-detail-action-state");
  const editButton = document.getElementById("schedule-detail-edit-btn");
  const enableDisableButton = document.getElementById("schedule-detail-enable-disable-btn");
  const pauseResumeButton = document.getElementById("schedule-detail-pause-resume-btn");
  const triggerToggleButton = document.getElementById("schedule-detail-triggers-toggle-btn");
  const triggerPanel = document.getElementById("schedule-detail-triggers-panel");
  const triggerState = document.getElementById("schedule-detail-triggers-state");
  const triggerList = document.getElementById("schedule-detail-triggers-list");
  const backLink = document.getElementById("schedule-detail-back-link");
  const scheduleId = String(routeState?.selectedScheduleId || "").trim();
  const scheduleListQuery = buildSchedulesListQueryFromRouteQuery(routeState?.query);
  const preserveLayout = Boolean(viewState.schedules.refreshDetailInPlace);

  if (!state || !summary || !actionState || !editButton || !enableDisableButton || !pauseResumeButton || !triggerToggleButton || !triggerPanel || !triggerState || !triggerList || !backLink) {
    return;
  }

  if (!preserveLayout) {
    viewState.schedules.triggersExpanded = false;
  }
  triggerToggleButton.onclick = () => {
    viewState.schedules.triggersExpanded = !viewState.schedules.triggersExpanded;
    setScheduleDetailTriggersExpanded(viewState.schedules.triggersExpanded);
  };
  setScheduleDetailTriggersExpanded(viewState.schedules.triggersExpanded);

  backLink.setAttribute("href", buildSchedulesListHash(scheduleId, scheduleListQuery));
  if (!preserveLayout) {
    state.className = "state";
    state.textContent = "Loading schedule detail...";
    summary.hidden = true;
    actionState.className = "state";
    actionState.textContent = "";
    actionState.hidden = true;
  }
  editButton.disabled = true;
  editButton.onclick = null;
  enableDisableButton.disabled = true;
  pauseResumeButton.disabled = true;
  if (!preserveLayout) {
    triggerState.className = "state";
    triggerState.textContent = "Loading triggers...";
  }
  if (!preserveLayout) {
    triggerList.hidden = true;
    triggerList.innerHTML = "";
  }

  if (scheduleId === "") {
    state.className = "state error";
    state.textContent = "Missing schedule id in route.";
    return;
  }

  viewState.schedules.selectedScheduleId = scheduleId;

  try {
    const response = await fetch(`/api/v1/schedules/${encodeURIComponent(scheduleId)}`, {
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      throw new Error(`Schedule API returned ${response.status}`);
    }
    const selected = await response.json();
    if (!shouldApplyRouteScopedUpdate("scheduleDetail", requestId)) {
      return;
    }

    document.getElementById("schedule-detail-id").textContent = valueOrDash(selected.scheduleId);
    document.getElementById("schedule-detail-key").textContent = valueOrDash(selected.scheduleKey);
    document.getElementById("schedule-detail-job").textContent = valueOrDash(selected.selectedJobKey);
    document.getElementById("schedule-detail-status").textContent = formatScheduleStatus(selected);
    document.getElementById("schedule-detail-timezone").textContent = valueOrDash(selected.timezone);
    document.getElementById("schedule-detail-expression").textContent = valueOrDash(selected.expression);
    document.getElementById("schedule-detail-description").textContent = valueOrDash(selected.description);
    document.getElementById("schedule-detail-last-accepted-due").textContent = valueOrDash(selected.lastAcceptedDueAt);
    document.getElementById("schedule-detail-next-due").textContent = valueOrDash(selected.nextDueAt);

    editButton.disabled = false;
    editButton.onclick = () => {
      viewState.schedules.editCancelReturnHash = "";
      openScheduleEditor({ mode: "edit", schedule: selected });
    };

    const scheduleControlState = getScheduleControlState(selected);
    enableDisableButton.textContent = scheduleControlState.enableDisableLabel;
    enableDisableButton.disabled = false;
    enableDisableButton.onclick = () => requestScheduleDetailStateChange(scheduleId, scheduleControlState.enableDisableAction, requestId);

    pauseResumeButton.textContent = scheduleControlState.pauseResumeLabel;
    pauseResumeButton.disabled = scheduleControlState.pauseResumeDisabled;
    pauseResumeButton.onclick = () => requestScheduleDetailStateChange(scheduleId, scheduleControlState.pauseResumeAction, requestId);

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
      triggerList.hidden = true;
      triggerList.innerHTML = "";
      triggerState.textContent = "No trigger events recorded for this schedule yet.";
    } else {
      const triggerNodes = document.createDocumentFragment();
      triggerItems.forEach((item) => {
        triggerNodes.appendChild(scheduleDetailHelpers.buildScheduleTriggerEventLine(item, scheduleId, scheduleListQuery));
      });
      triggerList.innerHTML = "";
      triggerList.appendChild(triggerNodes);
      triggerState.textContent = `Showing ${triggerItems.length} trigger event(s).`;
      triggerList.hidden = false;
    }

    if (!preserveLayout) {
      state.textContent = "Schedule detail loaded.";
    }
  } catch (error) {
    if (!shouldApplyRouteScopedUpdate("scheduleDetail", requestId)) {
      return;
    }
    state.className = "state error";
    state.textContent = `Unable to load schedule detail: ${error.message}`;
  }
}

function setScheduleDetailTriggersExpanded(expanded) {
  return scheduleDetailHelpers.setScheduleDetailTriggersExpanded(expanded);
}

async function requestScheduleDetailStateChange(scheduleId, action, requestId) {
  const state = document.getElementById("schedule-detail-action-state");
  const normalizedScheduleId = String(scheduleId || "").trim();
  const normalizedAction = String(action || "").trim().toLowerCase();
  if (!state || normalizedScheduleId === "" || !SCHEDULE_STATE_CHANGE_ACTIONS.has(normalizedAction)) {
    return;
  }
  if (scheduleRequestState.inFlightActionByScheduleId[normalizedScheduleId]) {
    state.className = "state";
    state.textContent = "Schedule action already in progress. Please wait for the current response.";
    state.hidden = false;
    return;
  }

  scheduleRequestState.inFlightActionByScheduleId[normalizedScheduleId] = true;
  viewState.schedules.refreshDetailInPlace = true;
  const previousScrollY = typeof window !== "undefined" && Number.isFinite(window.scrollY)
    ? window.scrollY
    : null;
  state.className = "state";
  state.textContent = `Submitting schedule ${normalizedAction} request...`;
  state.hidden = false;

  try {
    const response = await fetch(`/api/v1/schedules/${encodeURIComponent(normalizedScheduleId)}:${normalizedAction}`, {
      method: "POST",
      headers: { Accept: "application/json" },
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      state.className = "state error";
      state.textContent = `Schedule ${normalizedAction} failed: ${valueOrDash(payload.message)}`;
      return;
    }

    state.className = "state";
    state.textContent = `Schedule ${normalizedAction} accepted.`;
    viewState.schedules.loaded = false;
    await loadScheduleDetail(currentRouteState());
    if (previousScrollY !== null && typeof window.scrollTo === "function") {
      window.scrollTo({ top: previousScrollY, left: 0, behavior: "auto" });
    }
  } catch (error) {
    state.className = "state error";
    state.textContent = `Schedule ${normalizedAction} failed [runtime]: ${error.message}`;
  } finally {
    viewState.schedules.refreshDetailInPlace = false;
    delete scheduleRequestState.inFlightActionByScheduleId[normalizedScheduleId];
  }
}

// schedule detail now uses a dedicated route (`#/schedules/{scheduleId}`), so
// list-selection hash syncing is intentionally not used.


async function loadRuns(options = {}) {
  const requestId = ++loadRequestTracker.runs;
  const forceRefresh = Boolean(options?.forceRefresh);
  const state = document.getElementById("runs-state");
  const table = document.getElementById("runs-table");
  const body = document.getElementById("runs-body");
  const selectedJobKey = String(viewState.runs.selectedJobKey || "").trim();
  const selectedRunMode = normalizeSupportedFilter(viewState.runs.runModeFilter, SUPPORTED_RUN_MODES);
  const selectedRecoveryPolicy = normalizeSupportedFilter(viewState.runs.recoveryPolicyFilter, SUPPORTED_RECOVERY_POLICIES);
  const selectedTriggerSource = normalizeTriggerSourceFilter(viewState.runs.triggerSourceFilter);
  const selectedStartDate = normalizeIsoDate(viewState.runs.startDate);
  const selectedTimezone = String(viewState.runs.timezone || viewState.runs.browserTimezone || "UTC").trim() || "UTC";

  viewState.runs.selectedJobKey = selectedJobKey;
  viewState.runs.runModeFilter = selectedRunMode;
  viewState.runs.recoveryPolicyFilter = selectedRecoveryPolicy;
  viewState.runs.triggerSourceFilter = selectedTriggerSource;
  viewState.runs.startDate = selectedStartDate;
  viewState.runs.timezone = selectedTimezone;
  const loadKey = `${selectedJobKey || "__all__"}|${selectedRunMode || "__all_mode__"}|${selectedRecoveryPolicy || "__all_policy__"}|${selectedTriggerSource || "__all_source__"}|${selectedStartDate || "__no_date__"}|${selectedTimezone}`;

  if (!forceRefresh && viewState.runs.loaded && viewState.runs.loadedForKey === loadKey) {
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
    await ensureRunsTriggerSourceOptions();
    const runs = await fetchRunsForFilters(
      selectedJobKey,
      selectedRunMode,
      selectedRecoveryPolicy,
      selectedTriggerSource,
      selectedStartDate,
      selectedTimezone,
      { bypassCache: forceRefresh }
    );
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

function normalizeSupportedFilter(value, supportedValues) {
  return runsBridgeHelpers.normalizeSupportedFilter(value, supportedValues);
}

function normalizeTriggerSourceFilter(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if (normalized === "") {
    return "";
  }
  const sourceCodes = new Set(
    (Array.isArray(viewState.runs.triggerSourceOptions) ? viewState.runs.triggerSourceOptions : [])
      .map((item) => String(item?.sourceCode || "").trim().toUpperCase())
      .filter((item) => item !== "")
  );
  if (sourceCodes.size === 0) {
    return normalized;
  }
  return sourceCodes.has(normalized) ? normalized : "";
}

function normalizeIsoDate(value) {
  return runsBridgeHelpers.normalizeIsoDate(value);
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
  return runsBridgeHelpers.formatDateForInput(date);
}

function initializeControls() {
  jobsListUi.initializeControls();
  runsListUi.initializeControls();
  initializeScheduleControls();
}

function initializeScheduleControls() {
  const newButton = document.getElementById("schedules-new-btn");
  const refreshButton = document.getElementById("schedules-refresh-btn");
  const filterInput = document.getElementById("schedules-filter-input");
  const sortSelect = document.getElementById("schedules-sort-select");
  const sortDirectionButton = document.getElementById("schedules-sort-dir-btn");
  const pageSizeSelect = document.getElementById("schedules-page-size-select");
  const pagePrevButton = document.getElementById("schedules-page-prev-btn");
  const pageNextButton = document.getElementById("schedules-page-next-btn");
  const saveButton = document.getElementById("schedules-editor-save-btn");
  const cancelButton = document.getElementById("schedules-editor-cancel-btn");
  const expressionInput = document.getElementById("schedules-editor-expression-input");
  const scheduleKeyInput = document.getElementById("schedules-editor-key-input");
  const jobSelect = document.getElementById("schedules-editor-job-select");
  if (!refreshButton || !newButton || !filterInput || !sortSelect || !sortDirectionButton || !pageSizeSelect || !pagePrevButton || !pageNextButton || !saveButton || !cancelButton || !expressionInput || !scheduleKeyInput || !jobSelect) {
    return;
  }

  filterInput.value = viewState.schedules.filterText;
  sortSelect.value = viewState.schedules.sortKey;
  sortDirectionButton.textContent = viewState.schedules.sortDirection === "desc" ? "Desc" : "Asc";
  pageSizeSelect.value = String(viewState.schedules.pageSize);

  filterInput.addEventListener("input", () => {
    viewState.schedules.filterText = String(filterInput.value || "");
    viewState.schedules.page = 1;
    syncListRouteHash("schedules");
  });
  sortSelect.addEventListener("change", () => {
    viewState.schedules.sortKey = normalizeSortKey("schedules", sortSelect.value, "scheduleKey");
    viewState.schedules.page = 1;
    syncListRouteHash("schedules");
  });
  sortDirectionButton.addEventListener("click", () => {
    viewState.schedules.sortDirection = viewState.schedules.sortDirection === "asc" ? "desc" : "asc";
    sortDirectionButton.textContent = viewState.schedules.sortDirection === "desc" ? "Desc" : "Asc";
    syncListRouteHash("schedules");
  });
  pageSizeSelect.addEventListener("change", () => {
    viewState.schedules.pageSize = normalizePageSize(pageSizeSelect.value, defaultSchedulesPageSize());
    pageSizeSelect.value = String(viewState.schedules.pageSize);
    viewState.schedules.page = 1;
    syncListRouteHash("schedules");
  });
  pagePrevButton.addEventListener("click", () => {
    if (viewState.schedules.page <= 1) {
      return;
    }
    viewState.schedules.page -= 1;
    syncListRouteHash("schedules");
  });
  pageNextButton.addEventListener("click", () => {
    viewState.schedules.page += 1;
    syncListRouteHash("schedules");
  });

  newButton.addEventListener("click", () => {
    openScheduleEditor({ mode: "create" });
  });
  refreshButton.addEventListener("click", () => {
    viewState.schedules.loaded = false;
    if (currentRouteState().key === "schedules") {
      loadSchedules();
    }
  });
  saveButton.addEventListener("click", submitScheduleEditor);
  cancelButton.addEventListener("click", () => closeScheduleEditor());
  expressionInput.addEventListener("input", updateScheduleEditorExpressionValidation);
  scheduleKeyInput.addEventListener("input", updateScheduleEditorExpressionValidation);
  jobSelect.addEventListener("change", updateScheduleEditorExpressionValidation);
}

function applyRouteStateToListView(routeState) {
  if (routeState.key === "jobs") {
    jobsListUi.applyRouteState(routeState);
    return;
  }

  if (routeState.key === "schedules") {
    applySchedulesRouteState(routeState);
    return;
  }

  runsListUi.applyRouteState(routeState);
}

function applySchedulesRouteState(routeState) {
  viewState.schedules.filterText = String(routeState.filterText || "");
  viewState.schedules.page = normalizePositiveInteger(routeState.page, 1);
  viewState.schedules.pageSize = normalizePageSize(routeState.pageSize, defaultSchedulesPageSize());
  viewState.schedules.sortKey = normalizeSortKey("schedules", routeState.sortKey, "scheduleKey");
  viewState.schedules.sortDirection = normalizeDirection(routeState.sortDirection, "asc");
  const selectedScheduleId = String(routeState.selectedScheduleId || "").trim();
  if (selectedScheduleId !== "") {
    viewState.schedules.selectedScheduleId = selectedScheduleId;
  }
  viewState.schedules.pendingEditScheduleId = String(routeState.editScheduleId || "").trim();
  syncSchedulesControlsFromState();
}

function syncSchedulesControlsFromState() {
  const filterInput = document.getElementById("schedules-filter-input");
  const sortSelect = document.getElementById("schedules-sort-select");
  const sortDirectionButton = document.getElementById("schedules-sort-dir-btn");
  const pageSizeSelect = document.getElementById("schedules-page-size-select");
  if (filterInput) {
    filterInput.value = viewState.schedules.filterText;
  }
  if (sortSelect) {
    sortSelect.value = viewState.schedules.sortKey;
  }
  if (sortDirectionButton) {
    sortDirectionButton.textContent = viewState.schedules.sortDirection === "desc" ? "Desc" : "Asc";
  }
  if (pageSizeSelect) {
    pageSizeSelect.value = String(viewState.schedules.pageSize);
  }
}

function syncListRouteHash(routeKey) {
  return routeHelpers.syncListRouteHash(routeKey);
}

function getJobsRouteQuerySuffix() {
  return routeHelpers.getJobsRouteQuerySuffix();
}

function getSchedulesRouteQuerySuffix() {
  return routeHelpers.getSchedulesRouteQuerySuffix();
}

function buildSchedulesRouteQuery(source, options = {}) {
  return routeHelpers.buildSchedulesRouteQuery(source, options);
}

function buildSchedulesListQueryFromRouteQuery(query) {
  return routeHelpers.buildSchedulesListQueryFromRouteQuery(query);
}

function buildSchedulesListHash(scheduleId, scheduleListQuery) {
  return routeHelpers.buildSchedulesListHash(scheduleId, scheduleListQuery);
}

function buildScheduleDetailHash(scheduleId, scheduleListQuery) {
  return routeHelpers.buildScheduleDetailHash(scheduleId, scheduleListQuery);
}

function parseHashRoute() {
  return routeHelpers.parseHashRoute();
}

function shouldApplyRouteScopedUpdate(routeKey, requestId, routeValue) {
  return routeUpdateGuards.shouldApplyRouteScopedUpdate(routeKey, requestId, routeValue);
}


function normalizeSortKey(routeKey, value, fallback) {
  return routeHelpers.normalizeSortKey(routeKey, value, fallback);
}

function normalizeDirection(value, fallback) {
  return routeHelpers.normalizeDirection(value, fallback);
}

function normalizePositiveInteger(value, fallback) {
  return routeHelpers.normalizePositiveInteger(value, fallback);
}

function normalizePageSize(value, fallback) {
  return routeHelpers.normalizePageSize(value, fallback);
}

function defaultJobsPageSize() {
  return defaultJobsPageSizeValue();
}

function defaultSchedulesPageSize() {
  return defaultSchedulesPageSizeValue();
}

function getQuerySuffix(query) {
  return routeHelpers.getQuerySuffix(query);
}

async function ensureRunsJobOptions() {
  if (viewState.runs.jobOptions.length > 0) {
    return;
  }

  const jobs = viewState.jobs.loaded
    ? viewState.jobs.items
    : await runsBridgeHelpers.fetchJobsForRunsScope();

  viewState.runs.jobOptions = mapJobsToRunsJobOptions(jobs);
  renderRunsJobOptions();
}

async function ensureRunsTriggerSourceOptions() {
  if (viewState.runs.triggerSourceOptions.length > 0) {
    return;
  }
  try {
    const options = await fetchTriggerSourceOptionsValue();
    viewState.runs.triggerSourceOptions = Array.isArray(options) ? options : [];
  } catch {
    viewState.runs.triggerSourceOptions = [
      { sourceCode: "MANUAL", displayName: "Manual" },
      { sourceCode: "SCHEDULE", displayName: "Schedule" },
      { sourceCode: "EVENT", displayName: "Event" },
    ];
  }
  renderRunsTriggerSourceOptions();
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

function renderRunsTriggerSourceOptions() {
  const select = document.getElementById("runs-trigger-source-select");
  if (!select) {
    return;
  }

  const selected = viewState.runs.triggerSourceFilter || "";
  select.innerHTML = '<option value="">All sources</option>';
  const options = Array.isArray(viewState.runs.triggerSourceOptions)
    ? viewState.runs.triggerSourceOptions
    : [];
  options.forEach((source) => {
    const option = document.createElement("option");
    option.value = String(source?.sourceCode || "").trim();
    option.textContent = String(source?.displayName || source?.sourceCode || "-").trim();
    if (option.value !== "") {
      select.appendChild(option);
    }
  });
  select.value = selected;
}


function applyJobsItems(items) {
  return runsBridgeHelpers.applyJobsItems(items);
}

async function fetchRunsForFilters(selectedJobKey, runMode, recoveryPolicy, triggerSource, startDate, timezone, requestOptions = {}) {
  return runsBridgeHelpers.fetchRunsForFilters(selectedJobKey, runMode, recoveryPolicy, triggerSource, startDate, timezone, requestOptions);
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
  const sourceScheduleListQuery = String(routeState?.query?.scheduleListQuery || "").trim();

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
      backLink.setAttribute("href", buildSchedulesListHash(sourceScheduleId, sourceScheduleListQuery));
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

    runDetailHelpers.renderRunSteps(payload.steps);
    runDetailHelpers.renderRunFailureSummary(payload.failureSummary);
    runDetailHelpers.renderRunArtifacts(payload.artifacts);
    runDetailHelpers.renderRunEvidenceLinks(payload.evidenceLinks);

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

    await recoveryPromise;

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




