package com.etl.runner;

import com.etl.config.RunConfigurationMetadata;
import com.etl.exception.EtlExceptionDetails;
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
import java.util.List;
import java.util.Date;

/**
 * EtlJobRunner is responsible for executing the ETL job when the application
 * starts. It implements CommandLineRunner to run the job with specific
 * parameters.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>Keep this class as the current launch entry while the next runtime path is
 * introduced, but avoid building long-term architecture decisions directly into it.
 * Prefer delegating future launch behavior to new generation-first runtime components.</p>
 */
@Component
public class EtlJobRunner implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(EtlJobRunner.class);

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
		// Capture one run-level logging identity before launching Spring Batch so startup,
		// step, and summary evidence all share the same scenario/run correlation fields.
		String scenarioName = sanitizeForLogging(runConfigurationMetadata.scenarioName());
		String scenarioLogKey = RunLoggingContext.buildScenarioLogKey(scenarioName, LocalDate.now());
		String runCorrelationId = RunLoggingContext.buildRunCorrelationId(LocalDateTime.now());
		String runMode = runConfigurationMetadata.demoFallbackMode() ? "demo-fallback" : "explicit-job";

		JobParameters jobParameters = new JobParametersBuilder()
				// Job parameters mirror the resolved runtime metadata so the launched job, logs,
				// and job-execution records all describe the same selected scenario contract.
				.addDate("runDate", new Date())
				.addString("scenario", runConfigurationMetadata.scenarioName())
				.addString("scenarioLogKey", scenarioLogKey)
				.addString("jobConfigPath", defaultString(runConfigurationMetadata.jobConfigPath()))
				.addString("mainFlow", defaultString(runConfigurationMetadata.mainFlowName()))
				.addString("subFlow", defaultString(runConfigurationMetadata.subFlowName()))
				.addString("recoveryPolicy", runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue())
				.addString("runCorrelationId", runCorrelationId)
				.addString("runMode", runMode)
				.toJobParameters();

		RunLoggingContext.put(RunLoggingContext.SCENARIO, scenarioName);
		RunLoggingContext.put(RunLoggingContext.SCENARIO_LOG_KEY, scenarioLogKey);
		RunLoggingContext.put(RunLoggingContext.RUN_CORRELATION_ID, runCorrelationId);
		RunLoggingContext.put(RunLoggingContext.RUN_MODE, runMode);
		RunLoggingContext.put(RunLoggingContext.JOB_CONFIG_PATH, defaultString(runConfigurationMetadata.jobConfigPath()));
		RunLoggingContext.put(RunLoggingContext.MAIN_FLOW, defaultString(runConfigurationMetadata.mainFlowName()));
		RunLoggingContext.put(RunLoggingContext.SUB_FLOW, defaultString(runConfigurationMetadata.subFlowName()));
		RunLoggingContext.put(RunLoggingContext.RECOVERY_POLICY,
				runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue());

        try {
	            logger.info("RUN_EVENT event=run_requested scenario={} mainFlow={} subFlow={} recoveryPolicy={} runMode={} jobConfigPath={} plannedStepCount={} plannedSteps={}",
	                    runConfigurationMetadata.scenarioName(),
	                    defaultString(runConfigurationMetadata.mainFlowName()),
	                    defaultString(runConfigurationMetadata.subFlowName()),
	                    runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
	                    runMode,
	                    defaultString(runConfigurationMetadata.jobConfigPath()),
	                    runConfigurationMetadata.steps().size(),
	                    formatPlannedSteps(runConfigurationMetadata.steps()));
            jobLauncher.run(etlJob, jobParameters);
	            logger.info("RUN_EVENT event=run_finished scenario={} mainFlow={} subFlow={} recoveryPolicy={} runMode={} plannedStepCount={}",
	                    runConfigurationMetadata.scenarioName(),
	                    defaultString(runConfigurationMetadata.mainFlowName()),
	                    defaultString(runConfigurationMetadata.subFlowName()),
	                    runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
	                    runMode,
	                    runConfigurationMetadata.steps().size());
        } catch (Exception e) {
	            logger.error("RUN_EVENT event=run_failed scenario={} mainFlow={} subFlow={} recoveryPolicy={} runMode={} failureCategory={} exceptionType={} rootCause={}",
	                    runConfigurationMetadata.scenarioName(),
	                    defaultString(runConfigurationMetadata.mainFlowName()),
	                    defaultString(runConfigurationMetadata.subFlowName()),
	                    runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
	                    runMode,
	                    EtlExceptionDetails.categoryValueOf(e),
	                    EtlExceptionDetails.exceptionType(e),
	                    EtlExceptionDetails.rootCauseMessage(e),
	                    e);
	            throw new JobExecutionException(
	                    "ETL job launch failed failureCategory=" + EtlExceptionDetails.categoryValueOf(e)
	                            + " runDate=" + jobParameters.getDate("runDate"),
	                    e
	            );
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

	private String formatPlannedSteps(List<com.etl.config.job.JobConfig.JobStepConfig> steps) {
		return steps.stream()
				.map(step -> step.isCustomStep()
						? step.getName() + ":custom(" + (step.getCustom() == null ? "" : step.getCustom().getType()) + ")"
						: step.getName() + ":" + step.getSource() + "->" + step.getTarget())
				.reduce((left, right) -> left + "," + right)
				.orElse("none");
	}
}
