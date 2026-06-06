import test from "node:test";
import assert from "node:assert/strict";

import { createRunRecoveryPanel } from "../../../src/main/resources/static/operator/run-recovery-panel.js";
import { installDom } from "./dom-shim.js";

const IDS = [
  "run-detail-recovery-state",
  "run-detail-recovery-box",
  "run-detail-recovery-run-record-id",
  "run-detail-recovery-attempt-link-id",
  "run-detail-recovery-link-kind",
  "run-detail-recovery-prior-run-record-id",
  "run-detail-recovery-prior-job-execution-id",
  "run-detail-recovery-resume-supported",
  "run-detail-recovery-blocked-reason",
  "run-detail-recovery-anchors-list",
  "run-detail-recovery-anchors-empty",
];

test("run recovery panel renders advisory recovery diagnostics", () => {
  const { elements, restore } = installDom(IDS);
  try {
    const panel = createRunRecoveryPanel({
      valueOrDash: (value) => (value === null || value === undefined || value === "" ? "-" : String(value)),
    });

    panel.render({
      runRecordId: "rr-901",
      attemptLinkId: null,
      linkKind: null,
      priorRunRecordId: null,
      priorJobExecutionId: null,
      resumeSupported: false,
      resumeBlockedReason: "resume-from-checkpoint is not supported in the current shipped runtime; rerun-from-start remains the active execution boundary.",
      checkpointAnchors: [
        {
          checkpointAnchorId: "ca-log-901",
          stepRecordId: null,
          anchorKind: "RUN_LOG",
          anchorRef: "logs/2026-05-27/customer-load.log",
          anchorStatus: "COMPLETED",
        },
      ],
    });

    assert.equal(elements.get("run-detail-recovery-state").textContent, "Advisory recovery diagnostics loaded.");
    assert.equal(elements.get("run-detail-recovery-box").hidden, false);
    assert.equal(elements.get("run-detail-recovery-run-record-id").textContent, "rr-901");
    assert.equal(elements.get("run-detail-recovery-attempt-link-id").textContent, "-");
    assert.equal(elements.get("run-detail-recovery-resume-supported").textContent, "false");
    assert.equal(elements.get("run-detail-recovery-anchors-list").children.length, 1);
    assert.match(elements.get("run-detail-recovery-anchors-list").children[0].textContent, /RUN_LOG/);
    assert.equal(elements.get("run-detail-recovery-anchors-empty").hidden, true);
  } finally {
    restore();
  }
});

test("run recovery panel shows empty anchors message when none exist", () => {
  const { elements, restore } = installDom(IDS);
  try {
    const panel = createRunRecoveryPanel({
      valueOrDash: (value) => (value === null || value === undefined || value === "" ? "-" : String(value)),
    });

    panel.render({
      runRecordId: "rr-902",
      attemptLinkId: "al-902",
      linkKind: "INITIAL",
      priorRunRecordId: null,
      priorJobExecutionId: null,
      resumeSupported: false,
      resumeBlockedReason: "resume-from-checkpoint is not supported in the current shipped runtime; rerun-from-start remains the active execution boundary.",
      checkpointAnchors: [],
    });

    assert.equal(elements.get("run-detail-recovery-anchors-list").hidden, true);
    assert.equal(elements.get("run-detail-recovery-anchors-empty").hidden, false);
  } finally {
    restore();
  }
});

test("run recovery panel shows neutral message when recovery is missing", () => {
  const { elements, restore } = installDom(IDS);
  try {
    const panel = createRunRecoveryPanel({
      valueOrDash: (value) => (value === null || value === undefined || value === "" ? "-" : String(value)),
    });

    panel.render(null);

    assert.equal(elements.get("run-detail-recovery-state").textContent, "No advisory recovery diagnostics found for this run.");
    assert.equal(elements.get("run-detail-recovery-state").className, "state");
    assert.equal(elements.get("run-detail-recovery-box").hidden, true);
  } finally {
    restore();
  }
});

