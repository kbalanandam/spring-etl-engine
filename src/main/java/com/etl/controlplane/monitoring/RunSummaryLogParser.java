package com.etl.controlplane.monitoring;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RunSummaryLogParser {

	private static final Pattern RUN_SUMMARY_PATTERN =
			Pattern.compile("RUN_SUMMARY\\s+event=run_summary\\s+(.*)$");

	Optional<RunSummaryView> parse(String line, Path logPath) {
		if (line == null || line.isBlank()) {
			return Optional.empty();
		}
		Matcher matcher = RUN_SUMMARY_PATTERN.matcher(line);
		if (!matcher.find()) {
			return Optional.empty();
		}

		Map<String, String> fields = parseFields(matcher.group(1));
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
				logPath.toString()
		));
	}

	private static Map<String, String> parseFields(String fieldBlock) {
		Map<String, String> fields = new HashMap<>();
		String[] tokens = fieldBlock.split("\\s+");
		for (String token : tokens) {
			int separator = token.indexOf('=');
			if (separator <= 0 || separator >= token.length() - 1) {
				continue;
			}
			String key = token.substring(0, separator);
			String value = token.substring(separator + 1);
			fields.put(key, value);
		}
		return fields;
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

