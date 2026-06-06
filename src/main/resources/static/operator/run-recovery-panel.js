export function createRunRecoveryPanel(options) {
  const valueOrDash = options && typeof options.valueOrDash === "function"
    ? options.valueOrDash
    : (value) => (value === null || value === undefined || value === "" ? "-" : String(value));

  function render(recovery) {
    const state = document.getElementById("run-detail-recovery-state");
    const box = document.getElementById("run-detail-recovery-box");
    const anchorsList = document.getElementById("run-detail-recovery-anchors-list");
    const anchorsEmpty = document.getElementById("run-detail-recovery-anchors-empty");

    if (!state || !box || !anchorsList || !anchorsEmpty) {
      return;
    }

    anchorsList.innerHTML = "";

    if (!recovery) {
      state.className = "state";
      state.textContent = "No advisory recovery diagnostics found for this run.";
      state.hidden = false;
      box.hidden = true;
      anchorsList.hidden = true;
      anchorsEmpty.hidden = true;
      return;
    }

    document.getElementById("run-detail-recovery-run-record-id").textContent = valueOrDash(recovery.runRecordId);
    document.getElementById("run-detail-recovery-attempt-link-id").textContent = valueOrDash(recovery.attemptLinkId);
    document.getElementById("run-detail-recovery-link-kind").textContent = valueOrDash(recovery.linkKind);
    document.getElementById("run-detail-recovery-prior-run-record-id").textContent = valueOrDash(recovery.priorRunRecordId);
    document.getElementById("run-detail-recovery-prior-job-execution-id").textContent = valueOrDash(recovery.priorJobExecutionId);
    document.getElementById("run-detail-recovery-resume-supported").textContent = String(Boolean(recovery.resumeSupported));
    document.getElementById("run-detail-recovery-blocked-reason").textContent = valueOrDash(recovery.resumeBlockedReason);

    const anchors = Array.isArray(recovery.checkpointAnchors) ? recovery.checkpointAnchors : [];
    if (anchors.length === 0) {
      anchorsList.hidden = true;
      anchorsEmpty.hidden = false;
    } else {
      anchors.forEach((anchor) => {
        const item = document.createElement("li");
        item.textContent = [
          valueOrDash(anchor.anchorKind),
          valueOrDash(anchor.anchorStatus),
          valueOrDash(anchor.anchorRef),
          `anchorId=${valueOrDash(anchor.checkpointAnchorId)}`,
          `stepRecordId=${valueOrDash(anchor.stepRecordId)}`,
        ].join(" | ");
        anchorsList.appendChild(item);
      });
      anchorsEmpty.hidden = true;
      anchorsList.hidden = false;
    }

    state.className = "state";
    state.textContent = "Advisory recovery diagnostics loaded.";
    state.hidden = false;
    box.hidden = false;
  }

  function reset() {
    render(null);
  }

  return {
    render,
    reset,
  };
}

