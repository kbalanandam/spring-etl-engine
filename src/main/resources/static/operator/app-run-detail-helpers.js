export function createAppRunDetailHelpers(options = {}) {
  const {
    coalesceRunSteps,
    escapeHtml,
    focusScopedLogViewer,
    valueOrDash,
  } = options;

  function renderRunSteps(steps) {
    const table = document.getElementById("run-detail-steps-table");
    const body = document.getElementById("run-detail-steps-body");
    const empty = document.getElementById("run-detail-steps-empty");
    const list = coalesceRunSteps(steps);

    body.innerHTML = "";
    if (list.length === 0) {
      table.hidden = true;
      empty.hidden = false;
      return;
    }

    list.forEach((step) => {
      const row = document.createElement("tr");
      row.innerHTML = `
      <td>${escapeHtml(step.stepName || "-")}</td>
      <td>${escapeHtml(step.status || "-")}</td>
      <td>${escapeHtml(valueOrDash(step.readCount))}</td>
      <td>${escapeHtml(valueOrDash(step.writeCount))}</td>
      <td>${escapeHtml(valueOrDash(step.rejectedCount))}</td>`;
      body.appendChild(row);
    });

    empty.hidden = true;
    table.hidden = false;
  }

  function renderRunFailureSummary(failureSummary) {
    const empty = document.getElementById("run-detail-failure-empty");
    const box = document.getElementById("run-detail-failure-box");

    if (!failureSummary) {
      box.hidden = true;
      empty.hidden = false;
      return;
    }

    document.getElementById("run-detail-failure-category").textContent = valueOrDash(failureSummary.category);
    document.getElementById("run-detail-failure-type").textContent = valueOrDash(failureSummary.exceptionType);
    document.getElementById("run-detail-failure-message").textContent = valueOrDash(failureSummary.message);

    empty.hidden = true;
    box.hidden = false;
  }

  function renderRunArtifacts(artifacts) {
    const listElement = document.getElementById("run-detail-artifacts-list");
    const empty = document.getElementById("run-detail-artifacts-empty");
    const list = Array.isArray(artifacts) ? artifacts : [];

    listElement.innerHTML = "";
    if (list.length === 0) {
      listElement.hidden = true;
      empty.hidden = false;
      return;
    }

    list.forEach((artifact) => {
      const item = document.createElement("li");
      const parts = [valueOrDash(artifact.role), valueOrDash(artifact.path)];
      if (artifact.recordCount !== null && artifact.recordCount !== undefined) {
        parts.push(`records=${artifact.recordCount}`);
      }
      item.textContent = parts.join(" | ");
      listElement.appendChild(item);
    });

    empty.hidden = true;
    listElement.hidden = false;
  }

  function renderRunEvidenceLinks(evidenceLinks) {
    const listElement = document.getElementById("run-detail-evidence-list");
    const empty = document.getElementById("run-detail-evidence-empty");
    const list = Array.isArray(evidenceLinks) ? evidenceLinks : [];

    listElement.innerHTML = "";
    if (list.length === 0) {
      listElement.hidden = true;
      empty.hidden = false;
      return;
    }

    list.forEach((link) => {
      const item = document.createElement("li");
      const href = (link.href || "").trim();
      if (href && String(link.type || "").toLowerCase() === "log-file") {
        const scopedAnchor = document.createElement("a");
        scopedAnchor.href = "#";
        scopedAnchor.textContent = "Run log (scoped viewer)";
        scopedAnchor.addEventListener("click", (event) => {
          event.preventDefault();
          focusScopedLogViewer();
        });
        item.appendChild(scopedAnchor);

        item.appendChild(document.createTextNode(" | "));

        const rawAnchor = document.createElement("a");
        rawAnchor.href = href;
        rawAnchor.textContent = "Full scenario log (raw file)";
        rawAnchor.target = "_blank";
        rawAnchor.rel = "noreferrer";
        item.appendChild(rawAnchor);
      } else if (href) {
        const anchor = document.createElement("a");
        anchor.href = href;
        anchor.textContent = `${valueOrDash(link.label)} (${valueOrDash(link.type)})`;
        anchor.target = "_blank";
        anchor.rel = "noreferrer";
        item.appendChild(anchor);
      } else {
        item.textContent = `${valueOrDash(link.label)} (${valueOrDash(link.type)}) - no link target`;
      }
      listElement.appendChild(item);
    });

    empty.hidden = true;
    listElement.hidden = false;
  }

  return {
    renderRunArtifacts,
    renderRunEvidenceLinks,
    renderRunFailureSummary,
    renderRunSteps,
  };
}

