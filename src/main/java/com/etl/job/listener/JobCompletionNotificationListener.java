package com.etl.job.listener;

import com.etl.config.RunConfigurationMetadata;
import com.etl.config.job.JobConfig;
import com.etl.exception.EtlExceptionDetails;
import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.monitoring.RunSummaryRegistry;
import com.etl.controlplane.monitoring.RunSummaryView;
import com.etl.logging.RunLoggingContext;
import com.etl.runtime.job.JobHierarchyLoggingSupport;
import com.etl.runtime.job.JobRunCountRollup;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.runtime.job.JobSubFlowDescriptor;
import com.etl.step.CustomStepFailureFinalizer;
import com.etl.step.DynamicCustomStepFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Emits run-level start, summary, failure, and flow-hierarchy evidence for one job execution.
 *
 * <p>This listener is the run-level counterpart to {@link StepLoggingContextListener}. It
 * establishes job-scope logging fields before execution, emits main-flow/subflow plan evidence,
 * and then publishes run-summary plus failure diagnostics after the job completes.</p>
 */
@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

	private static final Logger logger = LoggerFactory.getLogger(JobCompletionNotificationListener.class);
	private final JobRuntimeDescriptor jobRuntimeDescriptor;
	private final RunConfigurationMetadata runConfigurationMetadata;
	private final DynamicCustomStepFactory customStepFactory;
	private final RunSummaryRegistry runSummaryRegistry;
	private final RunSummaryReadModelService runSummaryReadModelService;
	private final boolean syncJustFinishedRunFromLog;

	public JobCompletionNotificationListener() {
		this(null, null, null, null, null, true);
	}

	public JobCompletionNotificationListener(@Nullable JobRuntimeDescriptor jobRuntimeDescriptor) {
		this(jobRuntimeDescriptor, null, null, null, null, true);
	}

	public JobCompletionNotificationListener(@Nullable JobRuntimeDescriptor jobRuntimeDescriptor,
	                                        @Nullable RunConfigurationMetadata runConfigurationMetadata,
	                                        @Nullable DynamicCustomStepFactory customStepFactory) {
		this(jobRuntimeDescriptor, runConfigurationMetadata, customStepFactory, null, null, true);
	}

	public JobCompletionNotificationListener(@Nullable JobRuntimeDescriptor jobRuntimeDescriptor,
	                                        @Nullable RunConfigurationMetadata runConfigurationMetadata,
	                                        @Nullable DynamicCustomStepFactory customStepFactory,
	                                        @Nullable RunSummaryRegistry runSummaryRegistry) {
		this(jobRuntimeDescriptor, runConfigurationMetadata, customStepFactory, runSummaryRegistry, null, true);
	}

	@Autowired
	public JobCompletionNotificationListener(@Nullable JobRuntimeDescriptor jobRuntimeDescriptor,
	                                        @Nullable RunConfigurationMetadata runConfigurationMetadata,
	                                        @Nullable DynamicCustomStepFactory customStepFactory,
	                                        @Nullable RunSummaryRegistry runSummaryRegistry,
	                                        @Nullable RunSummaryReadModelService runSummaryReadModelService,
	                                        @Value("${controlplane.runs.sync-just-finished-job-from-log:true}") boolean syncJustFinishedRunFromLog) {
		this.jobRuntimeDescriptor = jobRuntimeDescriptor;
		this.runConfigurationMetadata = runConfigurationMetadata;
		this.customStepFactory = customStepFactory;
		this.runSummaryRegistry = runSummaryRegistry;
		this.runSummaryReadModelService = runSummaryReadModelService;
		this.syncJustFinishedRunFromLog = syncJustFinishedRunFromLog;
	}

	@Override
	public void beforeJob(JobExecution jobExecution) {
		// Seed the run-level MDC/logging context once so all later job and step events share the
		// same scenario, run-correlation, and flow identifiers.
		JobParameters jobParameters = jobExecution.getJobParameters();
		String scenario = RunLoggingContext.sanitizeScenarioName(jobParameters.getString("scenario", "unknown-scenario"));
		LocalDate logDate = jobExecution.getStartTime() == null ? LocalDate.now() : jobExecution.getStartTime().toLocalDate();
		String scenarioLogKey = RunLoggingContext.buildScenarioLogKey(scenario, logDate);
		String runCorrelationId = RunLoggingContext.buildRunCorrelationId(LocalDateTime.now());
		RunLoggingContext.put(RunLoggingContext.SCENARIO, scenario);
		RunLoggingContext.put(RunLoggingContext.SCENARIO_LOG_KEY, scenarioLogKey);
		RunLoggingContext.put(RunLoggingContext.RUN_CORRELATION_ID, runCorrelationId);
		RunLoggingContext.put(RunLoggingContext.RUN_MODE, jobParameters.getString("runMode", ""));
		RunLoggingContext.put(RunLoggingContext.JOB_CONFIG_PATH, jobParameters.getString("jobConfigPath", ""));
		RunLoggingContext.put(RunLoggingContext.MAIN_FLOW, jobParameters.getString("mainFlow", ""));
		RunLoggingContext.put(RunLoggingContext.SUB_FLOW, jobParameters.getString("subFlow", ""));
		RunLoggingContext.put(RunLoggingContext.RECOVERY_POLICY, jobParameters.getString("recoveryPolicy", ""));
		RunLoggingContext.put(RunLoggingContext.TRIGGER_EVENT_ID, jobParameters.getString("triggerEventId", ""));
		RunLoggingContext.put(RunLoggingContext.JOB_NAME, jobExecution.getJobInstance().getJobName());
		RunLoggingContext.put(RunLoggingContext.JOB_EXECUTION_ID, String.valueOf(jobExecution.getId()));

		logger.info("RUN_EVENT event=job_started scenario={} mainFlow={} subFlow={} recoveryPolicy={} jobName={} jobExecutionId={} startTime={} runMode={} jobConfigPath={}",
				scenario,
				jobParameters.getString("mainFlow", ""),
				jobParameters.getString("subFlow", ""),
				jobParameters.getString("recoveryPolicy", ""),
				jobExecution.getJobInstance().getJobName(),
				jobExecution.getId(),
				jobExecution.getStartTime(),
				jobParameters.getString("runMode", ""),
				jobParameters.getString("jobConfigPath", ""));
		logJobHierarchyPlan(jobExecution, jobParameters);
		persistRunSnapshot(jobExecution, "STARTED", null, null, null, null);
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		try {
			// Roll up executed-step evidence into one operator-facing run summary so published output,
			// handoff counts, and reject counts can be reconciled at run scope.
			LocalDateTime startTime = jobExecution.getStartTime();
			LocalDateTime endTime = jobExecution.getEndTime();
			Long durationSeconds = startTime != null && endTime != null
					? Duration.between(startTime, endTime).getSeconds()
					: null;
			JobRunCountRollup countRollup = JobRunCountRollup.calculate(jobExecution, jobRuntimeDescriptor);
			persistRunSnapshot(
					jobExecution,
					jobExecution.getStatus() == null ? "UNKNOWN" : jobExecution.getStatus().name(),
					durationSeconds,
					countRollup.sourceCount(),
					countRollup.writtenCount(),
					countRollup.rejectedCount());
			persistStepSnapshots(jobExecution);
			logger.info("RUN_SUMMARY event=run_summary scenario={} mainFlow={} subFlow={} runMode={} recoveryPolicy={} triggerEventId={} jobName={} jobExecutionId={} status={} startTime={} endTime={} durationSeconds={} sourceCount={} writtenCount={} rejectedCount={} handoffReadCount={} handoffWriteCount={} executedStepCount={} rollupMode={} failureCount={}",
					mdcValueOrDefault(RunLoggingContext.SCENARIO, "unknown-scenario"),
					mdcValueOrDefault(RunLoggingContext.MAIN_FLOW, ""),
					mdcValueOrDefault(RunLoggingContext.SUB_FLOW, ""),
					mdcValueOrDefault(RunLoggingContext.RUN_MODE, ""),
					mdcValueOrDefault(RunLoggingContext.RECOVERY_POLICY, ""),
					mdcValueOrDefault(RunLoggingContext.TRIGGER_EVENT_ID, ""),
					jobExecution.getJobInstance().getJobName(),
					jobExecution.getId(),
					jobExecution.getStatus(),
					startTime,
					endTime,
					durationSeconds == null ? "unknown" : durationSeconds,
					countRollup.sourceCount(),
					countRollup.writtenCount(),
					countRollup.rejectedCount(),
					countRollup.handoffReadCount(),
					countRollup.handoffWriteCount(),
					countRollup.executedStepCount(),
					countRollup.rollupMode(),
					jobExecution.getAllFailureExceptions().size());
			logSubFlowEvidence(jobExecution);

			if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
				logger.info("Job completed successfully in {} seconds.", durationSeconds == null ? "unknown" : durationSeconds);
			} else if (jobExecution.getStatus() == BatchStatus.FAILED) {
				invokeCustomFailureFinalizers(jobExecution);
				logger.error("Job failed after {} seconds.", durationSeconds == null ? "unknown" : durationSeconds);
				jobExecution.getAllFailureExceptions().forEach(
						failure -> logger.error(
								"JOB_FAILURE event=job_failure scenario={} mainFlow={} subFlow={} recoveryPolicy={} failureCategory={} exceptionType={} rootCause={} message={}",
								mdcValueOrDefault(RunLoggingContext.SCENARIO, "unknown-scenario"),
								mdcValueOrDefault(RunLoggingContext.MAIN_FLOW, ""),
								mdcValueOrDefault(RunLoggingContext.SUB_FLOW, ""),
								mdcValueOrDefault(RunLoggingContext.RECOVERY_POLICY, ""),
								EtlExceptionDetails.categoryValueOf(failure),
								EtlExceptionDetails.exceptionType(failure),
								EtlExceptionDetails.rootCauseMessage(failure),
								failure.getMessage(),
								failure)
				);
			} else {
				logger.info("Job finished with status {} after {} seconds.", jobExecution.getStatus(), durationSeconds == null ? "unknown" : durationSeconds);
			}
			syncJustFinishedRunFromLog(jobExecution);
		} finally {
			RunLoggingContext.clearJobScope();
		}

	}

	private void persistRunSnapshot(JobExecution jobExecution,
	                               String status,
	                               Long durationSeconds,
	                               Long sourceCount,
	                               Long writtenCount,
	                               Long rejectedCount) {
		if (runSummaryRegistry == null || jobExecution == null || jobExecution.getId() == null) {
			return;
		}
		JobParameters jobParameters = jobExecution.getJobParameters();
		String scenario = RunLoggingContext.sanitizeScenarioName(jobParameters.getString("scenario", "unknown-scenario"));
		LocalDate logDate = jobExecution.getStartTime() == null ? LocalDate.now() : jobExecution.getStartTime().toLocalDate();
		String logPath = "logs/" + logDate + "/" + scenario + ".log";
		try {
			runSummaryRegistry.upsert(new RunSummaryView(
					scenario,
					jobExecution.getId(),
					(status == null || status.isBlank()) ? "UNKNOWN" : status,
					jobExecution.getStartTime(),
					jobExecution.getEndTime(),
					durationSeconds,
					sourceCount,
					writtenCount,
					rejectedCount,
					nullIfBlank(jobParameters.getString("runMode", "")),
					nullIfBlank(jobParameters.getString("recoveryPolicy", "")),
					nullIfBlank(jobParameters.getString("triggerEventId", "")),
					null,
					logPath
			));
		} catch (RuntimeException ex) {
			logger.debug("RUN_EVENT event=direct_persistence_skipped reason={} jobExecutionId={}", ex.getMessage(), jobExecution.getId());
		}
	}

	private String nullIfBlank(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value;
	}

	private void syncJustFinishedRunFromLog(JobExecution jobExecution) {
		if (!syncJustFinishedRunFromLog || runSummaryReadModelService == null || jobExecution == null || jobExecution.getId() == null) {
			return;
		}
		JobParameters jobParameters = jobExecution.getJobParameters();
		String scenario = RunLoggingContext.sanitizeScenarioName(jobParameters.getString("scenario", "unknown-scenario"));
		LocalDate logDate = jobExecution.getStartTime() == null ? LocalDate.now() : jobExecution.getStartTime().toLocalDate();
		for (int attempt = 0; attempt < 3; attempt++) {
			boolean synced = runSummaryReadModelService.syncRunFromScenarioLog(scenario, logDate, jobExecution.getId());
			if (synced) {
				return;
			}
			if (attempt < 2) {
				try {
					Thread.sleep(75L);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	private void persistStepSnapshots(JobExecution jobExecution) {
		if (runSummaryRegistry == null || jobExecution == null || jobExecution.getId() == null) {
			return;
		}
		try {
			runSummaryRegistry.upsertStepSnapshots(jobExecution.getId(), jobExecution.getStepExecutions());
		} catch (RuntimeException ex) {
			logger.debug("RUN_EVENT event=direct_step_persistence_skipped reason={} jobExecutionId={}", ex.getMessage(), jobExecution.getId());
		}
	}

	private void invokeCustomFailureFinalizers(JobExecution jobExecution) {
		if (customStepFactory == null || runConfigurationMetadata == null || runConfigurationMetadata.steps() == null) {
			return;
		}
		for (JobConfig.JobStepConfig configuredStep : runConfigurationMetadata.steps()) {
			if (configuredStep == null || !configuredStep.isCustomStep() || configuredStep.getCustom() == null) {
				continue;
			}
			String stepName = configuredStep.getName() == null ? "" : configuredStep.getName().trim();
			if (stepName.isBlank()) {
				continue;
			}
			try {
				CustomStepFailureFinalizer finalizer = customStepFactory.getFailureFinalizer(stepName, configuredStep.getCustom());
				finalizer.onFailure(jobExecution, stepName, configuredStep.getCustom());
				logger.info("RUN_EVENT event=custom_step_failure_finalized scenario={} mainFlow={} subFlow={} recoveryPolicy={} stepName={} customType={} jobExecutionId={}",
						mdcValueOrDefault(RunLoggingContext.SCENARIO, "unknown-scenario"),
						mdcValueOrDefault(RunLoggingContext.MAIN_FLOW, ""),
						mdcValueOrDefault(RunLoggingContext.SUB_FLOW, ""),
						mdcValueOrDefault(RunLoggingContext.RECOVERY_POLICY, ""),
						stepName,
						configuredStep.getCustom().getType() == null ? "" : configuredStep.getCustom().getType(),
						jobExecution.getId());
			} catch (Exception e) {
				logger.warn("RUN_EVENT event=custom_step_failure_finalizer_failed scenario={} mainFlow={} subFlow={} recoveryPolicy={} stepName={} customType={} jobExecutionId={} message={}",
						mdcValueOrDefault(RunLoggingContext.SCENARIO, "unknown-scenario"),
						mdcValueOrDefault(RunLoggingContext.MAIN_FLOW, ""),
						mdcValueOrDefault(RunLoggingContext.SUB_FLOW, ""),
						mdcValueOrDefault(RunLoggingContext.RECOVERY_POLICY, ""),
						stepName,
						configuredStep.getCustom().getType() == null ? "" : configuredStep.getCustom().getType(),
						jobExecution.getId(),
						e.getMessage(),
						e);
			}
		}
	}

	private String mdcValueOrDefault(String key, String defaultValue) {
		String value = MDC.get(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private void logJobHierarchyPlan(JobExecution jobExecution, JobParameters jobParameters) {
		if (jobRuntimeDescriptor == null) {
			return;
		}
		// Plan events describe the synthesized MainFlow/SubFlow view of the selected flat step plan.
		logger.info("MAIN_FLOW_PLAN event=main_flow_plan scenario={} mainFlow={} subFlow={} recoveryPolicy={} jobExecutionId={} plannedSubFlowCount={} plannedStepCount={} visibleSubFlows={} handoffAliases={} supportsCrossSubFlowHandshake={} supportsBlockingOnUpstreamFailure={} summary={}",
				jobParameters.getString("scenario", "unknown-scenario"),
				jobParameters.getString("mainFlow", ""),
				jobParameters.getString("subFlow", ""),
				jobParameters.getString("recoveryPolicy", ""),
				jobExecution.getId(),
				jobRuntimeDescriptor.subFlowCount(),
				jobRuntimeDescriptor.stepCount(),
				JobHierarchyLoggingSupport.formatList(jobRuntimeDescriptor.mainFlowContext().visibleSubFlowNames()),
				JobHierarchyLoggingSupport.formatList(jobRuntimeDescriptor.mainFlowContext().handoffAliases()),
				jobRuntimeDescriptor.mainFlowContext().supportsCrossSubFlowHandshake(),
				jobRuntimeDescriptor.mainFlowContext().supportsBlockingOnUpstreamFailure(),
				jobRuntimeDescriptor.mainFlowContext().summary());
		for (JobSubFlowDescriptor subFlowDescriptor : jobRuntimeDescriptor.subFlows()) {
			logger.info("SUBFLOW_PLAN event=subflow_plan scenario={} mainFlow={} subFlow={} subFlowOrder={} initialStatus={} dependsOnSubFlows={} consumesHandoffAliases={} producesHandoffAliases={} stepNames={} controlSummary={} summary={}",
					jobParameters.getString("scenario", "unknown-scenario"),
					jobParameters.getString("mainFlow", ""),
					subFlowDescriptor.subFlowName(),
					subFlowDescriptor.subFlowOrder(),
					subFlowDescriptor.initialStatus(),
					JobHierarchyLoggingSupport.formatList(subFlowDescriptor.dependsOnSubFlowNames()),
					JobHierarchyLoggingSupport.formatList(subFlowDescriptor.consumesHandoffAliases()),
					JobHierarchyLoggingSupport.formatList(subFlowDescriptor.producesHandoffAliases()),
					JobHierarchyLoggingSupport.formatList(subFlowDescriptor.stepNames()),
					subFlowDescriptor.control().summary(),
					subFlowDescriptor.summary());
		}
	}

	private void logSubFlowEvidence(JobExecution jobExecution) {
		if (jobRuntimeDescriptor == null) {
			return;
		}
		// Subflow summaries are derived from executed steps plus runtime-descriptor control metadata,
		// not from a separate execution engine.
		List<JobHierarchyLoggingSupport.SubFlowStatusEvidence> evidence = JobHierarchyLoggingSupport.evaluateSubFlowEvidence(
				jobRuntimeDescriptor,
				jobExecution.getStepExecutions(),
				jobExecution.getStatus());
		for (JobHierarchyLoggingSupport.SubFlowStatusEvidence subFlowEvidence : evidence) {
			JobSubFlowDescriptor subFlowDescriptor = subFlowEvidence.subFlowDescriptor();
			logger.info("SUBFLOW_SUMMARY event=subflow_summary scenario={} mainFlow={} subFlow={} recoveryPolicy={} jobExecutionId={} subFlowOrder={} initialStatus={} status={} dependsOnSubFlows={} consumesHandoffAliases={} producesHandoffAliases={} stepNames={} blockedReason={} controlSummary={} summary={}",
					mdcValueOrDefault(RunLoggingContext.SCENARIO, "unknown-scenario"),
					mdcValueOrDefault(RunLoggingContext.MAIN_FLOW, ""),
					subFlowDescriptor.subFlowName(),
					mdcValueOrDefault(RunLoggingContext.RECOVERY_POLICY, ""),
					jobExecution.getId(),
					subFlowDescriptor.subFlowOrder(),
					subFlowDescriptor.initialStatus(),
					subFlowEvidence.observedStatus(),
					JobHierarchyLoggingSupport.formatList(subFlowDescriptor.dependsOnSubFlowNames()),
					JobHierarchyLoggingSupport.formatList(subFlowDescriptor.consumesHandoffAliases()),
					JobHierarchyLoggingSupport.formatList(subFlowDescriptor.producesHandoffAliases()),
					JobHierarchyLoggingSupport.formatList(subFlowDescriptor.stepNames()),
					subFlowEvidence.blockedReason().isBlank() ? "none" : subFlowEvidence.blockedReason(),
					subFlowDescriptor.control().summary(),
					subFlowDescriptor.summary());
		}
	}
}