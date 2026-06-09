package com.etl.controlplane.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Builds run-scoped log lines for one job execution id from scenario log evidence.
 */
@Service
public class RunScopedLogReadModelService {

	private static final int DEFAULT_LIMIT = 200;
	private static final int MAX_LIMIT = 1000;
	private static final Pattern LEVEL_PATTERN = Pattern.compile("^\\S+\\s+(INFO|WARN|ERROR|DEBUG|TRACE)\\s+");
	private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^(\\S+)\\s+");

	private final RunSummaryReadModelService runSummaryReadModelService;
	private final StructuredLogEventParser parser;
	private final int defaultLimit;

	@Autowired
	public RunScopedLogReadModelService(
			RunSummaryReadModelService runSummaryReadModelService,
			@Value("${controlplane.runs.detail-log.default-limit:200}") int defaultLimit
	) {
		this(runSummaryReadModelService, new StructuredLogEventParser(), defaultLimit);
	}

	RunScopedLogReadModelService(
			RunSummaryReadModelService runSummaryReadModelService,
			StructuredLogEventParser parser,
			int defaultLimit
	) {
		this.runSummaryReadModelService = runSummaryReadModelService;
		this.parser = parser;
		this.defaultLimit = normalizeLimit(defaultLimit);
	}

	public Optional<RunScopedLogView> findRunScopedLogByJobExecutionId(long jobExecutionId, Integer requestedLimit) {
		return runSummaryReadModelService.findRunByJobExecutionId(jobExecutionId)
				.map(summary -> build(summary, jobExecutionId, requestedLimit));
	}

	private RunScopedLogView build(RunSummaryView summary, long jobExecutionId, Integer requestedLimit) {
		int effectiveLimit = requestedLimit == null ? defaultLimit : normalizeLimit(requestedLimit);
		Path logPath = Path.of(summary.logPath());
		if (!Files.exists(logPath)) {
			return new RunScopedLogView(jobExecutionId, summary.scenario(), summary.logPath(), 0, false, List.of());
		}

		List<RunLogLineView> lines = new ArrayList<>();
		boolean includeContinuation = false;
		boolean truncated = false;

		try (Stream<String> stream = Files.lines(logPath)) {
			List<String> rawLines = stream.toList();
			for (int index = 0; index < rawLines.size(); index++) {
				String rawLine = rawLines.get(index);
				int lineNumber = index + 1;
				Optional<StructuredLogEvent> maybeEvent = parser.parse(rawLine, logPath, lineNumber);
				if (maybeEvent.isPresent()) {
					StructuredLogEvent event = maybeEvent.orElseThrow();
					includeContinuation = event.jobExecutionId() != null && event.jobExecutionId() == jobExecutionId;
					if (includeContinuation) {
						if (lines.size() >= effectiveLimit) {
							truncated = true;
							break;
						}
						lines.add(new RunLogLineView(
								lineNumber,
								event.loggedAt(),
								extractLevel(rawLine),
								event.recordType(),
								event.event(),
								rawLine,
								true
						));
					}
					continue;
				}

				if (includeContinuation) {
					if (lines.size() >= effectiveLimit) {
						truncated = true;
						break;
					}
					lines.add(new RunLogLineView(
							lineNumber,
							extractTimestamp(rawLine),
							extractLevel(rawLine),
							"RAW",
							"",
							rawLine,
							false
					));
				}
			}
		} catch (IOException ignored) {
			return new RunScopedLogView(jobExecutionId, summary.scenario(), summary.logPath(), 0, false, List.of());
		}

		return new RunScopedLogView(
				jobExecutionId,
				summary.scenario(),
				summary.logPath(),
				lines.size(),
				truncated,
				List.copyOf(lines)
		);
	}

	private int normalizeLimit(int candidate) {
		if (candidate < 1) {
			return DEFAULT_LIMIT;
		}
		return Math.min(candidate, MAX_LIMIT);
	}

	private String extractLevel(String rawLine) {
		Matcher matcher = LEVEL_PATTERN.matcher(rawLine == null ? "" : rawLine);
		if (!matcher.find()) {
			return "";
		}
		return matcher.group(1);
	}

	private LocalDateTime extractTimestamp(String rawLine) {
		Matcher matcher = TIMESTAMP_PATTERN.matcher(rawLine == null ? "" : rawLine);
		if (!matcher.find()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(matcher.group(1)).toLocalDateTime();
		} catch (Exception ignored) {
			return null;
		}
	}
}

