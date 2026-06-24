export function pruneObjectKeys(source, validKeys) {
  if (!source || typeof source !== "object") {
    return;
  }

  Object.keys(source).forEach((key) => {
    if (!validKeys.has(key)) {
      delete source[key];
    }
  });
}

export function reconcileJobsScopedCaches(options = {}) {
  const { jobsState, inFlightStepNamesByJobKey, validJobKeys } = options;
  const validKeys = validJobKeys instanceof Set ? validJobKeys : new Set();

  if (!jobsState || typeof jobsState !== "object") {
    return;
  }

  if (!validKeys.has(jobsState.expandedJobKey)) {
    jobsState.expandedJobKey = "";
  }

  pruneObjectKeys(jobsState.jobStepPreviewByJobKey, validKeys);
  pruneObjectKeys(jobsState.stepNamesByJobKey, validKeys);
  pruneObjectKeys(inFlightStepNamesByJobKey, validKeys);
}

export function applyJobsItems(options = {}) {
  const {
    items,
    jobsState,
    inFlightStepNamesByJobKey,
  } = options;

  if (!jobsState || typeof jobsState !== "object") {
    return [];
  }

  const jobs = Array.isArray(items) ? items : [];
  const validKeys = new Set(
    jobs
      .map((job) => String(job?.jobKey || "").trim())
      .filter((jobKey) => jobKey !== "")
  );

  jobsState.items = jobs;
  reconcileJobsScopedCaches({
    jobsState,
    inFlightStepNamesByJobKey,
    validJobKeys: validKeys,
  });
  return jobs;
}

