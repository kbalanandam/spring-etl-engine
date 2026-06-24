export function valueOrDash(value) {
  return value === null || value === undefined || value === "" ? "-" : String(value);
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

