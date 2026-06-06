export function createRunLogViewer(options) {
  const valueOrDash = options && typeof options.valueOrDash === "function"
    ? options.valueOrDash
    : (value) => (value === null || value === undefined || value === "" ? "-" : String(value));
  const escapeHtml = options && typeof options.escapeHtml === "function"
    ? options.escapeHtml
    : (value) => String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&#39;");

  const state = {
    currentRunId: "",
    lines: [],
    truncated: false,
    searchText: "",
    structuredOnly: false,
    compact: true,
  };

  function initializeControls() {
    const searchInput = document.getElementById("run-detail-log-search");
    const structuredOnly = document.getElementById("run-detail-log-structured-only");
    const compact = document.getElementById("run-detail-log-compact");
    const copyVisible = document.getElementById("run-detail-log-copy-visible");

    if (!searchInput || !structuredOnly || !compact) {
      return;
    }

    searchInput.value = state.searchText;
    structuredOnly.checked = state.structuredOnly;
    compact.checked = state.compact;

    searchInput.addEventListener("input", (event) => {
      state.searchText = event.target.value || "";
      renderRunLogLines();
    });
    structuredOnly.addEventListener("change", (event) => {
      state.structuredOnly = Boolean(event.target.checked);
      renderRunLogLines();
    });
    compact.addEventListener("change", (event) => {
      state.compact = Boolean(event.target.checked);
      renderRunLogLines();
    });

    if (copyVisible) {
      copyVisible.dataset.label = copyVisible.textContent;
      copyVisible.addEventListener("click", () => {
        copyVisibleRunLogLines(copyVisible);
      });
      updateRunLogCopyVisibleButton(0);
    }
  }

  function resetContext(runIdValue) {
    const normalizedRunId = String(runIdValue || "");
    if (state.currentRunId === normalizedRunId) {
      return;
    }

    state.currentRunId = normalizedRunId;
    state.lines = [];
    state.truncated = false;
    state.searchText = "";

    const searchInput = document.getElementById("run-detail-log-search");
    if (searchInput) {
      searchInput.value = "";
    }
  }

  async function load(runIdValue) {
    const logState = document.getElementById("run-detail-log-state");
    const logList = document.getElementById("run-detail-log-list");
    const logEmpty = document.getElementById("run-detail-log-empty");
    const logControls = document.getElementById("run-detail-log-controls");

    if (!logState || !logList || !logEmpty || !logControls) {
      return;
    }

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
      if (response.status === 404) {
        state.lines = [];
        state.truncated = false;
        renderRunLogLines();
        return;
      }
      if (!response.ok) {
        throw new Error(`Run log API returned ${response.status}`);
      }
      const payload = await response.json();
      state.lines = Array.isArray(payload.lines) ? payload.lines : [];
      state.truncated = Boolean(payload.truncated);
      logControls.hidden = false;
      renderRunLogLines();
    } catch (error) {
      logState.className = "state error";
      logState.textContent = `Unable to load run-scoped logs: ${error.message}`;
    }
  }

  function focus() {
    const controls = document.getElementById("run-detail-log-controls");
    const search = document.getElementById("run-detail-log-search");
    const list = document.getElementById("run-detail-log-list");
    const target = controls && controls.hidden ? list : controls;

    if (target && typeof target.scrollIntoView === "function") {
      target.scrollIntoView({ behavior: "smooth", block: "start" });
    }
    if (search && typeof search.focus === "function") {
      search.focus();
    }
  }

  function renderRunLogLines() {
    const logState = document.getElementById("run-detail-log-state");
    const logList = document.getElementById("run-detail-log-list");
    const logEmpty = document.getElementById("run-detail-log-empty");

    if (!logState || !logList || !logEmpty) {
      return;
    }

    const allLines = Array.isArray(state.lines) ? state.lines : [];
    const list = getFilteredRunLogLines();

    logList.innerHTML = "";
    if (list.length === 0) {
      updateRunLogCopyVisibleButton(0);
      logState.className = "state";
      logState.hidden = false;
      logState.textContent = allLines.length === 0
        ? "No run-scoped log lines returned."
        : "No run-scoped log lines match the current filter.";
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
      const timestamp = formatLogTimestamp(line.loggedAt);
      const summary = formatStructuredSummary(line);

      const fullMessage = valueOrDash(line.message);
      const renderedMessage = state.compact ? truncateForCompact(fullMessage, 240) : fullMessage;
      row.innerHTML = `
        <div class="log-line-meta">
          <span class="log-chip level-${escapeHtml(level.toLowerCase())}">${escapeHtml(level || "RAW")}</span>
          <span class="log-chip">L${escapeHtml(lineNumber)}</span>
          <span class="log-chip">${escapeHtml(recordType)}</span>
          <span class="log-chip">${escapeHtml(event)}</span>
          <span class="log-time">${escapeHtml(timestamp)}</span>
          <button type="button" class="log-copy-btn" title="Copy exact raw log text for incident evidence and handoff">Copy raw</button>
        </div>
        <div class="log-line-summary">${escapeHtml(summary)}</div>
        <pre class="log-line-text ${state.compact ? "compact" : ""}" title="${escapeHtml(fullMessage)}">${escapeHtml(renderedMessage)}</pre>`;

      const copyButton = row.querySelector(".log-copy-btn");
      if (copyButton) {
        copyButton.addEventListener("click", () => {
          copyRunLogLineRaw(fullMessage, copyButton);
        });
      }

      logList.appendChild(row);
    });

    logEmpty.hidden = true;
    logList.hidden = false;
    updateRunLogCopyVisibleButton(list.length);
    logState.hidden = false;
    logState.className = "state";
    const baseMessage = state.truncated
      ? `Showing ${list.length} of ${allLines.length} loaded line(s) (source truncated server-side).`
      : `Showing ${list.length} of ${allLines.length} loaded line(s).`;
    logState.textContent = baseMessage;
  }

  function getFilteredRunLogLines() {
    const allLines = Array.isArray(state.lines) ? state.lines : [];
    const searchTerm = (state.searchText || "").trim().toLowerCase();
    return allLines.filter((line) => {
      if (state.structuredOnly && !line.structured) {
        return false;
      }
      if (!searchTerm) {
        return true;
      }
      const haystack = `${valueOrDash(line.message)} ${valueOrDash(line.recordType)} ${valueOrDash(line.event)} ${valueOrDash(line.level)}`.toLowerCase();
      return haystack.includes(searchTerm);
    });
  }

  function formatStructuredSummary(line) {
    if (!line || !line.structured) {
      return "Raw framework log";
    }

    const recordType = valueOrDash(line.recordType);
    const event = valueOrDash(line.event);
    const message = valueOrDash(line.message);

    if (recordType === "STEP_EVENT") {
      if (event === "step_started") {
        return `Step started (${extractField(message, "stepName") || "unknown-step"})`;
      }
      if (event === "step_finished") {
        const stepName = extractField(message, "stepName") || "unknown-step";
        const status = extractField(message, "status") || "UNKNOWN";
        const readCount = extractField(message, "readCount") || "-";
        const writeCount = extractField(message, "writeCount") || "-";
        const rejectedCount = extractField(message, "rejectedCount") || "-";
        return `Step finished (${stepName}) status=${status} read=${readCount} write=${writeCount} rejected=${rejectedCount}`;
      }
    }

    if (recordType === "RUN_SUMMARY") {
      const status = extractField(message, "status") || "UNKNOWN";
      const sourceCount = extractField(message, "sourceCount") || "-";
      const writtenCount = extractField(message, "writtenCount") || "-";
      const rejectedCount = extractField(message, "rejectedCount") || "-";
      return `Run summary status=${status} source=${sourceCount} written=${writtenCount} rejected=${rejectedCount}`;
    }

    if (recordType === "RUN_EVENT") {
      if (event === "job_started") {
        return "Run started";
      }
      if (event === "run_failed") {
        return "Run failed";
      }
      if (event === "run_finished") {
        return "Run finished";
      }
      return `Run event (${event})`;
    }

    if (recordType === "MAIN_FLOW_PLAN") {
      const subFlows = extractField(message, "plannedSubFlowCount") || "-";
      const steps = extractField(message, "plannedStepCount") || "-";
      return `Main-flow plan subFlows=${subFlows} steps=${steps}`;
    }

    if (recordType === "SUBFLOW_PLAN") {
      const subFlow = extractField(message, "subFlow") || "-";
      const stepNames = extractField(message, "stepNames") || "-";
      return `Subflow plan ${subFlow} steps=${stepNames}`;
    }

    if (recordType === "SUBFLOW_SUMMARY") {
      const subFlow = extractField(message, "subFlow") || "-";
      const status = extractField(message, "status") || "UNKNOWN";
      return `Subflow summary ${subFlow} status=${status}`;
    }

    return `${recordType}/${event}`;
  }

  function extractField(message, key) {
    if (!message || !key) {
      return "";
    }
    const match = String(message).match(new RegExp(`${key}=([^\\s]+)`));
    return match ? match[1] : "";
  }

  async function copyRunLogLineRaw(message, button) {
    const copied = await copyTextToClipboard(valueOrDash(message));
    setTransientCopyFeedback(button, copied, "Copied for handoff");
  }

  async function copyVisibleRunLogLines(button) {
    const visible = getFilteredRunLogLines();
    if (visible.length === 0) {
      setTransientCopyFeedback(button, false, "No visible lines");
      return;
    }

    const payload = visible.map((line) => {
      const level = (line.level || "RAW").toUpperCase();
      const timestamp = formatLogTimestamp(line.loggedAt);
      const recordType = valueOrDash(line.recordType);
      const event = valueOrDash(line.event);
      const lineNumber = valueOrDash(line.lineNumber);
      const message = valueOrDash(line.message);
      return `${timestamp} [${level}] L${lineNumber} ${recordType}/${event} ${message}`;
    }).join("\n");

    const copied = await copyTextToClipboard(payload);
    setTransientCopyFeedback(button, copied, `Copied ${visible.length}`);
  }

  async function copyTextToClipboard(text) {
    let copied = false;

    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        copied = true;
      }
    } catch (error) {
      copied = false;
    }

    if (copied) {
      return true;
    }

    const helper = document.createElement("textarea");
    helper.value = text;
    helper.setAttribute("readonly", "readonly");
    helper.style.position = "absolute";
    helper.style.left = "-9999px";
    document.body.appendChild(helper);
    helper.select();
    try {
      copied = document.execCommand("copy");
    } catch (error) {
      copied = false;
    }
    document.body.removeChild(helper);
    return copied;
  }

  function setTransientCopyFeedback(button, copied, successText) {
    if (!button) {
      return;
    }

    const originalText = button.dataset.label || button.textContent;
    button.textContent = copied ? successText : "Copy failed";
    button.disabled = true;
    window.setTimeout(() => {
      button.textContent = originalText;
      button.disabled = false;
    }, 1200);
  }

  function updateRunLogCopyVisibleButton(visibleCount) {
    const button = document.getElementById("run-detail-log-copy-visible");
    if (!button) {
      return;
    }
    button.disabled = visibleCount <= 0;
  }

  function formatLogTimestamp(value) {
    const text = valueOrDash(value);
    if (text === "-") {
      return text;
    }

    const match = String(text).match(/^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?([zZ]|[+\-]\d{2}:\d{2})?$/);
    if (!match) {
      return text;
    }

    const base = match[1];
    const fractional = (match[2] || "").substring(0, 3).padEnd(3, "0");
    const zone = match[3] || "";
    return `${base}.${fractional}${zone}`;
  }

  function truncateForCompact(value, maxLength) {
    const text = valueOrDash(value);
    if (text.length <= maxLength) {
      return text;
    }
    return `${text.substring(0, maxLength)}...`;
  }

  return {
    initializeControls,
    resetContext,
    load,
    focus,
  };
}

