package com.etl.controlplane.monitoring;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

final class RunSummaryLogParser {

	private final StructuredLogEventParser parser = new StructuredLogEventParser();

	Optional<RunSummaryView> parse(String line, Path logPath) {
		Optional<StructuredLogEvent> maybeEvent = parser.parse(line, logPath);
		if (maybeEvent.isEmpty()) {
			return Optional.empty();
		}

		StructuredLogEvent event = maybeEvent.orElseThrow();
		if ("RUN_SUMMARY".equals(event.recordType()) && "run_summary".equalsIgnoreCase(event.event())) {
			var fields = event.fields();
			String scenario = field(fields, "scenario", "unknown-scenario");
			String status = field(fields, "status", "UNKNOWN");

			return Optional.of(new RunSummaryView(
					scenario,
					toLong(fields.get("jobExecutionId")),
					status,
					toDateTime(fields.get("startTime")),
					toDateTime(fields.get("endTime")),
					toLong(fields.get("durationSeconds")),
					toLong(fields.get("sourceCount")),
					toLong(fields.get("writtenCount")),
					toLong(fields.get("rejectedCount")),
					nullIfBlank(fields.get("runMode")),
					nullIfBlank(fields.get("recoveryPolicy")),
					nullIfBlank(fields.get("triggerOrigin")),
					logPath.toString()
			));
		}

		if ("RUN_EVENT".equals(event.recordType()) && "job_started".equalsIgnoreCase(event.event())) {
			var fields = event.fields();
			Long jobExecutionId = toLong(fields.get("jobExecutionId"));
			if (jobExecutionId == null) {
				jobExecutionId = event.jobExecutionId();
			}
			if (jobExecutionId == null) {
				return Optional.empty();
			}
			LocalDateTime startTime = toDateTime(fields.get("startTime"));
			if (startTime == null) {
				startTime = event.loggedAt();
			}
			String scenario = field(fields, "scenario", nullIfBlank(event.scenario()) == null ? "unknown-scenario" : event.scenario());

			return Optional.of(new RunSummaryView(
					scenario,
					jobExecutionId,
					"STARTED",
					startTime,
					null,
					null,
					null,
					null,
					null,
					nullIfBlank(fields.get("runMode")),
					nullIfBlank(fields.get("recoveryPolicy")),
					nullIfBlank(fields.get("triggerOrigin")),
					logPath.toString()
			));
		}

		return Optional.empty();
	}

	private static String nullIfBlank(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value;
	}

	private static String field(Map<String, String> fields, String key, String defaultValue) {
		String value = fields.get(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}

	private static Long toLong(String value) {
		if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
			return null;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static LocalDateTime toDateTime(String value) {
		if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
			return null;
		}
		try {
			return LocalDateTime.parse(value);
		} catch (Exception ex) {
			return null;
		}
	}
}




