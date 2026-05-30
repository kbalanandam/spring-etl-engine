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

window.addEventListener("hashchange", renderRoute);
window.addEventListener("DOMContentLoaded", () => {
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
    const items = Array.isArray(payload.items) ? payload.items : [];

    if (items.length === 0) {
      state.textContent = "No job bundles found.";
      return;
    }

    items.forEach((job) => {
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

    state.textContent = `Loaded ${items.length} job bundle(s).`;
    table.hidden = false;
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
    const items = Array.isArray(payload.items) ? payload.items : [];

    if (items.length === 0) {
      state.textContent = "No recent runs found.";
      return;
    }

    items.forEach((run) => {
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

    state.textContent = `Loaded ${items.length} run(s).`;
    table.hidden = false;
  } catch (error) {
    state.className = "state error";
    state.textContent = `Unable to load runs: ${error.message}`;
  }
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



