export function validateScheduleExpression(expressionValue) {
  const expression = String(expressionValue || "").trim();
  if (expression === "") {
    return { valid: false, message: "Expression is required." };
  }

  // Accept standard cron shortcuts while keeping backend as final authority.
  if (/^@(yearly|annually|monthly|weekly|daily|hourly|reboot)$/i.test(expression)) {
    return { valid: true, message: "" };
  }

  const fields = expression.split(/\s+/).filter((part) => part !== "");
  if (!(fields.length === 5 || fields.length === 6)) {
    return { valid: false, message: "Use 5 or 6 cron fields (for example: 0 0 * * *)." };
  }

  const allowedTokenPattern = /^[\dA-Za-z*\/?,\-#LW]+$/;
  const hasInvalidToken = fields.some((token) => !allowedTokenPattern.test(token));
  if (hasInvalidToken) {
    return { valid: false, message: "Expression contains unsupported characters." };
  }

  const fieldKinds = fields.length === 6
    ? ["second", "minute", "hour", "dayOfMonth", "month", "dayOfWeek"]
    : ["minute", "hour", "dayOfMonth", "month", "dayOfWeek"];

  const monthNames = new Set(["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]);
  const weekdayNames = new Set(["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"]);

  const hasUnsupportedAlphaChunk = fields.some((token, index) => {
    const kind = fieldKinds[index] || "";
    const alphaChunks = String(token || "").match(/[A-Za-z]+/g) || [];
    if (alphaChunks.length === 0) {
      return false;
    }

    return alphaChunks.some((chunk) => {
      const upper = chunk.toUpperCase();
      if (kind === "month") {
        return !monthNames.has(upper);
      }
      if (kind === "dayOfWeek") {
        return !(weekdayNames.has(upper) || upper === "L");
      }
      if (kind === "dayOfMonth") {
        return !(upper === "L" || upper === "W" || upper === "LW");
      }
      return true;
    });
  });

  if (hasUnsupportedAlphaChunk) {
    return { valid: false, message: "Expression contains unsupported cron value." };
  }

  const hasOutOfRangeWeekdayNumber = fields.some((token, index) => {
    const kind = fieldKinds[index] || "";
    if (kind !== "dayOfWeek") {
      return false;
    }

    const numericChunks = String(token || "").match(/\d+/g) || [];
    if (numericChunks.length === 0) {
      return false;
    }

    return numericChunks.some((chunk) => {
      const weekday = Number.parseInt(chunk, 10);
      return !Number.isFinite(weekday) || weekday < 0 || weekday > 7;
    });
  });

  if (hasOutOfRangeWeekdayNumber) {
    return { valid: false, message: "Expression contains unsupported cron value." };
  }

  return { valid: true, message: "" };
}

export function describeScheduleExpression(expressionValue) {
  const expression = String(expressionValue || "").trim();
  if (expression === "") {
    return "";
  }

  const validation = validateScheduleExpression(expression);
  if (!validation.valid) {
    return "";
  }

  const shortcutDescriptions = {
    "@yearly": "Runs once per year.",
    "@annually": "Runs once per year.",
    "@monthly": "Runs once per month.",
    "@weekly": "Runs once per week.",
    "@daily": "Runs once per day.",
    "@hourly": "Runs once per hour.",
    "@reboot": "Runs when the service starts.",
  };
  const shortcut = expression.toLowerCase();
  if (shortcutDescriptions[shortcut]) {
    return shortcutDescriptions[shortcut];
  }

  const fields = expression.split(/\s+/).filter((part) => part !== "");
  if (!(fields.length === 5 || fields.length === 6)) {
    return "";
  }

  const seconds = fields.length === 6 ? fields[0] : "0";
  const minute = fields.length === 6 ? fields[1] : fields[0];
  const hour = fields.length === 6 ? fields[2] : fields[1];
  const dayOfMonth = fields.length === 6 ? fields[3] : fields[2];
  const month = fields.length === 6 ? fields[4] : fields[3];
  const dayOfWeek = fields.length === 6 ? fields[5] : fields[4];

  const normalizeWildcard = (value) => (value === "?" ? "*" : value);
  const extractStep = (token) => {
    const match = String(token || "").match(/^(?:\*|\d+)\/(\d+)$/);
    return match ? match[1] : null;
  };
  const minuteToken = normalizeWildcard(minute);
  const hourToken = normalizeWildcard(hour);
  const dayOfMonthToken = normalizeWildcard(dayOfMonth);
  const monthToken = normalizeWildcard(month);
  const dayOfWeekToken = normalizeWildcard(dayOfWeek);

  const isEveryDay = dayOfMonthToken === "*" && monthToken === "*" && dayOfWeekToken === "*";
  const hasFixedMinute = /^\d+$/.test(minuteToken);
  const hasFixedHour = /^\d+$/.test(hourToken);
  const minuteStep = extractStep(minuteToken);
  const hourStep = extractStep(hourToken);
  const hasMinuteStep = minuteStep !== null;
  const hasHourStep = hourStep !== null;
  const hasSingleRunPerMinuteBoundary = fields.length === 5 || seconds === "0";
  const secondsStep = extractStep(seconds);

  if (seconds === "*" && minuteToken === "*" && hourToken === "*" && isEveryDay) {
    return "Runs every second.";
  }

  if (secondsStep !== null && minuteToken === "*" && hourToken === "*" && isEveryDay) {
    if (secondsStep === "1") {
      return "Runs every second.";
    }
    return `Runs every ${secondsStep} second(s).`;
  }

  if (minuteToken === "*" && hourToken === "*" && isEveryDay && hasSingleRunPerMinuteBoundary) {
    return "Runs every minute.";
  }

  if (hasMinuteStep && hourToken === "*" && isEveryDay && hasSingleRunPerMinuteBoundary) {
    if (minuteStep === "1") {
      return "Runs every minute.";
    }
    return `Runs every ${minuteStep} minute(s).`;
  }

  if (minuteToken === "*" && hourToken === "*" && dayOfMonthToken === "*" && monthToken === "*" && dayOfWeekToken !== "*" && hasSingleRunPerMinuteBoundary) {
    return "Runs every minute on selected weekday(s).";
  }

  if (hasMinuteStep && hourToken === "*" && dayOfMonthToken === "*" && monthToken === "*" && dayOfWeekToken !== "*" && hasSingleRunPerMinuteBoundary) {
    if (minuteStep === "1") {
      return "Runs every minute on selected weekday(s).";
    }
    return `Runs every ${minuteStep} minute(s) on selected weekday(s).`;
  }

  if (minuteToken === "0" && hourToken === "*" && isEveryDay && hasSingleRunPerMinuteBoundary) {
    return "Runs every hour.";
  }

  if (hasFixedMinute && hourToken === "*" && isEveryDay && hasSingleRunPerMinuteBoundary) {
    return `Runs at minute ${minuteToken} of every hour.`;
  }

  if (hasFixedMinute && hasHourStep && isEveryDay && hasSingleRunPerMinuteBoundary) {
    return `Runs every ${hourStep} hour(s) at minute ${minuteToken}.`;
  }

  if (hasMinuteStep && hasFixedHour && isEveryDay && hasSingleRunPerMinuteBoundary) {
    const hh = String(hourToken).padStart(2, "0");
    if (minuteStep === "1") {
      return `Runs every minute during hour ${hh} each day.`;
    }
    return `Runs every ${minuteStep} minute(s) during hour ${hh} each day.`;
  }

  if (hasFixedMinute && hasFixedHour && isEveryDay && hasSingleRunPerMinuteBoundary) {
    const hh = String(hourToken).padStart(2, "0");
    const mm = String(minuteToken).padStart(2, "0");
    return `Runs daily at ${hh}:${mm}.`;
  }

  if (hasFixedMinute && hasFixedHour && dayOfMonthToken === "*" && monthToken === "*" && dayOfWeekToken !== "*" && hasSingleRunPerMinuteBoundary) {
    const hh = String(hourToken).padStart(2, "0");
    const mm = String(minuteToken).padStart(2, "0");
    return `Runs weekly on selected weekday(s) at ${hh}:${mm}.`;
  }

  if (hasFixedMinute && hasFixedHour && dayOfMonthToken !== "*" && monthToken === "*" && dayOfWeekToken === "*" && hasSingleRunPerMinuteBoundary) {
    const hh = String(hourToken).padStart(2, "0");
    const mm = String(minuteToken).padStart(2, "0");
    return `Runs monthly on day ${dayOfMonthToken} at ${hh}:${mm}.`;
  }

  if (minuteToken === "0" && hourToken === "0" && dayOfMonthToken === "*" && monthToken === "*" && dayOfWeekToken === "*" && hasSingleRunPerMinuteBoundary) {
    return "Runs daily at midnight.";
  }

  if (minuteToken === "0" && hourToken === "0" && dayOfMonthToken === "*" && monthToken === "*" && dayOfWeekToken !== "*" && hasSingleRunPerMinuteBoundary) {
    return "Runs weekly on selected weekday(s) at 00:00.";
  }

  return `Runs on a custom cron pattern (${expression}).`;
}

