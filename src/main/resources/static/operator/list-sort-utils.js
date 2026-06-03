export function sortItems(items, key, direction) {
  const factor = direction === "desc" ? -1 : 1;
  return [...items].sort((left, right) => factor * compareValues(left[key], right[key]));
}

export function compareValues(left, right) {
  if (left === right) {
    return 0;
  }
  if (left === null || left === undefined) {
    return -1;
  }
  if (right === null || right === undefined) {
    return 1;
  }

  const leftNumber = Number(left);
  const rightNumber = Number(right);
  const leftIsNumber = !Number.isNaN(leftNumber) && String(left).trim() !== "";
  const rightIsNumber = !Number.isNaN(rightNumber) && String(right).trim() !== "";
  if (leftIsNumber && rightIsNumber) {
    return leftNumber - rightNumber;
  }

  const leftTime = Date.parse(String(left));
  const rightTime = Date.parse(String(right));
  if (!Number.isNaN(leftTime) && !Number.isNaN(rightTime)) {
    return leftTime - rightTime;
  }

  return String(left).localeCompare(String(right));
}

export function toggleDirection(direction) {
  return direction === "asc" ? "desc" : "asc";
}

export function labelDirection(direction) {
  return direction === "asc" ? "Asc" : "Desc";
}

