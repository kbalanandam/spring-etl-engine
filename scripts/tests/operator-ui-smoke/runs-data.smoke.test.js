import test from "node:test";
import assert from "node:assert/strict";

import {
  RUNS_FILTER_CACHE_MAX_ENTRIES,
  RUNS_FILTER_CACHE_TTL_MS,
  buildRunsFilterCacheKey,
  fetchRunsForFilters,
  getCachedRunsByFilter,
  setCachedRunsByFilter,
} from "../../../src/main/resources/static/operator/runs-data.js";

function createCache() {
  return {
    byFilter: {},
    order: [],
  };
}

test("buildRunsFilterCacheKey creates stable composite key", () => {
  const key = buildRunsFilterCacheKey("customer-load", "explicit-job", "rerun-from-start", "", "2026-06-23", "UTC");
  assert.equal(key, "customer-load|explicit-job|rerun-from-start||2026-06-23|UTC");
});

test("getCachedRunsByFilter returns cached array entries and structured entries", () => {
  const cache = createCache();
  cache.byFilter.legacy = [{ jobExecutionId: 11 }];
  cache.byFilter.structured = { items: [{ jobExecutionId: 12 }], cachedAt: 100 };
  cache.order.push("structured");

  assert.deepEqual(getCachedRunsByFilter(cache, "legacy", 150), [{ jobExecutionId: 11 }]);
  assert.deepEqual(getCachedRunsByFilter(cache, "structured", 150), [{ jobExecutionId: 12 }]);
  assert.deepEqual(cache.order, ["structured"]);
});

test("getCachedRunsByFilter evicts expired cache entries", () => {
  const cache = createCache();
  cache.byFilter.stale = { items: [{ jobExecutionId: 19 }], cachedAt: 100 };
  cache.order.push("stale");

  const value = getCachedRunsByFilter(cache, "stale", 100 + RUNS_FILTER_CACHE_TTL_MS + 1);
  assert.equal(value, null);
  assert.equal(cache.byFilter.stale, undefined);
  assert.deepEqual(cache.order, []);
});

test("setCachedRunsByFilter keeps only max LRU entries", () => {
  const cache = createCache();

  for (let index = 0; index <= RUNS_FILTER_CACHE_MAX_ENTRIES; index += 1) {
    setCachedRunsByFilter(cache, `k${index}`, [{ jobExecutionId: index }], index);
  }

  assert.equal(cache.order.length, RUNS_FILTER_CACHE_MAX_ENTRIES);
  assert.equal(cache.byFilter.k0, undefined);
  assert.deepEqual(cache.byFilter.k30.items, [{ jobExecutionId: 30 }]);
});

test("fetchRunsForFilters builds API query and caches payload items", async () => {
  const cache = createCache();
  let fetchCount = 0;
  let requestedUrl = "";

  const fetchFn = async (url) => {
    fetchCount += 1;
    requestedUrl = String(url);
    return {
      ok: true,
      async json() {
        return { items: [{ jobExecutionId: 41 }] };
      },
    };
  };

  const first = await fetchRunsForFilters({
    selectedJobKey: "customer-load",
    runMode: "explicit-job",
    recoveryPolicy: "rerun-from-start",
    startDate: "2026-06-23",
    timezone: "UTC",
    cache,
    fetchFn,
    nowMs: 200,
  });

  const second = await fetchRunsForFilters({
    selectedJobKey: "customer-load",
    runMode: "explicit-job",
    recoveryPolicy: "rerun-from-start",
    startDate: "2026-06-23",
    timezone: "UTC",
    cache,
    fetchFn,
    nowMs: 201,
  });

  assert.equal(fetchCount, 1);
  assert.deepEqual(first, [{ jobExecutionId: 41 }]);
  assert.deepEqual(second, [{ jobExecutionId: 41 }]);
  assert.match(requestedUrl, /^\/api\/v1\/runs\?/);
  assert.match(requestedUrl, /limit=200/);
  assert.match(requestedUrl, /job=customer-load/);
  assert.match(requestedUrl, /runMode=explicit-job/);
  assert.match(requestedUrl, /recoveryPolicy=rerun-from-start/);
  assert.match(requestedUrl, /startDate=2026-06-23/);
  assert.match(requestedUrl, /timezone=UTC/);
});

test("fetchRunsForFilters normalizes non-array payload items", async () => {
  const cache = createCache();

  const items = await fetchRunsForFilters({
    cache,
    fetchFn: async () => ({
      ok: true,
      async json() {
        return { items: null };
      },
    }),
    nowMs: 300,
  });

  assert.deepEqual(items, []);
}
);

test("fetchRunsForFilters appends refresh flag when hard refresh is requested", async () => {
  let requestedUrl = "";

  await fetchRunsForFilters({
    cache: createCache(),
    forceRefresh: true,
    bypassCache: true,
    fetchFn: async (url) => {
      requestedUrl = String(url);
      return {
        ok: true,
        async json() {
          return { items: [{ jobExecutionId: 77 }] };
        },
      };
    },
  });

  assert.match(requestedUrl, /refresh=true/);
});

