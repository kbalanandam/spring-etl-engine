package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Parsed structured log line enriched with MDC prefix metadata.
 */
record StructuredLogEvent(
		LocalDateTime loggedAt,
		Integer lineNumber,
		String scenario,
		String runCorrelationId,
		Long jobExecutionId,
		String mdcStepName,
		String recordType,
		String event,
		Map<String, String> fields,
		String logPath
) {
}

