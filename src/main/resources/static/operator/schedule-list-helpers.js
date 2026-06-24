export function filterSchedulesItems(items, filterText, formatScheduleStatus) {
  const list = Array.isArray(items) ? items : [];
  const normalizedFilter = String(filterText || "").trim().toLowerCase();
  if (normalizedFilter === "") {
    return list;
  }
  return list.filter((schedule) => {
    const key = String(schedule?.scheduleKey || "").toLowerCase();
    const job = String(schedule?.selectedJobKey || "").toLowerCase();
    const status = String(formatScheduleStatus(schedule) || "").toLowerCase();
    return key.includes(normalizedFilter) || job.includes(normalizedFilter) || status.includes(normalizedFilter);
  });
}

export function sortSchedulesItems(items, sortKey, sortDirection, options = {}) {
  const {
    normalizeSortKey,
    normalizeDirection,
    formatScheduleStatus,
  } = options;

  const normalizedSortKey = normalizeSortKey("schedules", sortKey, "scheduleKey");
  const direction = normalizeDirection(sortDirection, "asc") === "desc" ? -1 : 1;
  const normalizedItems = Array.isArray(items) ? [...items] : [];

  const readSortValue = (schedule) => {
    if (normalizedSortKey === "status") {
      return String(formatScheduleStatus(schedule) || "").toLowerCase();
    }
    if (normalizedSortKey === "nextDueAt") {
      return String(schedule?.nextDueAt || "");
    }
    if (normalizedSortKey === "selectedJobKey") {
      return String(schedule?.selectedJobKey || "").toLowerCase();
    }
    return String(schedule?.scheduleKey || "").toLowerCase();
  };

  normalizedItems.sort((left, right) => {
    const leftValue = readSortValue(left);
    const rightValue = readSortValue(right);
    return leftValue.localeCompare(rightValue, undefined, { numeric: true, sensitivity: "base" }) * direction;
  });
  return normalizedItems;
}

