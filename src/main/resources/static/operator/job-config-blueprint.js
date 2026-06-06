export function buildJobConfigBlueprint(payload) {
  const jobRawYaml = typeof payload?.rawYaml === "string" ? payload.rawYaml : "";
  const sourceRawYaml = typeof payload?.sourceRawYaml === "string" ? payload.sourceRawYaml : "";
  const targetRawYaml = typeof payload?.targetRawYaml === "string" ? payload.targetRawYaml : "";
  const processorRawYaml = typeof payload?.processorRawYaml === "string" ? payload.processorRawYaml : "";
  const rawPathHints = parseTopLevelPathHints(jobRawYaml);

  const steps = parseJobSteps(jobRawYaml);
  const sourcesByName = parseNamedEntries(sourceRawYaml, "sources", "sourceName");
  const targetsByName = parseNamedEntries(targetRawYaml, "targets", "targetName");
  const processorMappings = parseNamedEntries(processorRawYaml, "mappings", "source", "target");
  const rejectPath = parseProcessorRejectPath(processorRawYaml);

  const enrichedSteps = steps.map((step) => {
    const source = sourcesByName.get(normalizeKey(step.source));
    const target = targetsByName.get(normalizeKey(step.target));
    const mapping = processorMappings.get(`${normalizeKey(step.source)}->${normalizeKey(step.target)}`);

    return {
      name: step.name || "-",
      source: step.source || "-",
      target: step.target || "-",
      sourcePath: firstValue(source, ["filePath", "path", "queryFile", "directoryPath"]),
      sourceFormat: firstValue(source, ["format", "type"]),
      targetPath: firstValue(target, ["filePath", "path", "directoryPath"]),
      targetFormat: firstValue(target, ["format", "type"]),
      processorFieldCount: mapping ? countFieldMappings(mapping.rawBlock) : 0,
      processorTransformCount: mapping ? countSectionTypeEntries(mapping.rawBlock, "transforms") : 0,
      processorRuleCount: mapping ? countSectionTypeEntries(mapping.rawBlock, "rules") : 0,
      rejectPath: rejectPath,
      hasProcessorMapping: Boolean(mapping),
      hasProcessorPayload: processorRawYaml.trim() !== "",
    };
  });

  return {
    sourceConfigPath: payload?.sourceConfigPath || rawPathHints.sourceConfigPath || "",
    targetConfigPath: payload?.targetConfigPath || rawPathHints.targetConfigPath || "",
    processorConfigPath: payload?.processorConfigPath || rawPathHints.processorConfigPath || "",
    hasSourcePayload: sourceRawYaml.trim() !== "",
    hasTargetPayload: targetRawYaml.trim() !== "",
    hasProcessorPayload: processorRawYaml.trim() !== "",
    steps: enrichedSteps,
  };
}

function parseTopLevelPathHints(rawYaml) {
  const hints = {
    sourceConfigPath: "",
    targetConfigPath: "",
    processorConfigPath: "",
  };
  const lines = (typeof rawYaml === "string" ? rawYaml : "").split(/\r?\n/);
  for (const line of lines) {
    const match = line.match(/^\s*(sourceConfigPath|targetConfigPath|processorConfigPath)\s*:\s*(.+)\s*$/);
    if (!match) {
      continue;
    }
    hints[match[1]] = stripYamlQuotes(match[2]);
  }
  return hints;
}

function parseJobSteps(rawYaml) {
  const text = typeof rawYaml === "string" ? rawYaml : "";
  if (text.trim() === "") {
    return [];
  }

  const lines = text.split(/\r?\n/);
  const steps = [];
  let inStepsBlock = false;
  let stepsIndent = 0;
  let current = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === "" || trimmed.startsWith("#")) {
      continue;
    }

    if (!inStepsBlock) {
      const stepsMatch = line.match(/^(\s*)steps\s*:\s*$/);
      if (stepsMatch) {
        inStepsBlock = true;
        stepsIndent = stepsMatch[1].length;
      }
      continue;
    }

    const indent = line.length - line.trimStart().length;
    if (indent <= stepsIndent && !line.trimStart().startsWith("-")) {
      break;
    }

    const itemStart = line.match(/^\s*-\s*(\w+)\s*:\s*(.+)\s*$/);
    if (itemStart) {
      if (current) {
        steps.push(current);
      }
      current = { name: "", source: "", target: "" };
      assignStepField(current, itemStart[1], itemStart[2]);
      continue;
    }

    if (!current) {
      continue;
    }

    const fieldMatch = line.match(/^\s*(\w+)\s*:\s*(.+)\s*$/);
    if (!fieldMatch) {
      continue;
    }
    assignStepField(current, fieldMatch[1], fieldMatch[2]);
  }

  if (current) {
    steps.push(current);
  }

  return steps;
}

function assignStepField(step, key, rawValue) {
  const value = stripYamlQuotes(rawValue);
  if (key === "name") {
    step.name = value;
    return;
  }
  if (key === "source") {
    step.source = value;
    return;
  }
  if (key === "target") {
    step.target = value;
  }
}

