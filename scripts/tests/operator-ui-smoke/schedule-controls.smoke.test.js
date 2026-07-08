import test from "node:test";
import assert from "node:assert/strict";

import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

test("operator job detail includes native schedule panel markup", async () => {
  const htmlPath = resolve(process.cwd(), "src/main/resources/static/operator/index.html");
  const source = await readFile(htmlPath, "utf8");

  assert.match(source, /id="job-detail-schedule-state"/);
  assert.match(source, /id="job-detail-schedule-summary"/);
  assert.match(source, /id="job-detail-schedule-action-btn"/);
  assert.match(source, /id="job-detail-trigger-events-state"/);
  assert.match(source, /id="job-detail-trigger-events-list"/);
  assert.match(source, /Recent trigger events/);
  assert.match(source, /Native scheduler controls only\./);
});

test("operator includes schedule workbench and trigger evidence markup", async () => {
  const htmlPath = resolve(process.cwd(), "src/main/resources/static/operator/index.html");
  const source = await readFile(htmlPath, "utf8");

  assert.match(source, /id="tab-schedules"/);
  assert.match(source, /id="view-schedules"/);
  assert.match(source, /id="view-schedule-detail"/);
  assert.match(source, /id="schedules-editor"/);
  assert.match(source, /id="schedules-new-btn"/);
  assert.match(source, /id="schedules-filter-input"/);
  assert.match(source, /id="schedules-sort-select"/);
  assert.match(source, /id="schedules-page-size-select"/);
  assert.match(source, /id="schedules-page-status"/);
  assert.doesNotMatch(source, /id="schedules-evidence-panel"/);
  assert.match(source, /id="schedules-editor-expression-state"/);
  assert.doesNotMatch(source, /Cron expression \(5 or 6 fields\)/);
  assert.match(source, /id="schedule-detail-enable-disable-btn"/);
  assert.match(source, /id="schedule-detail-pause-resume-btn"/);
  assert.match(source, /id="schedule-detail-edit-btn"/);
  assert.match(source, /id="schedule-detail-triggers-toggle-btn"/);
  assert.match(source, /id="schedule-detail-triggers-panel"/);
  assert.match(source, /id="schedule-detail-triggers-list"/);
  assert.match(source, /Use explicit actions to open schedule history or jump to the related job context\./);
  assert.match(source, /Trigger origin/);
});

