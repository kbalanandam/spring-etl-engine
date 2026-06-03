package com.etl.logging;

import org.slf4j.MDC;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Central MDC keys and helpers for scenario/job-run aware logging.
 */
public final class RunLoggingContext {
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	public static final String SCENARIO = "scenario";
	public static final String SCENARIO_LOG_KEY = "scenarioLogKey";
	public static final String RUN_CORRELATION_ID = "runCorrelationId";
	public static final String RUN_MODE = "runMode";
	public static final String JOB_CONFIG_PATH = "jobConfigPath";
	public static final String MAIN_FLOW = "mainFlow";
	public static final String SUB_FLOW = "subFlow";
	public static final String RECOVERY_POLICY = "recoveryPolicy";
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
		MDC.remove(MAIN_FLOW);
		MDC.remove(SUB_FLOW);
		MDC.remove(RECOVERY_POLICY);
		clearJobScope();
	}

	public static String sanitizeScenarioName(String value) {
		String resolved = value == null ? "" : value.trim();
		if (resolved.isBlank()) {
			return "unknown-scenario";
		}
		return resolved.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	public static String buildScenarioLogKey(String scenario, LocalDate date) {
		LocalDate resolvedDate = date == null ? LocalDate.now() : date;
		return resolvedDate + "/" + sanitizeScenarioName(scenario);
	}

	public static String buildRunCorrelationId(LocalDateTime now) {
		LocalDateTime resolvedNow = now == null ? LocalDateTime.now() : now;
		return RUN_ID_FORMATTER.format(resolvedNow);
	}
}


