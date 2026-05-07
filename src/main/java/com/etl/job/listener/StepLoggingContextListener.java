package com.etl.job.listener;

import com.etl.logging.RunLoggingContext;
import com.etl.runtime.FileIngestionRuntimeSupport;
import com.etl.runtime.scenario.ScenarioHierarchyLoggingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class StepLoggingContextListener implements StepExecutionListener {

  private static final Logger logger = LoggerFactory.getLogger(StepLoggingContextListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        JobParameters jobParameters = jobParameters(stepExecution);
        ExecutionContext executionContext = stepExecution.getExecutionContext();
        String resolvedSubFlow = resolvedSubFlow(jobParameters, executionContext);
        RunLoggingContext.put(RunLoggingContext.MAIN_FLOW, stringParameter(jobParameters, "mainFlow"));
        RunLoggingContext.put(RunLoggingContext.SUB_FLOW, resolvedSubFlow);
        RunLoggingContext.put(RunLoggingContext.RECOVERY_POLICY, stringParameter(jobParameters, "recoveryPolicy"));
        RunLoggingContext.put(RunLoggingContext.STEP_NAME, stepExecution.getStepName());
	logger.info("STEP_EVENT event=step_started mainFlow={} subFlow={} recoveryPolicy={} stepName={} stepExecutionId={} stepSubFlowOrder={} dependsOnSubFlows={} consumesHandoffAliases={} producesHandoffAliases={} upstreamSteps={} linkTypes={} linkControlSummary={} stepSummary={}",
            stringParameter(jobParameters, "mainFlow"),
            resolvedSubFlow,
            stringParameter(jobParameters, "recoveryPolicy"),
            stepExecution.getStepName(),
            stepExecution.getId(),
            ScenarioHierarchyLoggingSupport.intValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_SUB_FLOW_ORDER_KEY, -1),
            ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_DEPENDS_ON_SUB_FLOWS_KEY),
            ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_CONSUMES_HANDOFFS_KEY),
            ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_PRODUCES_HANDOFFS_KEY),
            ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_UPSTREAM_STEPS_KEY),
            ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_LINK_TYPES_KEY),
            ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_LINK_CONTROL_SUMMARY_KEY),
            ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_SUMMARY_KEY));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
  JobParameters jobParameters = jobParameters(stepExecution);
  ExecutionContext executionContext = stepExecution.getExecutionContext();
  String resolvedSubFlow = resolvedSubFlow(jobParameters, executionContext);
  int rejectedCount = executionContext == null ? 0 : executionContext.getInt(FileIngestionRuntimeSupport.REJECTED_COUNT_KEY, 0);
  String rejectOutputPath = executionContext == null ? "" : executionContext.getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY, "");
  String archivedSourcePath = executionContext == null ? "" : executionContext.getString(FileIngestionRuntimeSupport.ARCHIVED_SOURCE_PATH_KEY, "");
	logger.info("STEP_EVENT event=step_finished mainFlow={} subFlow={} recoveryPolicy={} stepName={} stepExecutionId={} status={} readCount={} writeCount={} filterCount={} skipCount={} rollbackCount={} rejectedCount={} rejectOutputPath={} archivedSourcePath={} stepSubFlowOrder={} dependsOnSubFlows={} consumesHandoffAliases={} producesHandoffAliases={} upstreamSteps={} linkTypes={} linkControlSummary={} stepSummary={}",
        stringParameter(jobParameters, "mainFlow"),
        resolvedSubFlow,
        stringParameter(jobParameters, "recoveryPolicy"),
        stepExecution.getStepName(),
        stepExecution.getId(),
        stepExecution.getExitStatus().getExitCode(),
        stepExecution.getReadCount(),
        stepExecution.getWriteCount(),
        stepExecution.getFilterCount(),
        stepExecution.getSkipCount(),
		stepExecution.getRollbackCount(),
		rejectedCount,
		rejectOutputPath,
    archivedSourcePath,
    ScenarioHierarchyLoggingSupport.intValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_SUB_FLOW_ORDER_KEY, -1),
    ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_DEPENDS_ON_SUB_FLOWS_KEY),
    ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_CONSUMES_HANDOFFS_KEY),
    ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_PRODUCES_HANDOFFS_KEY),
    ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_UPSTREAM_STEPS_KEY),
    ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_LINK_TYPES_KEY),
    ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_LINK_CONTROL_SUMMARY_KEY),
    ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_SUMMARY_KEY));
        RunLoggingContext.put(RunLoggingContext.SUB_FLOW, stringParameter(jobParameters, "subFlow"));
        RunLoggingContext.clearStepScope();
        return stepExecution.getExitStatus();
    }

  private String resolvedSubFlow(JobParameters jobParameters, ExecutionContext executionContext) {
    String stepSubFlow = ScenarioHierarchyLoggingSupport.stringValue(executionContext, ScenarioHierarchyLoggingSupport.STEP_SUB_FLOW_NAME_KEY);
    return stepSubFlow.isBlank() ? stringParameter(jobParameters, "subFlow") : stepSubFlow;
  }

    private JobParameters jobParameters(StepExecution stepExecution) {
        JobExecution jobExecution = stepExecution == null ? null : stepExecution.getJobExecution();
        return jobExecution == null ? null : jobExecution.getJobParameters();
    }

    private String stringParameter(JobParameters jobParameters, String parameterName) {
        return jobParameters == null ? "" : jobParameters.getString(parameterName, "");
    }
}

