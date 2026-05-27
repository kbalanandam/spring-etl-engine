package com.etl.controlplane.monitoring;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses one structured runtime log line into MDC metadata plus message key/value fields.
 */
final class StructuredLogEventParser {

	private static final Pattern PREFIX_PATTERN = Pattern.compile(
			"^(\\S+)\\s+\\S+\\s+\\[[^\\]]*]\\s+\\[scenario:([^\\]]*)]\\s+\\[run:([^\\]]*)]\\s+\\[job:([^\\]]*)]\\s+\\[step:([^\\]]*)]\\s+[^-]+\\s+-\\s+(.*)$");
	private static final Pattern MESSAGE_PATTERN = Pattern.compile("^(\\w+)\\s+(.*)$");
	private static final Pattern FIELD_PATTERN = Pattern.compile("(\\w+)=((?:(?!\\s+\\w+=).)+)");

	Optional<StructuredLogEvent> parse(String line, Path logPath) {
		if (line == null || line.isBlank()) {
			return Optional.empty();
		}

		Matcher prefixMatcher = PREFIX_PATTERN.matcher(line);
		if (!prefixMatcher.find()) {
			return Optional.empty();
		}

		String message = prefixMatcher.group(6);
		Matcher messageMatcher = MESSAGE_PATTERN.matcher(message);
		if (!messageMatcher.find()) {
			return Optional.empty();
		}

		Map<String, String> fields = parseFields(messageMatcher.group(2));
		return Optional.of(new StructuredLogEvent(
				toPrefixTimestamp(prefixMatcher.group(1)),
				normalize(prefixMatcher.group(2)),
				normalize(prefixMatcher.group(3)),
				toLong(prefixMatcher.group(4)),
				normalize(prefixMatcher.group(5)),
				normalize(messageMatcher.group(1)),
				normalize(fields.get("event")),
				Map.copyOf(fields),
				logPath.toString()
		));
	}

	static Map<String, String> parseFields(String fieldBlock) {
		Map<String, String> fields = new HashMap<>();
		if (fieldBlock == null || fieldBlock.isBlank()) {
			return fields;
		}
		Matcher matcher = FIELD_PATTERN.matcher(fieldBlock);
		while (matcher.find()) {
			String key = matcher.group(1);
			String value = matcher.group(2);
			fields.put(key, value == null ? "" : value.trim());
		}
		return fields;
	}

	private static LocalDateTime toPrefixTimestamp(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value).toLocalDateTime();
		} catch (Exception ex) {
			return null;
		}
	}

	private static Long toLong(String value) {
		if (value == null || value.isBlank() || "n/a".equalsIgnoreCase(value) || "unknown".equalsIgnoreCase(value)) {
			return null;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}

