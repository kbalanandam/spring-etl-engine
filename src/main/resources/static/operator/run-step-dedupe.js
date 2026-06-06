function hasValue(value) {
  return value !== null && value !== undefined && value !== "" && value !== "-";
}

function metricScore(step) {
  let score = 0;
  if (hasValue(step && step.readCount)) {
    score += 1;
  }
  if (hasValue(step && step.writeCount)) {
    score += 1;
  }
  if (hasValue(step && step.rejectedCount)) {
    score += 1;
  }
  return score;
}

function statusScore(status) {
  const normalized = String(status || "").toUpperCase();
  if (normalized === "COMPLETED") {
    return 3;
  }
  if (normalized === "FAILED") {
    return 2;
  }
  if (normalized === "STARTED") {
    return 1;
  }
  return 0;
}

function preferredStep(left, right) {
  const leftMetricScore = metricScore(left);
  const rightMetricScore = metricScore(right);
  if (rightMetricScore > leftMetricScore) {
    return right;
  }
  if (rightMetricScore < leftMetricScore) {
    return left;
  }

  const leftStatusScore = statusScore(left && left.status);
  const rightStatusScore = statusScore(right && right.status);
  if (rightStatusScore > leftStatusScore) {
    return right;
  }
  return left;
}

export function coalesceRunSteps(steps) {
  const list = Array.isArray(steps) ? steps : [];
  const byKey = new Map();

  list.forEach((step, index) => {
    const stepName = String((step && step.stepName) || "").trim();
    const key = stepName === "" ? `__step_${index}` : stepName.toLowerCase();
    if (!byKey.has(key)) {
      byKey.set(key, step);
      return;
    }
    byKey.set(key, preferredStep(byKey.get(key), step));
  });

  return Array.from(byKey.values());
}

