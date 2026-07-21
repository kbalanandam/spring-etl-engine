export function createAppScheduleDetailHelpers(options = {}) {
  const {
    valueOrDash,
    formatScheduleTriggerOriginToken,
    formatDateTimeSeconds,
  } = options;

  function setScheduleDetailTriggersExpanded(expanded) {
    const triggerToggleButton = document.getElementById("schedule-detail-triggers-toggle-btn");
    const triggerPanel = document.getElementById("schedule-detail-triggers-panel");
    if (!triggerToggleButton || !triggerPanel) {
      return;
    }
    const isExpanded = Boolean(expanded);
    triggerPanel.hidden = !isExpanded;
    triggerToggleButton.textContent = isExpanded ? "Hide recent triggers" : "Show recent triggers";
  }

  function buildScheduleTriggerEventLine(item, scheduleId, scheduleListQuery) {
    const line = document.createElement("li");
    line.className = "job-trigger-event-item";
    const requestedAt = typeof formatDateTimeSeconds === "function"
      ? formatDateTimeSeconds(item?.requestedAt)
      : valueOrDash(item?.requestedAt);
    const origin = formatScheduleTriggerOriginToken(item?.triggerOrigin);
    const decision = valueOrDash(item?.decisionStatus);
    const triggerEventId = valueOrDash(item?.triggerEventId);
    const launchedRunId = String(item?.launchedRunId || "").trim();

    const decisionToken = String(item?.decisionStatus || "").trim().toUpperCase();
    const decisionChip = document.createElement("span");
    decisionChip.className = "decision-chip";
    if (decisionToken === "DUPLICATE_SUPPRESSED") {
      decisionChip.classList.add("decision-chip-warning");
    } else if (decisionToken === "ACCEPTED") {
      decisionChip.classList.add("decision-chip-success");
    }
    decisionChip.textContent = decision;

    const metadata = document.createElement("span");
    metadata.textContent = `${requestedAt} | origin=${origin} | triggerEventId=${triggerEventId} | launchedRunId=`;

    line.appendChild(decisionChip);
    line.appendChild(document.createTextNode(" "));
    line.appendChild(metadata);

    if (launchedRunId !== "") {
      const runLink = document.createElement("a");
      const params = new URLSearchParams();
      params.set("from", "schedule");
      params.set("scheduleId", String(scheduleId || ""));
      if (String(scheduleListQuery || "").trim() !== "") {
        params.set("scheduleListQuery", String(scheduleListQuery || "").trim());
      }
      runLink.href = `#/runs/${encodeURIComponent(launchedRunId)}?${params.toString()}`;
      runLink.textContent = launchedRunId;
      line.appendChild(runLink);
    } else {
      line.appendChild(document.createTextNode("-"));
    }

    return line;
  }

  return {
    buildScheduleTriggerEventLine,
    setScheduleDetailTriggersExpanded,
  };
}

