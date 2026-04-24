package com.etl.logging;

import org.slf4j.MDC;

/**
 * Central MDC keys and helpers for scenario/job-run aware logging.
 */
public final class RunLoggingContext {

	public static final String SCENARIO = "scenario";
	public static final String SCENARIO_LOG_KEY = "scenarioLogKey";
	public static final String RUN_CORRELATION_ID = "runCorrelationId";
	public static final String RUN_MODE = "runMode";
	public static final String JOB_CONFIG_PATH = "jobConfigPath";
	public static final String JOB_NAME = "jobName";
	public static final String JOB_EXECUTION_ID = "jobExecutionId";
	public static final String STEP_NAME = "stepName";

	private RunLoggingContext() {
	}

	public static void put(String key, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		MDC.put(key, value);
	}

	public static void clearStepScope() {
		MDC.remove(STEP_NAME);
	}

	public static void clearJobScope() {
		clearStepScope();
		MDC.remove(JOB_NAME);
		MDC.remove(JOB_EXECUTION_ID);
	}

	public static void clearAll() {
		MDC.remove(SCENARIO);
		MDC.remove(SCENARIO_LOG_KEY);
		MDC.remove(RUN_CORRELATION_ID);
		MDC.remove(RUN_MODE);
		MDC.remove(JOB_CONFIG_PATH);
		clearJobScope();
	}
}