function parseNamedEntries(rawYaml, sectionName, primaryNameKey, secondaryNameKey = "") {
  const entries = new Map();
  const lines = (typeof rawYaml === "string" ? rawYaml : "").split(/\r?\n/);
  let inSection = false;
  let sectionIndent = 0;
  let itemIndent = -1;
  let current = null;
  let currentRawLines = [];

  const flush = () => {
    if (!current) {
      return;
    }
    current.rawBlock = currentRawLines.join("\n");
    const keyA = normalizeKey(current[primaryNameKey]);
    const keyB = secondaryNameKey ? normalizeKey(current[secondaryNameKey]) : "";
    if (secondaryNameKey) {
      if (keyA && keyB) {
        entries.set(`${keyA}->${keyB}`, current);
      }
    } else if (keyA) {
      entries.set(keyA, current);
    }
    current = null;
    currentRawLines = [];
  };

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === "" || trimmed.startsWith("#")) {
      continue;
    }

    if (!inSection) {
      const sectionMatch = line.match(new RegExp(`^(\\s*)${escapeRegex(sectionName)}\\s*:\\s*$`));
      if (sectionMatch) {
        inSection = true;
        sectionIndent = sectionMatch[1].length;
      }
      continue;
    }

    const indent = line.length - line.trimStart().length;
    if (indent <= sectionIndent && !line.trimStart().startsWith("-")) {
      flush();
      break;
    }

    const itemMatch = line.match(/^(\s*)-\s*(.*)$/);
    const startsTopLevelItem = itemMatch
      && indent > sectionIndent
      && (itemIndent < 0 || indent === itemIndent);
    if (startsTopLevelItem) {
      flush();
      itemIndent = itemMatch[1].length;
      current = {};
      currentRawLines.push(line);

      const inline = itemMatch[2] || "";
      const inlineField = inline.match(/^(\w+)\s*:\s*(.+)$/);
      if (inlineField) {
        current[inlineField[1]] = stripYamlQuotes(inlineField[2]);
      }
      continue;
    }

    if (!current || indent <= itemIndent) {
      continue;
    }

    currentRawLines.push(line);
    const fieldMatch = line.match(/^\s*(\w+)\s*:\s*(.+)\s*$/);
    if (!fieldMatch) {
      continue;
    }
    const key = fieldMatch[1];
    if (current[key] === undefined || current[key] === "") {
      current[key] = stripYamlQuotes(fieldMatch[2]);
    }
  }

  flush();
  return entries;
}

function parseProcessorRejectPath(rawYaml) {
  const lines = (typeof rawYaml === "string" ? rawYaml : "").split(/\r?\n/);
  let inRejectHandling = false;
  let rejectIndent = 0;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === "" || trimmed.startsWith("#")) {
      continue;
    }

    if (!inRejectHandling) {
      const match = line.match(/^(\s*)rejectHandling\s*:\s*$/);
      if (match) {
        inRejectHandling = true;
        rejectIndent = match[1].length;
      }
      continue;
    }

    const indent = line.length - line.trimStart().length;
    if (indent <= rejectIndent) {
      break;
    }

    const outputPathMatch = line.match(/^\s*(outputPath|rejectFilePath)\s*:\s*(.+)\s*$/);
    if (outputPathMatch) {
      return stripYamlQuotes(outputPathMatch[2]);
    }
  }

  return "";
}

function countFieldMappings(rawBlock) {
  const lines = String(rawBlock || "").split(/\r?\n/);
  let count = 0;
  for (const line of lines) {
    if (/^\s*-\s*(from|sourceField)\s*:/.test(line)) {
      count += 1;
    }
  }
  return count;
}

function countSectionTypeEntries(rawBlock, sectionKey) {
  const lines = String(rawBlock || "").split(/\r?\n/);
  let activeSection = "";
  let activeIndent = -1;
  let count = 0;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === "" || trimmed.startsWith("#")) {
      continue;
    }

    const indent = line.length - line.trimStart().length;
    const sectionMatch = line.match(/^\s*(transforms|rules)\s*:\s*$/);
    if (sectionMatch) {
      activeSection = sectionMatch[1];
      activeIndent = indent;
      continue;
    }

    if (activeSection && indent <= activeIndent) {
      activeSection = "";
      activeIndent = -1;
    }

    if (activeSection === sectionKey && /^\s*-\s*type\s*:/.test(line)) {
      count += 1;
    }
  }

  return count;
}

function firstValue(entry, keys) {
  if (!entry) {
    return "";
  }
  for (const key of keys) {
    const value = entry[key];
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      return String(value).trim();
    }
  }
  return "";
}

function stripYamlQuotes(rawValue) {
  const value = String(rawValue || "").trim();
  if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
    return value.substring(1, value.length - 1).trim();
  }
  return value;
}

function normalizeKey(value) {
  return String(value || "").trim().toLowerCase();
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}



