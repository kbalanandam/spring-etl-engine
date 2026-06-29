export const RUNS_FILTER_CACHE_MAX_ENTRIES = 30;
export const RUNS_FILTER_CACHE_TTL_MS = 5 * 60 * 1000;
export const RUNS_EMPTY_FILTER_CACHE_TTL_MS = 3 * 1000;

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

export function buildRunsFilterCacheKey(selectedJobKey, runMode, recoveryPolicy, triggerSource, startDate, timezone) {
  return `${selectedJobKey || ""}|${runMode || ""}|${recoveryPolicy || ""}|${triggerSource || ""}|${startDate || ""}|${timezone || ""}`;
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

  const effectiveTtlMs = entry.items.length === 0 ? Math.min(ttlMs, RUNS_EMPTY_FILTER_CACHE_TTL_MS) : ttlMs;
  if (nowMs - entry.cachedAt > effectiveTtlMs) {
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

export function clearCachedRunsByFilter(cacheSource, cacheKey) {
  const cache = ensureCache(cacheSource);
  delete cache.byFilter[cacheKey];
  cache.order = cache.order.filter((key) => key !== cacheKey);
}

export async function fetchRunsForFilters(options) {
  const {
    selectedJobKey,
    runMode,
    recoveryPolicy,
    triggerSource,
    startDate,
    timezone,
    cache,
    fetchFn = fetch,
    nowMs = Date.now(),
    cacheTtlMs = RUNS_FILTER_CACHE_TTL_MS,
    cacheMaxEntries = RUNS_FILTER_CACHE_MAX_ENTRIES,
    bypassCache = false,
  } = options || {};

  const cacheKey = buildRunsFilterCacheKey(selectedJobKey, runMode, recoveryPolicy, triggerSource, startDate, timezone);
  const cached = bypassCache ? null : getCachedRunsByFilter(cache, cacheKey, nowMs, cacheTtlMs);
  if (cached !== null) {
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
  if (triggerSource) {
    params.set("triggerSource", triggerSource);
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
  if (items.length === 0) {
    clearCachedRunsByFilter(cache, cacheKey);
    return items;
  }

  setCachedRunsByFilter(cache, cacheKey, items, nowMs, cacheMaxEntries);
  return items;
}

export async function fetchTriggerSourceOptions(options = {}) {
  const { fetchFn = fetch } = options;
  const response = await fetchFn("/api/v1/runs/trigger-sources", { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`Trigger-source API returned ${response.status}`);
  }
  const payload = await response.json();
  return Array.isArray(payload.items) ? payload.items : [];
}

