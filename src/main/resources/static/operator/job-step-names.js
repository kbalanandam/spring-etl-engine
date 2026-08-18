export function extractStepNamesFromRawYaml(rawYaml) {
  const text = typeof rawYaml === "string" ? rawYaml : "";
  if (text.trim() === "") {
    return [];
  }

  const lines = text.split(/\r?\n/);
  const stepNames = [];
  let inStepsBlock = false;
  let stepsIndent = 0;

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

    const stepNameMatch = line.match(/^\s*-\s*name\s*:\s*(.+)\s*$/);
    if (!stepNameMatch) {
      continue;
    }

    let stepName = stepNameMatch[1].trim();
    if ((stepName.startsWith('"') && stepName.endsWith('"')) || (stepName.startsWith("'") && stepName.endsWith("'"))) {
      stepName = stepName.substring(1, stepName.length - 1).trim();
    }
    if (stepName !== "") {
      stepNames.push(stepName);
    }
  }

  return Array.from(new Set(stepNames));
}

