import {
  buildJobsRouteQuery as buildJobsRouteQueryValue,
  buildScheduleDetailHash as buildScheduleDetailHashValue,
  buildSchedulesEditHash as buildSchedulesEditHashValue,
  buildSchedulesListHash as buildSchedulesListHashValue,
  buildSchedulesListQueryFromRouteQuery as buildSchedulesListQueryFromRouteQueryValue,
  buildSchedulesRouteQuery as buildSchedulesRouteQueryValue,
  defaultJobsPageSize as defaultJobsPageSizeValue,
  defaultSchedulesPageSize as defaultSchedulesPageSizeValue,
  getQuerySuffix as getQuerySuffixValue,
  normalizeDirection as normalizeDirectionValue,
  normalizePageSize as normalizePageSizeValue,
  normalizePositiveInteger as normalizePositiveIntegerValue,
  normalizeSortKey as normalizeSortKeyValue,
  parseHashRoute as parseHashRouteValue,
} from "./route-state.js";
import {
  buildRunsRouteHash as buildRunsRouteHashValue,
} from "./runs-route-state.js";

export function createAppRouteHelpers(options = {}) {
  const {
    viewState,
    sortKeys,
    jobsPageSizeOptions,
  } = options;

  function defaultJobsPageSize() {
    return defaultJobsPageSizeValue();
  }

  function defaultSchedulesPageSize() {
    return defaultSchedulesPageSizeValue();
  }

  function buildJobsRouteQuery(source) {
    return buildJobsRouteQueryValue(source, { defaultPageSize: defaultJobsPageSize() });
  }

  function buildSchedulesRouteQuery(source, routeOptions = {}) {
    return buildSchedulesRouteQueryValue(source, {
      includeSelectedScheduleId: routeOptions.includeSelectedScheduleId,
      defaultPageSize: defaultSchedulesPageSize(),
      fallbackSortKey: "scheduleKey",
      fallbackSortDirection: "asc",
    });
  }

  function buildSchedulesListQueryFromRouteQuery(query) {
    return buildSchedulesListQueryFromRouteQueryValue(query, {
      defaultPageSize: defaultSchedulesPageSize(),
      fallbackSortKey: viewState.schedules.sortKey || "scheduleKey",
      fallbackSortDirection: viewState.schedules.sortDirection || "asc",
    });
  }

  function buildSchedulesListHash(scheduleId, scheduleListQuery) {
    return buildSchedulesListHashValue(scheduleId, scheduleListQuery, {
      fallbackSortKey: viewState.schedules.sortKey || "scheduleKey",
      fallbackSortDirection: viewState.schedules.sortDirection || "asc",
    });
  }

  function buildSchedulesEditHash(scheduleId, scheduleListQuery) {
    return buildSchedulesEditHashValue(scheduleId, scheduleListQuery, {
      fallbackSortKey: viewState.schedules.sortKey || "scheduleKey",
      fallbackSortDirection: viewState.schedules.sortDirection || "asc",
    });
  }

  function buildScheduleDetailHash(scheduleId, scheduleListQuery) {
    return buildScheduleDetailHashValue(scheduleId, scheduleListQuery);
  }

  function getJobsRouteHash() {
    const query = buildJobsRouteQuery(viewState.jobs);
    return query ? `#/jobs?${query}` : "#/jobs";
  }

  function getJobsRouteQuerySuffix() {
    const query = buildJobsRouteQuery(viewState.jobs);
    return query ? `?${query}` : "";
  }

  function getSchedulesRouteHash() {
    const query = buildSchedulesRouteQuery(viewState.schedules);
    return query ? `#/schedules?${query}` : "#/schedules";
  }

  function getSchedulesRouteQuerySuffix() {
    const query = buildSchedulesRouteQuery(viewState.schedules, { includeSelectedScheduleId: false });
    return query ? `?${query}` : "";
  }

  function getRunsRouteHash() {
    return buildRunsRouteHashValue(viewState.runs);
  }

  function syncListRouteHash(routeKey) {
    const hash = routeKey === "jobs"
      ? getJobsRouteHash()
      : routeKey === "schedules"
        ? getSchedulesRouteHash()
        : getRunsRouteHash();
    if (location.hash !== hash) {
      location.hash = hash;
    }
  }

  function parseHashRoute() {
    return parseHashRouteValue();
  }

  function normalizeSortKey(routeKey, value, fallback) {
    return normalizeSortKeyValue(routeKey, value, fallback, sortKeys);
  }

  function normalizeDirection(value, fallback) {
    return normalizeDirectionValue(value, fallback);
  }

  function normalizePositiveInteger(value, fallback) {
    return normalizePositiveIntegerValue(value, fallback);
  }

  function normalizePageSize(value, fallback) {
    return normalizePageSizeValue(value, fallback, jobsPageSizeOptions);
  }

  function getQuerySuffix(query) {
    return getQuerySuffixValue(query);
  }

  return {
    buildJobsRouteQuery,
    buildScheduleDetailHash,
    buildSchedulesEditHash,
    buildSchedulesListHash,
    buildSchedulesListQueryFromRouteQuery,
    buildSchedulesRouteQuery,
    defaultJobsPageSize,
    defaultSchedulesPageSize,
    getJobsRouteHash,
    getJobsRouteQuerySuffix,
    getQuerySuffix,
    getRunsRouteHash,
    getSchedulesRouteHash,
    getSchedulesRouteQuerySuffix,
    normalizeDirection,
    normalizePageSize,
    normalizePositiveInteger,
    normalizeSortKey,
    parseHashRoute,
    syncListRouteHash,
  };
}

