export function valueOrDash(value) {
  return value === null || value === undefined || value === "" ? "-" : String(value);
}

export function formatDateTimeSeconds(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return String(value);
  }
  const year = String(parsed.getFullYear()).padStart(4, "0");
  const month = String(parsed.getMonth() + 1).padStart(2, "0");
  const day = String(parsed.getDate()).padStart(2, "0");
  const hours = String(parsed.getHours()).padStart(2, "0");
  const minutes = String(parsed.getMinutes()).padStart(2, "0");
  const seconds = String(parsed.getSeconds()).padStart(2, "0");
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

export function resolveBrowserTimeZoneLabel() {
  try {
    const label = String(Intl.DateTimeFormat().resolvedOptions().timeZone || "").trim();
    return label === "" ? "local time" : label;
  } catch {
    return "local time";
  }
}

export function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function formatJobDetailRecentRunLabel(run) {
  const runId = valueOrDash(run?.jobExecutionId);
  const status = valueOrDash(run?.status);
  const start = valueOrDash(run?.startTime);
  return `runId=${runId} | status=${status} | start=${start}`;
}

export function categorizeTriggerFailure(statusCode) {
  if (statusCode === 404 || statusCode === 409) {
    return "config";
  }
  if (statusCode >= 400 && statusCode < 500) {
    return "validation";
  }
  return "runtime";
}

