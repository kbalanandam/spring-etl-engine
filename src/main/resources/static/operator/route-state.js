const DEFAULT_PAGE_SIZES = [8, 10, 15, 20];

const DEFAULT_SORT_KEYS = {
  jobs: ["jobKey", "displayName", "readinessStatus"],
  schedules: ["scheduleKey", "selectedJobKey", "status", "nextDueAt"],
  runs: ["startTime", "jobExecutionId", "scenario", "status", "triggerOrigin", "runMode", "recoveryPolicy"],
};

export function parseHashRoute(hashValue = (typeof location !== "undefined" ? location.hash : "")) {
  const raw = String(hashValue || "").replace(/^#\/?/, "");
  const separatorIndex = raw.indexOf("?");

  if (separatorIndex < 0) {
    return { path: raw, query: {} };
  }

  const path = raw.substring(0, separatorIndex);
  const queryString = raw.substring(separatorIndex + 1);
  const query = {};
  const params = new URLSearchParams(queryString);
  params.forEach((value, key) => {
    query[key] = value;
  });
  return { path, query };
}

export function normalizeSortKey(routeKey, value, fallback, sortKeys = DEFAULT_SORT_KEYS) {
  if (!value) {
    return fallback;
  }
  const supported = Array.isArray(sortKeys?.[routeKey]) ? sortKeys[routeKey] : [];
  return supported.includes(value) ? value : fallback;
}

export function normalizeDirection(value, fallback) {
  if (value === "asc" || value === "desc") {
    return value;
  }
  return fallback;
}

export function normalizePositiveInteger(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  if (Number.isFinite(parsed) && parsed > 0) {
    return parsed;
  }
  return fallback;
}

export function normalizePageSize(value, fallback, allowedPageSizes = DEFAULT_PAGE_SIZES) {
  const parsed = normalizePositiveInteger(value, fallback);
  return allowedPageSizes.includes(parsed) ? parsed : fallback;
}

export function defaultJobsPageSize(viewportHeight = (typeof window !== "undefined" && Number.isFinite(window.innerHeight)
  ? window.innerHeight
  : 900)) {
  if (viewportHeight >= 1200) {
    return 15;
  }
  if (viewportHeight >= 900) {
    return 10;
  }
  return 8;
}

export function defaultSchedulesPageSize(viewportHeight) {
  return defaultJobsPageSize(viewportHeight);
}

export function getQuerySuffix(query) {
  const params = new URLSearchParams();
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined && String(value) !== "") {
      params.set(key, String(value));
    }
  });
  const serialized = params.toString();
  return serialized ? `?${serialized}` : "";
}

export function buildJobsRouteQuery(source, options = {}) {
  const defaultPageSize = Number.isFinite(options.defaultPageSize)
    ? options.defaultPageSize
    : defaultJobsPageSize();
  const params = new URLSearchParams();
  if (String(source?.filterText || "").trim() !== "") {
    params.set("f", String(source.filterText).trim());
  }
  if ((source?.page || 1) > 1) {
    params.set("page", String(source.page));
  }
  if ((source?.pageSize || defaultPageSize) !== defaultPageSize) {
    params.set("pageSize", String(source.pageSize));
  }
  params.set("sort", String(source?.sortKey || "jobKey"));
  params.set("dir", String(source?.sortDirection || "asc"));
  return params.toString();
}

export function buildSchedulesRouteQuery(source, options = {}) {
  const includeSelectedScheduleId = options.includeSelectedScheduleId !== false;
  const sortKeys = options.sortKeys || DEFAULT_SORT_KEYS;
  const allowedPageSizes = options.allowedPageSizes || DEFAULT_PAGE_SIZES;
  const defaultPageSize = Number.isFinite(options.defaultPageSize)
    ? options.defaultPageSize
    : defaultSchedulesPageSize();
  const fallbackSortKey = String(options.fallbackSortKey || "scheduleKey");
  const fallbackSortDirection = String(options.fallbackSortDirection || "asc");

  const params = new URLSearchParams();
  const filterText = String(source?.filterText || "").trim();
  const sortKey = normalizeSortKey("schedules", source?.sortKey, fallbackSortKey, sortKeys);
  const sortDirection = normalizeDirection(source?.sortDirection, fallbackSortDirection);
  const page = normalizePositiveInteger(source?.page, 1);
  const pageSize = normalizePageSize(source?.pageSize, defaultPageSize, allowedPageSizes);
  const selectedScheduleId = String(source?.selectedScheduleId || "").trim();

  if (filterText !== "") {
    params.set("f", filterText);
  }
  if (page > 1) {
    params.set("page", String(page));
  }
  if (pageSize !== defaultPageSize) {
    params.set("pageSize", String(pageSize));
  }
  params.set("sort", sortKey);
  params.set("dir", sortDirection);
  if (includeSelectedScheduleId && selectedScheduleId !== "") {
    params.set("scheduleId", selectedScheduleId);
  }
  return params.toString();
}

