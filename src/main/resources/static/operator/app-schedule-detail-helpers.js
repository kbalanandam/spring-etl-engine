export function createAppScheduleDetailHelpers(options = {}) {
  const {
    valueOrDash,
    formatScheduleTriggerOriginToken,
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
    const requestedAt = valueOrDash(item?.requestedAt);
    const origin = formatScheduleTriggerOriginToken(item?.triggerOrigin);
    const decision = valueOrDash(item?.decisionStatus);
    const triggerEventId = valueOrDash(item?.triggerEventId);
    const launchedRunId = String(item?.launchedRunId || "").trim();
    const hasLaunchedRunId = launchedRunId !== "";

    const decisionToken = String(item?.decisionStatus || "").trim().toUpperCase();
    const decisionChip = document.createElement("span");
    decisionChip.className = "decision-chip";
    if (decisionToken === "DUPLICATE_SUPPRESSED" || decisionToken === "LAUNCH_SKIPPED") {
      decisionChip.classList.add("decision-chip-warning");
    } else if (decisionToken === "ACCEPTED" && !hasLaunchedRunId) {
      decisionChip.classList.add("decision-chip-error");
    } else if (decisionToken === "ACCEPTED") {
      decisionChip.classList.add("decision-chip-success");
    }
    decisionChip.textContent = decision;

    const metadata = document.createElement("span");
    metadata.textContent = hasLaunchedRunId
      ? `${requestedAt} | origin=${origin} | launch=CONFIRMED | triggerEventId=${triggerEventId} | launchedRunId=`
      : `${requestedAt} | origin=${origin} | launch=NOT_CONFIRMED | triggerEventId=${triggerEventId}`;

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
    }

    return line;
  }

  return {
    buildScheduleTriggerEventLine,
    setScheduleDetailTriggersExpanded,
  };
}

