export function normalizeSupportedFilter(value, supportedValues) {
  const normalized = String(value || "").trim();
  if (normalized === "") {
    return "";
  }
  return supportedValues.has(normalized) ? normalized : "";
}

export function normalizeIsoDate(value) {
  const normalized = String(value || "").trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(normalized)) {
    return "";
  }
  const [yearText, monthText, dayText] = normalized.split("-");
  const year = Number.parseInt(yearText, 10);
  const month = Number.parseInt(monthText, 10);
  const day = Number.parseInt(dayText, 10);
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
    return "";
  }
  const date = new Date(Date.UTC(year, month - 1, day));
  const matches = date.getUTCFullYear() === year
    && date.getUTCMonth() + 1 === month
    && date.getUTCDate() === day;
  return matches ? normalized : "";
}

export function formatDateForInput(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function buildRunsRouteHash(source) {
  const params = new URLSearchParams();

  if (String(source?.filterText || "").trim() !== "") {
    params.set("f", String(source.filterText).trim());
  }
  if (String(source?.selectedJobKey || "").trim() !== "") {
    params.set("job", String(source.selectedJobKey).trim());
  }
  if (String(source?.runModeFilter || "").trim() !== "") {
    params.set("runMode", String(source.runModeFilter).trim());
  }
  if (String(source?.recoveryPolicyFilter || "").trim() !== "") {
    params.set("recoveryPolicy", String(source.recoveryPolicyFilter).trim());
  }
  if (String(source?.triggerSourceFilter || "").trim() !== "") {
    params.set("triggerSource", String(source.triggerSourceFilter).trim());
  }
  if (String(source?.startDate || "").trim() !== "") {
    params.set("startDate", String(source.startDate).trim());
  }
  if (String(source?.timezone || "").trim() !== "") {
    params.set("timezone", String(source.timezone).trim());
  }
  params.set("sort", String(source?.sortKey || "startTime"));
  params.set("dir", String(source?.sortDirection || "desc"));
  return `#/runs?${params.toString()}`;
}

