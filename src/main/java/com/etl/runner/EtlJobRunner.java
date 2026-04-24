package com.etl.runner;

import com.etl.config.RunConfigurationMetadata;
import com.etl.logging.RunLoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * EtlJobRunner is responsible for executing the ETL job when the application
 * starts. It implements CommandLineRunner to run the job with specific
 * parameters.
 */
@Component
public class EtlJobRunner implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(EtlJobRunner.class);
	private static final DateTimeFormatter LOG_FILE_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private final JobLauncher jobLauncher;
	private final Job etlJob;
	private final RunConfigurationMetadata runConfigurationMetadata;

	public EtlJobRunner(JobLauncher jobLauncher, Job etlJob, RunConfigurationMetadata runConfigurationMetadata) {
		this.jobLauncher = jobLauncher;
		this.etlJob = etlJob;
		this.runConfigurationMetadata = runConfigurationMetadata;
	}

	@Override
	public void run(String... args) throws Exception {
		String scenarioName = sanitizeForLogging(runConfigurationMetadata.scenarioName());
		String scenarioLogKey = LOG_FILE_DATE_FORMATTER.format(LocalDate.now()) + "/" + scenarioName;
		String runCorrelationId = RUN_ID_FORMATTER.format(LocalDateTime.now());
		String runMode = runConfigurationMetadata.demoFallbackMode() ? "demo-fallback" : "explicit-job";

		JobParameters jobParameters = new JobParametersBuilder()
				.addDate("runDate", new Date())
				.addString("scenario", runConfigurationMetadata.scenarioName())
				.addString("scenarioLogKey", scenarioLogKey)
				.addString("jobConfigPath", defaultString(runConfigurationMetadata.jobConfigPath()))
				.addString("runCorrelationId", runCorrelationId)
				.addString("runMode", runMode)
				.toJobParameters();

		RunLoggingContext.put(RunLoggingContext.SCENARIO, scenarioName);
		RunLoggingContext.put(RunLoggingContext.SCENARIO_LOG_KEY, scenarioLogKey);
		RunLoggingContext.put(RunLoggingContext.RUN_CORRELATION_ID, runCorrelationId);
		RunLoggingContext.put(RunLoggingContext.RUN_MODE, runMode);
		RunLoggingContext.put(RunLoggingContext.JOB_CONFIG_PATH, defaultString(runConfigurationMetadata.jobConfigPath()));

        try {
	            logger.info("Starting ETL job for scenario '{}' in {} mode.", runConfigurationMetadata.scenarioName(), runMode);
            jobLauncher.run(etlJob, jobParameters);
	            logger.info("ETL job finished for scenario '{}'.", runConfigurationMetadata.scenarioName());
        } catch (Exception e) {
            throw new JobExecutionException("ETL Job failed for date: " + jobParameters.getDate("runDate"), e);
		} finally {
			RunLoggingContext.clearAll();
        }
	}

	private String sanitizeForLogging(String value) {
		String resolved = defaultString(value).trim();
		if (resolved.isBlank()) {
			return "unknown-scenario";
		}
		return resolved.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private String defaultString(String value) {
		return value == null ? "" : value;
	}
}
