package com.etl.controlplane.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads RUN_SUMMARY evidence from scenario log files and projects it as a simple read model.
 */
@Component
public class RunSummaryReadModelService {

	private final Path logBaseDir;
	private final RunSummaryLogParser parser;

	@Autowired
	public RunSummaryReadModelService(@Value("${etl.logging.base-dir:logs}") String logBaseDir) {
		this(Path.of(logBaseDir), new RunSummaryLogParser());
	}

	RunSummaryReadModelService(Path logBaseDir, RunSummaryLogParser parser) {
		this.logBaseDir = logBaseDir;
		this.parser = parser;
	}

	public List<RunSummaryView> latestRuns(int limit) {
		if (limit <= 0 || !Files.exists(logBaseDir)) {
			return List.of();
		}

		List<RunSummaryView> runs = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(logBaseDir)) {
			paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".log"))
					.forEach(path -> collectRunSummaries(path, runs));
		} catch (IOException ignored) {
			return List.of();
		}

		return runs.stream()
				.sorted(Comparator
						.comparing(RunSummaryView::startTime, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(RunSummaryView::jobExecutionId, Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(limit)
				.toList();
	}

	public Optional<RunSummaryView> findRunByJobExecutionId(long jobExecutionId) {
		if (!Files.exists(logBaseDir)) {
			return Optional.empty();
		}

		return latestRuns(Integer.MAX_VALUE).stream()
				.filter(run -> run.jobExecutionId() != null && run.jobExecutionId() == jobExecutionId)
				.findFirst();
	}

	public List<RunSummaryView> latestRunsForJob(String jobKey, String displayName, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		String normalizedJobKey = normalize(jobKey);
		String normalizedDisplayName = normalize(displayName);
		return latestRuns(Integer.MAX_VALUE).stream()
				.filter(run -> matchesJob(run, normalizedJobKey, normalizedDisplayName))
				.limit(limit)
				.toList();
	}

	private void collectRunSummaries(Path logPath, List<RunSummaryView> target) {
		try (Stream<String> lines = Files.lines(logPath)) {
			lines.map(line -> parser.parse(line, logPath))
					.filter(java.util.Optional::isPresent)
					.map(java.util.Optional::get)
					.forEach(target::add);
		} catch (IOException ignored) {
			// Read-model collection is best-effort for now; unavailable files are skipped.
		}
	}

	private boolean matchesJob(RunSummaryView run, String normalizedJobKey, String normalizedDisplayName) {
		String scenario = normalize(run.scenario());
		return scenario.equalsIgnoreCase(normalizedJobKey)
				|| (!normalizedDisplayName.isBlank() && scenario.equalsIgnoreCase(normalizedDisplayName));
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}