export function buildSchedulesListQueryFromRouteQuery(query, options = {}) {
  const sortKeys = options.sortKeys || DEFAULT_SORT_KEYS;
  const allowedPageSizes = options.allowedPageSizes || DEFAULT_PAGE_SIZES;
  const defaultPageSize = Number.isFinite(options.defaultPageSize)
    ? options.defaultPageSize
    : defaultSchedulesPageSize();
  const fallbackSortKey = String(options.fallbackSortKey || "scheduleKey");
  const fallbackSortDirection = String(options.fallbackSortDirection || "asc");

  const params = new URLSearchParams();
  const filterText = String(query?.f || "").trim();
  const page = normalizePositiveInteger(query?.page, 1);
  const pageSize = normalizePageSize(query?.pageSize, defaultPageSize, allowedPageSizes);
  const sortKey = normalizeSortKey("schedules", query?.sort, fallbackSortKey, sortKeys);
  const sortDirection = normalizeDirection(query?.dir, fallbackSortDirection);

  if (filterText !== "") {
    params.set("f", filterText);
  }
  if (page > 1) {
    params.set("page", String(page));
  }
  if (pageSize !== defaultPageSize) {
    params.set("pageSize", String(pageSize));
  }
  params.set("sort", sortKey);
  params.set("dir", sortDirection);
  return params.toString();
}

export function buildSchedulesListHash(scheduleId, scheduleListQuery, options = {}) {
  const params = new URLSearchParams(String(scheduleListQuery || ""));
  const normalizedScheduleId = String(scheduleId || "").trim();
  const fallbackSortKey = String(options.fallbackSortKey || "scheduleKey");
  const fallbackSortDirection = String(options.fallbackSortDirection || "asc");

  if (normalizedScheduleId !== "") {
    params.set("scheduleId", normalizedScheduleId);
  }
  if (!params.has("sort")) {
    params.set("sort", fallbackSortKey);
  }
  if (!params.has("dir")) {
    params.set("dir", fallbackSortDirection);
  }
  const query = params.toString();
  return query ? `#/schedules?${query}` : "#/schedules";
}

export function buildSchedulesEditHash(scheduleId, scheduleListQuery, options = {}) {
  const params = new URLSearchParams(String(scheduleListQuery || ""));
  const normalizedScheduleId = String(scheduleId || "").trim();
  const fallbackSortKey = String(options.fallbackSortKey || "scheduleKey");
  const fallbackSortDirection = String(options.fallbackSortDirection || "asc");

  if (normalizedScheduleId !== "") {
    params.set("scheduleId", normalizedScheduleId);
    params.set("editScheduleId", normalizedScheduleId);
    params.set("editReturn", "detail");
  }
  if (!params.has("sort")) {
    params.set("sort", fallbackSortKey);
  }
  if (!params.has("dir")) {
    params.set("dir", fallbackSortDirection);
  }
  const query = params.toString();
  return query ? `#/schedules?${query}` : "#/schedules";
}

export function buildScheduleDetailHash(scheduleId, scheduleListQuery) {
  const normalizedScheduleId = String(scheduleId || "").trim();
  if (normalizedScheduleId === "") {
    return "#/schedules";
  }
  const query = String(scheduleListQuery || "").trim();
  return query === ""
    ? `#/schedules/${encodeURIComponent(normalizedScheduleId)}`
    : `#/schedules/${encodeURIComponent(normalizedScheduleId)}?${query}`;
}

