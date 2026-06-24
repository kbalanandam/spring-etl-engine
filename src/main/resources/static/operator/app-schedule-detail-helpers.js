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
    const requestedAt = valueOrDash(item?.requestedAt);
    const origin = formatScheduleTriggerOriginToken(item?.triggerOrigin);
    const decision = valueOrDash(item?.decisionStatus);
    const triggerEventId = valueOrDash(item?.triggerEventId);
    const launchedRunId = String(item?.launchedRunId || "").trim();

    line.appendChild(document.createTextNode(`${requestedAt} | origin=${origin} | decision=${decision} | triggerEventId=${triggerEventId} | launchedRunId=`));

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

