export function pickNewestSchedule(schedules) {
  return [...schedules].sort((left, right) => {
    const leftUpdated = String(left?.updatedAt || "");
    const rightUpdated = String(right?.updatedAt || "");
    return rightUpdated.localeCompare(leftUpdated);
  })[0];
}

export function selectScheduleForJobDetail(schedules, preferredScheduleId) {
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

export function getScheduleControlState(schedule) {
  const enabled = Boolean(schedule?.enabled);
  const paused = enabled && Boolean(schedule?.paused);

  return {
    enabled,
    paused,
    statusLabel: !enabled ? "Disabled" : paused ? "Paused" : "Active",
    enableDisableAction: enabled ? "disable" : "enable",
    enableDisableLabel: enabled ? "Disable" : "Enable",
    pauseResumeAction: enabled ? (paused ? "resume" : "pause") : null,
    pauseResumeLabel: paused ? "Resume" : "Pause",
    detailPauseResumeLabel: paused ? "Resume schedule" : "Pause schedule",
    pauseResumeDisabled: !enabled,
  };
}

export function formatScheduleStatus(schedule) {
  return getScheduleControlState(schedule).statusLabel;
}

export function formatTriggerOriginToken(token) {
  const normalized = String(token || "").trim().toUpperCase();
  if (normalized === "SCHEDULE") {
    return "Schedule";
  }
  if (normalized === "EVENT") {
    return "Event";
  }
  return "Manual";
}

export function formatScheduleTriggerOriginToken(token) {
  const normalized = String(token || "").trim().toUpperCase();
  if (normalized === "EVENT") {
    return "Event";
  }
  if (normalized === "MANUAL") {
    return "Manual";
  }
  return "Schedule";
}

