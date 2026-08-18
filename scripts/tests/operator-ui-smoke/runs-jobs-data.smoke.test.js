import test from "node:test";
import assert from "node:assert/strict";

import {
  fetchJobsForRunsScope,
  mapJobsToRunsJobOptions,
} from "../../../src/main/resources/static/operator/runs-jobs-data.js";

test("mapJobsToRunsJobOptions keeps job key and display name shape", () => {
  const options = mapJobsToRunsJobOptions([
    { jobKey: "customer-load", displayName: "Customer Load", ignored: true },
    { jobKey: "orders-load" },
  ]);

  assert.deepEqual(options, [
    { jobKey: "customer-load", displayName: "Customer Load" },
    { jobKey: "orders-load", displayName: undefined },
  ]);
});

test("mapJobsToRunsJobOptions tolerates non-array input", () => {
  assert.deepEqual(mapJobsToRunsJobOptions(null), []);
});

test("fetchJobsForRunsScope returns API items array", async () => {
  const jobs = await fetchJobsForRunsScope({
    fetchFn: async (url, options) => {
      assert.equal(url, "/api/v1/jobs");
      assert.deepEqual(options, { headers: { Accept: "application/json" } });
      return {
        ok: true,
        async json() {
          return {
            items: [{ jobKey: "customer-load", displayName: "Customer Load" }],
          };
        },
      };
    },
  });

  assert.deepEqual(jobs, [{ jobKey: "customer-load", displayName: "Customer Load" }]);
});

test("fetchJobsForRunsScope normalizes non-array payload items", async () => {
  const jobs = await fetchJobsForRunsScope({
    fetchFn: async () => ({
      ok: true,
      async json() {
        return { items: null };
      },
    }),
  });

  assert.deepEqual(jobs, []);
});

test("fetchJobsForRunsScope surfaces jobs api failures", async () => {
  await assert.rejects(
    () => fetchJobsForRunsScope({
      fetchFn: async () => ({ ok: false, status: 503 }),
    }),
    /Jobs API returned 503/
  );
});

