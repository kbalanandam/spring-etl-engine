const routes = {
  jobs: {
    tab: document.getElementById("tab-jobs"),
    view: document.getElementById("view-jobs"),
    load: loadJobs,
  },
  runs: {
    tab: document.getElementById("tab-runs"),
    view: document.getElementById("view-runs"),
    load: loadRuns,
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

function currentRouteKey() {
  const hash = location.hash.replace(/^#\/?/, "").toLowerCase();
  if (hash === "runs") {
    return "runs";
  }
  return "jobs";
}

function renderRoute() {
  const routeKey = currentRouteKey();

  Object.entries(routes).forEach(([key, route]) => {
    const active = key === routeKey;
    route.tab.classList.toggle("active", active);
    route.view.hidden = !active;
  });

  routes[routeKey].load();
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

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

