package com.etl.job.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
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

        logger.info("Job started: {} at {}", jobExecution.getJobInstance().getJobName(), jobExecution.getStartTime());
	}

	@Override
	public void afterJob(JobExecution jobExecution) {

		LocalDateTime startTime = jobExecution.getStartTime();
		LocalDateTime endTime = jobExecution.getEndTime();
		if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
			assert startTime != null;
			logger.info("!!! JOB FINISHED! The job {} has completed successfully.Time taken: {} Seconds.", jobExecution.getJobInstance().getJobName(), Duration.between(startTime, endTime).getSeconds());
		} else if (jobExecution.getStatus() == BatchStatus.FAILED) {
			logger.error("!!! JOB FAILED! The job {} has failed.", jobExecution.getJobInstance().getJobName());
			jobExecution.getAllFailureExceptions().forEach(Throwable::printStackTrace);
		} else {
			logger.info("!!! JOB STATUS: {}", jobExecution.getStatus());
		}

	}
}