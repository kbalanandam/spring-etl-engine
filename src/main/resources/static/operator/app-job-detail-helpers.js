export function createAppJobDetailHelpers(options = {}) {
  const {
    valueOrDash,
    formatTriggerOriginToken,
  } = options;

  function buildJobTriggerEventLine(item, jobKey, query) {
    const line = document.createElement("li");
    const requestedAt = valueOrDash(item?.requestedAt);
    const origin = formatTriggerOriginToken(item?.triggerOrigin);
    const decision = valueOrDash(item?.decisionStatus);
    const reason = valueOrDash(item?.reason);
    const requestedBy = valueOrDash(item?.requestedBy);
    const triggerEventId = valueOrDash(item?.triggerEventId);
    const launchedRunId = String(item?.launchedRunId || "").trim();
    const message = String(item?.message || "").trim();

    line.appendChild(document.createTextNode(`${requestedAt} | origin=${origin} | decision=${decision} | reason=${reason} | requestedBy=${requestedBy} | triggerEventId=${triggerEventId} | launchedRunId=`));

    if (launchedRunId !== "") {
      const runLink = document.createElement("a");
      const params = new URLSearchParams();
      params.set("from", "job");
      params.set("job", String(jobKey || "").trim());
      const source = String(query?.from || "").trim().toLowerCase();
      const sourceScheduleId = String(query?.scheduleId || "").trim();
      const sourceScheduleListQuery = String(query?.scheduleListQuery || "").trim();
      if (source === "schedule" && sourceScheduleId !== "") {
        params.set("scheduleId", sourceScheduleId);
        if (sourceScheduleListQuery !== "") {
          params.set("scheduleListQuery", sourceScheduleListQuery);
        }
      }
      runLink.href = `#/runs/${encodeURIComponent(launchedRunId)}?${params.toString()}`;
      runLink.textContent = launchedRunId;
      line.appendChild(runLink);
    } else {
      line.appendChild(document.createTextNode("-"));
    }

    if (message !== "") {
      line.title = message;
    }

    return line;
  }

  function renderJobTriggerEvents(recentTriggerEvents, jobKey, query) {
    const triggerEventsState = document.getElementById("job-detail-trigger-events-state");
    const triggerEventsList = document.getElementById("job-detail-trigger-events-list");
    if (!triggerEventsState || !triggerEventsList) {
      return;
    }

    const events = Array.isArray(recentTriggerEvents) ? recentTriggerEvents : [];
    triggerEventsList.innerHTML = "";

    if (events.length === 0) {
      triggerEventsState.className = "state";
      triggerEventsState.textContent = "No recent trigger events found for this job.";
      triggerEventsList.hidden = true;
      return;
    }

    events.forEach((item) => {
      triggerEventsList.appendChild(buildJobTriggerEventLine(item, jobKey, query));
    });

    triggerEventsState.className = "state";
    triggerEventsState.textContent = `Showing ${events.length} recent trigger event(s).`;
    triggerEventsList.hidden = false;
  }

  return {
    buildJobTriggerEventLine,
    renderJobTriggerEvents,
  };
}