test("operator app wires schedule editor and detail schedule state-change actions", async () => {
  const appPath = resolve(process.cwd(), "src/main/resources/static/operator/app.js");
  const source = await readFile(appPath, "utf8");
  const helpersPath = resolve(process.cwd(), "src/main/resources/static/operator/schedule-ui-helpers.js");
  const helpersSource = await readFile(helpersPath, "utf8");
  const detailHelpersPath = resolve(process.cwd(), "src/main/resources/static/operator/app-schedule-detail-helpers.js");
  const detailHelpersSource = await readFile(detailHelpersPath, "utf8");
  const editorHelpersPath = resolve(process.cwd(), "src/main/resources/static/operator/app-schedule-editor-helpers.js");
  const editorHelpersSource = await readFile(editorHelpersPath, "utf8");
  const stylesPath = resolve(process.cwd(), "src/main/resources/static/operator/styles.css");
  const stylesSource = await readFile(stylesPath, "utf8");

  assert.match(source, /DEFAULT_SCHEDULE_LOOKUP_LIMIT\s*=\s*200/);
  assert.match(source, /\/api\/v1\/schedules\?limit=\$\{DEFAULT_SCHEDULE_LOOKUP_LIMIT\}/);
  assert.match(source, /No native schedule is configured for this job\./);
  assert.match(source, /from "\.\/schedule-ui-helpers\.js"/);
  assert.match(source, /Multiple native schedules are configured for this job \(\$\{matches\.length\} found\)\. Managing \$\{valueOrDash\(selectedSchedule\.scheduleKey\)\} in this panel\./);
  assert.match(source, /function openScheduleEditor\(/);
  assert.match(source, /const returnToDetailAfterSave = mode === "edit" && editCancelReturnHash !== "";/);
  assert.doesNotMatch(source, /function requestScheduleWorkbenchStateChange\(schedule, action, requestId\)/);
  assert.match(source, /function classifyTriggerAcceptance\(payload, options = \{\}\)/);
  assert.match(source, /function hasLaunchedRunIdValue\(payload\)/);
  assert.doesNotMatch(source, /Pause schedule \$\{valueOrDash\(scheduleKey\)\}\?/);
  assert.match(source, /function buildSchedulesRouteQuery\(/);
  assert.match(source, /function buildSchedulesListHash\(/);
  assert.match(source, /row\.dataset\.scheduleId = scheduleId;/);
  assert.match(source, /detailsButton\.textContent = "Details";/);
  assert.match(source, /detailsButton\.addEventListener\("click", \(\) => \{/);
  assert.match(source, /location\.hash = scheduleQuerySuffix === ""/);
  assert.doesNotMatch(source, /row\.addEventListener\("click"/);
  assert.doesNotMatch(source, /function focusSelectedScheduleRow\(/);
  assert.doesNotMatch(source, /function refreshScheduleWorkbenchEvidence\(schedule, requestId\)/);
  assert.doesNotMatch(source, /function refreshSelectedScheduleWorkbenchEvidence\(items, requestId\)/);
  assert.doesNotMatch(source, /schedule-selected-row/);
  assert.match(source, /function submitScheduleEditor\(/);
  assert.match(source, /from "\.\/schedule-expression\.js"/);
  assert.match(editorHelpersSource, /validateScheduleExpression\(expressionInput\.value\)/);
  assert.match(editorHelpersSource, /describeScheduleExpression\(expressionInput\.value\)/);
  assert.match(source, /function updateScheduleEditorExpressionValidation\(/);
  assert.match(source, /from "\.\/app-schedule-editor-helpers\.js"/);
  assert.match(editorHelpersSource, /Meaning: \$\{meaning\}/);
  assert.match(editorHelpersSource, /expression-invalid/);
  assert.match(source, /method = mode === "edit" \? "PUT" : "POST"/);
  assert.match(source, /\/api\/v1\/schedules\/\$\{encodeURIComponent\(scheduleId\)\}/);
  assert.match(source, /\/api\/v1\/schedules"/);
  assert.match(source, /viewState\.schedules\.selectedScheduleId = String\(schedule\?\.scheduleId \|\| ""\)\.trim\(\);/);
  assert.match(source, /SCHEDULE_STATE_CHANGE_ACTIONS/);
  assert.match(source, /\/api\/v1\/schedules\/\$\{encodeURIComponent\(normalizedScheduleId\)\}:\$\{normalizedAction\}/);
  assert.match(source, /Schedule action already in progress\. Please wait for the current response\./);
  assert.match(source, /\/api\/v1\/schedules\/\$\{encodeURIComponent\(scheduleId\)\}/);
  assert.match(source, /\/api\/v1\/schedules\/\$\{encodeURIComponent\(normalizedScheduleId\)\}\/trigger-events\?page=\$\{page\}&size=\$\{size\}/);
  assert.match(source, /\/api\/v1\/schedules\/\$\{encodeURIComponent\(scheduleId\)\}:trigger-now/);
  assert.match(source, /Trigger warning: schedule is not assigned to a job\/task, so launch cannot be confirmed\./);
  assert.match(source, /Trigger now accepted but worker launch was not confirmed for \$\{selectedJobKey\}\./);
  assert.match(source, /const detail = backendMessage !== "" \? backendMessage : `status=\$\{response\.status\}`;/);
  assert.match(source, /Schedule trigger-now endpoint is unavailable in the running backend\. Restart the app with the latest build\./);
  assert.match(source, /function loadScheduleDetail\(routeState\)/);
  assert.match(source, /function requestScheduleDetailStateChange\(scheduleId, action, requestId\)/);
  assert.match(source, /function setScheduleDetailTriggersExpanded\(expanded\)/);
  assert.match(source, /from "\.\/app-schedule-detail-helpers\.js"/);
  assert.match(detailHelpersSource, /triggerToggleButton\.textContent = isExpanded \? "Hide recent triggers" : "Show recent triggers";/);
  assert.match(detailHelpersSource, /decisionChip\.className = "decision-chip"/);
  assert.match(detailHelpersSource, /decision-chip-warning/);
  assert.match(detailHelpersSource, /decision-chip-success/);
  assert.match(source, /const preserveLayout = Boolean\(viewState\.schedules\.refreshDetailInPlace\);/);
  assert.doesNotMatch(source, /Refreshing schedule detail\.\.\./);
  assert.match(source, /buildSchedulesListHash\(sourceScheduleId, sourceScheduleListQuery\)/);
  assert.match(source, /params\.set\("scheduleId", sourceScheduleId\);/);
  assert.match(source, /params\.set\("scheduleListQuery", sourceScheduleListQuery\);/);
  assert.match(source, /Back to schedules/);
  assert.match(source, /createAppJobDetailHelpers/);
  assert.match(source, /const items = Array\.isArray\(payload\.items\) \? payload\.items : \[\];/);
  assert.match(source, /function refreshJobDetailRecentTriggerEvents\(jobKeyValue, query, options = \{\}\)/);
  assert.match(source, /function refreshJobDetailRecentRuns\(jobKeyValue, query\)/);
  assert.match(stylesSource, /\.decision-chip/);
  assert.match(stylesSource, /\.decision-chip-success/);
  assert.match(stylesSource, /\.decision-chip-warning/);
  assert.match(stylesSource, /\.decision-chip-error/);
  assert.match(stylesSource, /\.state\.state-success/);
  assert.match(stylesSource, /\.state\.state-warning/);
  assert.doesNotMatch(stylesSource, /#schedules-evidence-panel/);
  assert.match(source, /const scheduleControlState = getScheduleControlState\(selectedSchedule\);/);
  assert.match(source, /scheduleActionButton\.textContent = scheduleControlState\.detailPauseResumeLabel/);
  assert.match(source, /closeScheduleEditor\(\{ navigateToReturnHash: returnToDetailAfterSave \}\);/);
  assert.doesNotMatch(source, /editButton\.textContent = "Edit";/);
  assert.doesNotMatch(source, /toggleButton\.textContent = scheduleControlState\.pauseResumeLabel/);

  assert.match(helpersSource, /export function selectScheduleForJobDetail\(schedules, preferredScheduleId\)/);
  assert.match(helpersSource, /export function getScheduleControlState\(schedule\)/);
  assert.match(helpersSource, /export function formatTriggerOriginToken\(token\)/);
  assert.match(editorHelpersSource, /export function createAppScheduleEditorHelpers\(options = \{\}\)/);
});


