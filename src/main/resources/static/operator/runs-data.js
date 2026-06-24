export const RUNS_FILTER_CACHE_MAX_ENTRIES = 30;
export const RUNS_FILTER_CACHE_TTL_MS = 5 * 60 * 1000;

function ensureCache(cache) {
  if (!cache || typeof cache !== "object") {
    return { byFilter: {}, order: [] };
  }
  if (!cache.byFilter || typeof cache.byFilter !== "object") {
    cache.byFilter = {};
  }
  if (!Array.isArray(cache.order)) {
    cache.order = [];
  }
  return cache;
}

export function buildRunsFilterCacheKey(selectedJobKey, runMode, recoveryPolicy, startDate, timezone) {
  return `${selectedJobKey || ""}|${runMode || ""}|${recoveryPolicy || ""}|${startDate || ""}|${timezone || ""}`;
}

export function getCachedRunsByFilter(cacheSource, cacheKey, nowMs = Date.now(), ttlMs = RUNS_FILTER_CACHE_TTL_MS) {
  const cache = ensureCache(cacheSource);
  const entry = cache.byFilter[cacheKey];
  if (!entry) {
    return null;
  }

  // Keep compatibility with legacy array-only cache entries.
  if (Array.isArray(entry)) {
    return entry;
  }

  if (!Array.isArray(entry.items) || !Number.isFinite(entry.cachedAt)) {
    delete cache.byFilter[cacheKey];
    cache.order = cache.order.filter((key) => key !== cacheKey);
    return null;
  }

  if (nowMs - entry.cachedAt > ttlMs) {
    delete cache.byFilter[cacheKey];
    cache.order = cache.order.filter((key) => key !== cacheKey);
    return null;
  }

  cache.order = cache.order.filter((key) => key !== cacheKey);
  cache.order.push(cacheKey);
  return entry.items;
}

export function setCachedRunsByFilter(cacheSource, cacheKey, items, nowMs = Date.now(), maxEntries = RUNS_FILTER_CACHE_MAX_ENTRIES) {
  const cache = ensureCache(cacheSource);
  cache.byFilter[cacheKey] = {
    items,
    cachedAt: nowMs,
  };

  cache.order = cache.order.filter((key) => key !== cacheKey);
  cache.order.push(cacheKey);

  while (cache.order.length > maxEntries) {
    const staleKey = cache.order.shift();
    if (staleKey) {
      delete cache.byFilter[staleKey];
    }
  }
}

export async function fetchRunsForFilters(options) {
  const {
    selectedJobKey,
    runMode,
    recoveryPolicy,
    startDate,
    timezone,
    cache,
    fetchFn = fetch,
    nowMs = Date.now(),
    cacheTtlMs = RUNS_FILTER_CACHE_TTL_MS,
    cacheMaxEntries = RUNS_FILTER_CACHE_MAX_ENTRIES,
  } = options || {};

  const cacheKey = buildRunsFilterCacheKey(selectedJobKey, runMode, recoveryPolicy, startDate, timezone);
  const cached = getCachedRunsByFilter(cache, cacheKey, nowMs, cacheTtlMs);
  if (cached) {
    return cached;
  }

  const params = new URLSearchParams();
  params.set("limit", "200");
  if (selectedJobKey) {
    params.set("job", selectedJobKey);
  }
  if (runMode) {
    params.set("runMode", runMode);
  }
  if (recoveryPolicy) {
    params.set("recoveryPolicy", recoveryPolicy);
  }
  if (startDate) {
    params.set("startDate", startDate);
  }
  if (timezone) {
    params.set("timezone", timezone);
  }

  const response = await fetchFn(`/api/v1/runs?${params.toString()}`, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`Runs API returned ${response.status}`);
  }

  const payload = await response.json();
  const items = Array.isArray(payload.items) ? payload.items : [];
  setCachedRunsByFilter(cache, cacheKey, items, nowMs, cacheMaxEntries);
  return items;
}

