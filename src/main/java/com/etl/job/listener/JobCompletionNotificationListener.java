package com.etl.job.listener;

import com.etl.exception.EtlExceptionDetails;
import com.etl.logging.RunLoggingContext;
import com.etl.runtime.job.JobHierarchyLoggingSupport;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.runtime.job.JobSubFlowDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

	/**
	 * JobCompletionNotificationListener is a listener that provides notifications
	 * about the completion of a job. It implements the JobExecutionListener
	 * interface and is used to log the start and end of a job, as well as its
	 * status.
	 */

	private static final Logger logger = LoggerFactory.getLogger(JobCompletionNotificationListener.class);
	private final JobRuntimeDescriptor jobRuntimeDescriptor;

	public JobCompletionNotificationListener() {
		this(null);
	}

	@Autowired
	public JobCompletionNotificationListener(@Nullable JobRuntimeDescriptor jobRuntimeDescriptor) {
		this.jobRuntimeDescriptor = jobRuntimeDescriptor;
	}

	@Override
	public void beforeJob(JobExecution jobExecution) {
		JobParameters jobParameters = jobExecution.getJobParameters();
		RunLoggingContext.put(RunLoggingContext.SCENARIO, jobParameters.getString("scenario", "unknown-scenario"));
		RunLoggingContext.put(RunLoggingContext.SCENARIO_LOG_KEY, jobParameters.getString("scenarioLogKey", ""));
		RunLoggingContext.put(RunLoggingContext.RUN_CORRELATION_ID, jobParameters.getString("runCorrelationId", ""));
		RunLoggingContext.put(RunLoggingContext.RUN_MODE, jobParameters.getString("runMode", ""));
		RunLoggingContext.put(RunLoggingContext.JOB_CONFIG_PATH, jobParameters.getString("jobConfigPath", ""));
		RunLoggingContext.put(RunLoggingContext.MAIN_FLOW, jobParameters.getString("mainFlow", ""));
		RunLoggingContext.put(RunLoggingContext.SUB_FLOW, jobParameters.getString("subFlow", ""));
		RunLoggingContext.put(RunLoggingContext.RECOVERY_POLICY, jobParameters.getString("recoveryPolicy", ""));
		RunLoggingContext.put(RunLoggingContext.JOB_NAME, jobExecution.getJobInstance().getJobName());
		RunLoggingContext.put(RunLoggingContext.JOB_EXECUTION_ID, String.valueOf(jobExecution.getId()));

		logger.info("RUN_EVENT event=job_started scenario={} mainFlow={} subFlow={} recoveryPolicy={} jobName={} jobExecutionId={} startTime={} runMode={} jobConfigPath={}",
				jobParameters.getString("scenario", "unknown-scenario"),
				jobParameters.getString("mainFlow", ""),
				jobParameters.getString("subFlow", ""),
				jobParameters.getString("recoveryPolicy", ""),
				jobExecution.getJobInstance().getJobName(),
				jobExecution.getId(),
				jobExecution.getStartTime(),
				jobParameters.getString("runMode", ""),
				jobParameters.getString("jobConfigPath", ""));
		logJobHierarchyPlan(jobExecution, jobParameters);
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		try {
			LocalDateTime startTime = jobExecution.getStartTime();
			LocalDateTime endTime = jobExecution.getEndTime();
			Long durationSeconds = startTime != null && endTime != null
					? Duration.between(startTime, endTime).getSeconds()
					: null;
			logger.info("RUN_SUMMARY event=run_summary scenario={} mainFlow={} subFlow={} recoveryPolicy={} jobName={} jobExecutionId={} status={} startTime={} endTime={} durationSeconds={} failureCount={}",
					mdcValueOrDefault(RunLoggingContext.SCENARIO, "unknown-scenario"),
					mdcValueOrDefault(RunLoggingContext.MAIN_FLOW, ""),
					mdcValueOrDefault(RunLoggingContext.SUB_FLOW, ""),
					mdcValueOrDefault(RunLoggingContext.RECOVERY_POLICY, ""),
					jobExecution.getJobInstance().getJobName(),
					jobExecution.getId(),
					jobExecution.getStatus(),
					startTime,
					endTime,
					durationSeconds == null ? "unknown" : durationSeconds,
					jobExecution.getAllFailureExceptions().size());
			logSubFlowEvidence(jobExecution);

			if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
				logger.info("Job completed successfully in {} seconds.", durationSeconds == null ? "unknown" : durationSeconds);
			} else if (jobExecution.getStatus() == BatchStatus.FAILED) {
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
		} finally {
			RunLoggingContext.clearJobScope();
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