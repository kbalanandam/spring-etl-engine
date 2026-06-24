export function mapJobsToRunsJobOptions(items) {
  const jobs = Array.isArray(items) ? items : [];
  return jobs.map((job) => ({
    jobKey: job?.jobKey,
    displayName: job?.displayName,
  }));
}

export async function fetchJobsForRunsScope(options = {}) {
  const { fetchFn = fetch } = options;
  const response = await fetchFn("/api/v1/jobs", { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`Jobs API returned ${response.status}`);
  }

  const payload = await response.json();
  return Array.isArray(payload.items) ? payload.items : [];
}

