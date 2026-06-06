export function buildJobConfigDocuments(payload) {
  const rawYaml = typeof payload?.rawYaml === "string" ? payload.rawYaml : "";
  const pathHints = parseJobConfigPathHints(rawYaml);

  const documents = [
    {
      key: "job",
      label: "Job config",
      path: payload?.jobConfigPath || "job-config.yaml",
      content: payload?.rawYaml || "",
    },
    {
      key: "source",
      label: "Source config",
      path: payload?.sourceConfigPath || pathHints.sourceConfigPath || "source-config.yaml",
      content: payload?.sourceRawYaml || "",
    },
    {
      key: "target",
      label: "Target config",
      path: payload?.targetConfigPath || pathHints.targetConfigPath || "target-config.yaml",
      content: payload?.targetRawYaml || "",
    },
    {
      key: "processor",
      label: "Processor config",
      path: payload?.processorConfigPath || pathHints.processorConfigPath || "processor-config.yaml",
      content: payload?.processorRawYaml || "",
    },
  ];

  const missingCompanionDocuments = documents
    .filter((document) => document.key !== "job" && String(document.content || "").trim() === "")
    .map((document) => document.label);

  return {
    documents,
    missingCompanionDocuments,
  };
}

export function pickJobConfigDocument(documents, requestedKey) {
  const list = Array.isArray(documents) ? documents : [];
  if (list.length === 0) {
    return null;
  }
  const normalizedKey = normalizeDocumentKey(requestedKey);
  const selected = list.find((document) => document.key === normalizedKey);
  return selected || list[0];
}

export function normalizeDocumentKey(value) {
  const normalized = String(value || "").trim().toLowerCase();
  if (normalized === "job" || normalized === "source" || normalized === "target" || normalized === "processor") {
    return normalized;
  }
  return "job";
}

export function buildMissingCompanionWarning(selectedDocument, missingCompanionDocuments) {
  const selected = selectedDocument || null;
  const missing = Array.isArray(missingCompanionDocuments) ? missingCompanionDocuments : [];
  if (!selected || selected.key === "job" || !missing.includes(selected.label)) {
    return "";
  }
  return `Companion config payload can be unavailable when file resolution is blocked on the backend path. Missing: ${missing.join(", ")}.`;
}

function parseJobConfigPathHints(rawYaml) {
  const hints = {
    sourceConfigPath: "",
    targetConfigPath: "",
    processorConfigPath: "",
  };
  const lines = String(rawYaml || "").split(/\r?\n/);
  for (const line of lines) {
    const match = line.match(/^\s*(sourceConfigPath|targetConfigPath|processorConfigPath)\s*:\s*(.+)\s*$/);
    if (!match) {
      continue;
    }
    hints[match[1]] = String(match[2]).trim();
  }
  return hints;
}

