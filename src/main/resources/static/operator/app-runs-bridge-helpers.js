export function createAppRunsBridgeHelpers(options = {}) {
  const {
    viewState,
    inFlightStepNamesByJobKey,
    applyJobsItemsValue,
    fetchJobsForRunsScopeValue,
    fetchRunsForFiltersValue,
    normalizeSupportedFilterValue,
    normalizeIsoDateValue,
    formatDateForInputValue,
  } = options;

  function normalizeSupportedFilter(value, supportedValues) {
    return normalizeSupportedFilterValue(value, supportedValues);
  }

  function normalizeIsoDate(value) {
    return normalizeIsoDateValue(value);
  }

  function formatDateForInput(date) {
    return formatDateForInputValue(date);
  }

  function applyJobsItems(items) {
    return applyJobsItemsValue({
      items,
      jobsState: viewState.jobs,
      inFlightStepNamesByJobKey,
    });
  }

  async function fetchJobsForRunsScope() {
    const jobs = await fetchJobsForRunsScopeValue();
    applyJobsItems(jobs);
    viewState.jobs.loaded = true;
    return jobs;
  }

  async function fetchRunsForFilters(selectedJobKey, runMode, recoveryPolicy, triggerSource, startDate, timezone, requestOptions = {}) {
    return fetchRunsForFiltersValue({
      selectedJobKey,
      runMode,
      recoveryPolicy,
      triggerSource,
      startDate,
      timezone,
      cache: viewState.runs.cache,
      bypassCache: Boolean(requestOptions?.bypassCache),
      forceRefresh: Boolean(requestOptions?.forceRefresh),
    });
  }

  return {
    applyJobsItems,
    fetchJobsForRunsScope,
    fetchRunsForFilters,
    formatDateForInput,
    normalizeIsoDate,
    normalizeSupportedFilter,
  };
}

