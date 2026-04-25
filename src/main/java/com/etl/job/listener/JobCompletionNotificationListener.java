package com.etl.job.listener;

import com.etl.logging.RunLoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

	/**
	 * JobCompletionNotificationListener is a listener that provides notifications
	 * about the completion of a job. It implements the JobExecutionListener
	 * interface and is used to log the start and end of a job, as well as its
	 * status.
	 */

	private static final Logger logger = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

	@Override
	public void beforeJob(JobExecution jobExecution) {
		JobParameters jobParameters = jobExecution.getJobParameters();
		RunLoggingContext.put(RunLoggingContext.SCENARIO, jobParameters.getString("scenario", "unknown-scenario"));
		RunLoggingContext.put(RunLoggingContext.SCENARIO_LOG_KEY, jobParameters.getString("scenarioLogKey", ""));
		RunLoggingContext.put(RunLoggingContext.RUN_CORRELATION_ID, jobParameters.getString("runCorrelationId", ""));
		RunLoggingContext.put(RunLoggingContext.RUN_MODE, jobParameters.getString("runMode", ""));
		RunLoggingContext.put(RunLoggingContext.JOB_CONFIG_PATH, jobParameters.getString("jobConfigPath", ""));
		RunLoggingContext.put(RunLoggingContext.JOB_NAME, jobExecution.getJobInstance().getJobName());
		RunLoggingContext.put(RunLoggingContext.JOB_EXECUTION_ID, String.valueOf(jobExecution.getId()));

		logger.info("RUN_EVENT event=job_started scenario={} jobName={} jobExecutionId={} startTime={} runMode={} jobConfigPath={}",
				jobParameters.getString("scenario", "unknown-scenario"),
				jobExecution.getJobInstance().getJobName(),
				jobExecution.getId(),
				jobExecution.getStartTime(),
				jobParameters.getString("runMode", ""),
				jobParameters.getString("jobConfigPath", ""));
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		try {
			LocalDateTime startTime = jobExecution.getStartTime();
			LocalDateTime endTime = jobExecution.getEndTime();
			Long durationSeconds = startTime != null && endTime != null
					? Duration.between(startTime, endTime).getSeconds()
					: null;
			logger.info("RUN_SUMMARY event=run_summary scenario={} jobName={} jobExecutionId={} status={} startTime={} endTime={} durationSeconds={} failureCount={}",
					mdcValueOrDefault(RunLoggingContext.SCENARIO, "unknown-scenario"),
					jobExecution.getJobInstance().getJobName(),
					jobExecution.getId(),
					jobExecution.getStatus(),
					startTime,
					endTime,
					durationSeconds == null ? "unknown" : durationSeconds,
					jobExecution.getAllFailureExceptions().size());

			if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
				logger.info("Job completed successfully in {} seconds.", durationSeconds == null ? "unknown" : durationSeconds);
			} else if (jobExecution.getStatus() == BatchStatus.FAILED) {
				logger.error("Job failed after {} seconds.", durationSeconds == null ? "unknown" : durationSeconds);
				jobExecution.getAllFailureExceptions().forEach(
						failure -> logger.error("Job failure detail: {}", failure.getMessage(), failure)
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
}