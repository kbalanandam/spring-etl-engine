package com.etl.runner;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * EtlJobRunner is responsible for executing the ETL job when the application
 * starts. It implements CommandLineRunner to run the job with specific
 * parameters.
 */
@Component
public class EtlJobRunner implements CommandLineRunner {

	private static Logger logger = LoggerFactory.getLogger(EtlJobRunner.class);

	private final JobLauncher jobLauncher;
	private final Job etlJob;

	public EtlJobRunner(JobLauncher jobLauncher, Job etlJob) {
		this.jobLauncher = jobLauncher;
		this.etlJob = etlJob;
	}

	@Override
	public void run(String... args) throws Exception {
		JobParameters jobParameters = new JobParametersBuilder().addDate("runDate", new Date()).toJobParameters();

		try {
			logger.info("Starting ETL Job...");
			jobLauncher.run(etlJob, jobParameters);
			logger.info("ETL Job Finished.");
		} catch (Exception e) {
			logger.error("ETL Job failed: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
