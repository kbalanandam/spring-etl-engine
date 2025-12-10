package com.etl.job.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

	/**
	 * JobCompletionNotificationListener is a listener that provides notifications
	 * about the completion of a job. It implements the JobExecutionListener
	 * interface and is used to log the start and end of a job, as well as its
	 * status.
	 */

	private static final Logger logger = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

	// Inject the application context
	public JobCompletionNotificationListener(ConfigurableApplicationContext context) {
	}

	@Override
	public void beforeJob(JobExecution jobExecution) {

		logger.info(
				"Job started: " + jobExecution.getJobInstance().getJobName() + " at " + jobExecution.getStartTime());
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		LocalDateTime startTime = jobExecution.getStartTime();
		LocalDateTime endTime = jobExecution.getEndTime();
		if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
			logger.info("!!! JOB FINISHED! The job " + jobExecution.getJobInstance().getJobName()
					+ " has completed successfully." + "Time taken: "
					+ Duration.between(startTime, endTime).getSeconds() + " Seconds.");
		} else if (jobExecution.getStatus() == BatchStatus.FAILED) {
			logger.error("!!! JOB FAILED! The job " + jobExecution.getJobInstance().getJobName() + " has failed.");
			// Optionally print failure exceptions
			jobExecution.getAllFailureExceptions().forEach(e -> e.printStackTrace());
		} else {
			logger.info("!!! JOB STATUS: " + jobExecution.getStatus());
		}

	}
}