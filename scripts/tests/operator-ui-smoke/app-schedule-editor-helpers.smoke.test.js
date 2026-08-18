import test from "node:test";
import assert from "node:assert/strict";

import { createAppScheduleEditorHelpers } from "../../../src/main/resources/static/operator/app-schedule-editor-helpers.js";

class FakeClassList {
  constructor() {
	this.values = new Set();
  }

  toggle(value, force) {
	if (force) {
	  this.values.add(value);
	} else {
	  this.values.delete(value);
	}
  }

  remove(value) {
	this.values.delete(value);
  }

  has(value) {
	return this.values.has(value);
  }
}

class FakeElement {
  constructor(id = "") {
	this.id = id;
	this.hidden = false;
	this.disabled = false;
	this.checked = false;
	this.value = "";
	this.textContent = "";
	this.className = "";
	this.classList = new FakeClassList();
  }
}

function installEditorDom() {
  const previousDocument = globalThis.document;
  const elements = new Map();
  [
	"schedules-editor",
	"schedules-editor-save-btn",
	"schedules-editor-key-input",
	"schedules-editor-job-select",
	"schedules-editor-expression-input",
	"schedules-editor-expression-state",
	"schedules-editor-title",
	"schedules-editor-state",
	"schedules-editor-timezone-input",
	"schedules-editor-description-input",
	"schedules-editor-enabled-input",
  ].forEach((id) => {
	elements.set(id, new FakeElement(id));
  });

  globalThis.document = {
	getElementById(id) {
	  return elements.get(id) || null;
	},
  };

  return {
	elements,
	restore() {
	  globalThis.document = previousDocument;
	},
  };
}

function createHelpers(viewState) {
  return createAppScheduleEditorHelpers({
	describeScheduleExpression: () => "Runs every minute.",
	validateScheduleExpression: (value) => {
	  if (String(value || "").trim() === "") {
		return { valid: false, message: "Expression is required." };
	  }
	  return { valid: true, message: "" };
	},
	valueOrDash: (value) => (value === null || value === undefined || value === "" ? "-" : String(value)),
	viewState,
  });
}

test("schedule editor helpers prepare edit mode and update validation", () => {
  const dom = installEditorDom();
  try {
	const viewState = {
	  schedules: {
		editorMode: "create",
		editingScheduleId: "",
		editCancelReturnHash: "",
	  },
	};
	const helpers = createHelpers(viewState);

	const prepared = helpers.prepareScheduleEditorForOpen({
	  mode: "edit",
	  schedule: {
		scheduleId: "sched-1",
		scheduleKey: "daily",
		selectedJobKey: "customer-load",
		expression: "* * * * *",
		timezone: "UTC",
		description: "Daily",
		enabled: true,
	  },
	});

	assert.equal(Boolean(prepared), true);
	assert.equal(viewState.schedules.editorMode, "edit");
	assert.equal(viewState.schedules.editingScheduleId, "sched-1");
	assert.equal(dom.elements.get("schedules-editor-title").textContent, "Edit schedule");

	const valid = helpers.updateScheduleEditorExpressionValidation();
	assert.equal(valid, true);
	assert.equal(dom.elements.get("schedules-editor-save-btn").disabled, false);
	assert.equal(dom.elements.get("schedules-editor-expression-state").className, "state success");
  } finally {
	dom.restore();
  }
});

test("schedule editor helpers close and reset editor state", () => {
  const dom = installEditorDom();
  try {
	const viewState = {
	  schedules: {
		editorMode: "edit",
		editingScheduleId: "sched-2",
		editCancelReturnHash: "#/schedules/sched-2",
	  },
	};
	const helpers = createHelpers(viewState);

	dom.elements.get("schedules-editor").hidden = false;
	dom.elements.get("schedules-editor-key-input").disabled = true;
	dom.elements.get("schedules-editor-key-input").value = "daily";
	dom.elements.get("schedules-editor-expression-input").value = "* * * * *";
	dom.elements.get("schedules-editor-expression-input").classList.toggle("expression-invalid", true);

	const closed = helpers.closeScheduleEditorUi();
	assert.equal(closed, true);
	assert.equal(viewState.schedules.editorMode, "create");
	assert.equal(viewState.schedules.editingScheduleId, "");
	assert.equal(viewState.schedules.editCancelReturnHash, "");
	assert.equal(dom.elements.get("schedules-editor").hidden, true);
	assert.equal(dom.elements.get("schedules-editor-key-input").disabled, false);
	assert.equal(dom.elements.get("schedules-editor-key-input").value, "");
	assert.equal(dom.elements.get("schedules-editor-expression-input").classList.has("expression-invalid"), false);
  } finally {
	dom.restore();
  }
});


