export function createAppScheduleEditorHelpers(options = {}) {
  const {
    describeScheduleExpression,
    validateScheduleExpression,
    valueOrDash,
    viewState,
  } = options;

  function getScheduleEditorElements() {
    return {
      editor: document.getElementById("schedules-editor"),
      saveButton: document.getElementById("schedules-editor-save-btn"),
      keyInput: document.getElementById("schedules-editor-key-input"),
      jobSelect: document.getElementById("schedules-editor-job-select"),
      expressionInput: document.getElementById("schedules-editor-expression-input"),
      expressionState: document.getElementById("schedules-editor-expression-state"),
      title: document.getElementById("schedules-editor-title"),
      state: document.getElementById("schedules-editor-state"),
      timezoneInput: document.getElementById("schedules-editor-timezone-input"),
      descriptionInput: document.getElementById("schedules-editor-description-input"),
      enabledInput: document.getElementById("schedules-editor-enabled-input"),
    };
  }

  function updateScheduleEditorExpressionValidation() {
    const { editor, saveButton, keyInput, jobSelect, expressionInput, expressionState } = getScheduleEditorElements();
    if (!editor || editor.hidden || !saveButton || !keyInput || !jobSelect || !expressionInput || !expressionState) {
      return true;
    }

    const mode = viewState.schedules.editorMode === "edit" ? "edit" : "create";
    const scheduleKey = String(keyInput.value || "").trim();
    const selectedJobKey = String(jobSelect.value || "").trim();
    const expressionCheck = validateScheduleExpression(expressionInput.value);

    expressionInput.classList.toggle("expression-invalid", !expressionCheck.valid);
    if (expressionCheck.valid) {
      const meaning = describeScheduleExpression(expressionInput.value);
      expressionState.hidden = meaning === "";
      expressionState.className = "state success";
      expressionState.textContent = meaning === "" ? "" : `Meaning: ${meaning}`;
    } else {
      expressionState.hidden = false;
      expressionState.className = "state error";
      expressionState.textContent = expressionCheck.message;
    }

    const keyValid = mode === "edit" || scheduleKey !== "";
    const canSave = keyValid && selectedJobKey !== "" && expressionCheck.valid;
    saveButton.disabled = !canSave;
    return canSave;
  }

  function prepareScheduleEditorForOpen(input = {}) {
    const { mode, schedule } = input;
    const {
      editor,
      title,
      state,
      keyInput,
      jobSelect,
      expressionInput,
      timezoneInput,
      descriptionInput,
      enabledInput,
    } = getScheduleEditorElements();

    if (!editor || !title || !state || !keyInput || !jobSelect || !expressionInput || !timezoneInput || !descriptionInput || !enabledInput) {
      return null;
    }

    const normalizedMode = mode === "edit" ? "edit" : "create";
    const currentSchedule = normalizedMode === "edit" ? (schedule || {}) : null;

    viewState.schedules.editorMode = normalizedMode;
    viewState.schedules.editingScheduleId = normalizedMode === "edit"
      ? String(currentSchedule?.scheduleId || "").trim()
      : "";

    title.textContent = normalizedMode === "edit" ? "Edit schedule" : "Create schedule";
    keyInput.disabled = normalizedMode === "edit";
    keyInput.value = normalizedMode === "edit" ? String(currentSchedule?.scheduleKey || "") : "";
    jobSelect.value = normalizedMode === "edit" ? String(currentSchedule?.selectedJobKey || "") : "";
    expressionInput.value = normalizedMode === "edit" ? String(currentSchedule?.expression || "") : "";
    timezoneInput.value = normalizedMode === "edit" ? String(currentSchedule?.timezone || "UTC") : "UTC";
    descriptionInput.value = normalizedMode === "edit" ? String(currentSchedule?.description || "") : "";
    enabledInput.checked = normalizedMode === "edit" ? Boolean(currentSchedule?.enabled) : true;

    state.className = "state";
    state.textContent = normalizedMode === "edit"
      ? `Editing ${valueOrDash(currentSchedule?.scheduleId)}.`
      : "Create a new native schedule.";
    editor.hidden = false;

    return {
      editor,
      jobSelect,
      normalizedMode,
      currentSchedule,
    };
  }

  function closeScheduleEditorUi() {
    const {
      editor,
      state,
      keyInput,
      expressionState,
      expressionInput,
      timezoneInput,
      descriptionInput,
      enabledInput,
      jobSelect,
    } = getScheduleEditorElements();

    if (!editor || !state || !keyInput || !expressionState || !expressionInput || !timezoneInput || !descriptionInput || !enabledInput || !jobSelect) {
      return false;
    }

    viewState.schedules.editorMode = "create";
    viewState.schedules.editingScheduleId = "";
    viewState.schedules.editCancelReturnHash = "";
    keyInput.disabled = false;
    keyInput.value = "";
    expressionInput.value = "";
    timezoneInput.value = "UTC";
    descriptionInput.value = "";
    enabledInput.checked = true;
    jobSelect.value = "";
    expressionInput.classList.remove("expression-invalid");
    expressionState.hidden = true;
    expressionState.className = "state";
    expressionState.textContent = "";
    state.className = "state";
    state.textContent = "";
    editor.hidden = true;
    return true;
  }

  return {
    closeScheduleEditorUi,
    getScheduleEditorElements,
    prepareScheduleEditorForOpen,
    updateScheduleEditorExpressionValidation,
  };
}

