package com.etl.controlplane.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	private final RunSummaryRegistry registry;

	@Autowired
	public RunSummaryReadModelService(@Value("${etl.logging.base-dir:logs}") String logBaseDir,
	                                  RunSummaryRegistry registry) {
		this(Path.of(logBaseDir), new RunSummaryLogParser(), registry);
	}

	RunSummaryReadModelService(Path logBaseDir, RunSummaryLogParser parser) {
		this(logBaseDir, parser, new InMemoryRunSummaryRegistry());
	}

	RunSummaryReadModelService(Path logBaseDir, RunSummaryLogParser parser, RunSummaryRegistry registry) {
		this.logBaseDir = logBaseDir;
		this.parser = parser;
		this.registry = registry;
	}

	public List<RunSummaryView> latestRuns(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		reindexFromLogs();
		return registry.latestRuns(limit);
	}

	public Optional<RunSummaryView> findRunByJobExecutionId(long jobExecutionId) {
		reindexFromLogs();
		return registry.findByJobExecutionId(jobExecutionId);
	}

	public List<RunSummaryView> latestRunsForJob(String jobKey, String displayName, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		reindexFromLogs();
		String normalizedJobKey = normalize(jobKey);
		String normalizedDisplayName = normalize(displayName);
		return registry.latestRuns(Integer.MAX_VALUE).stream()
				.filter(run -> matchesJob(run, normalizedJobKey, normalizedDisplayName))
				.limit(limit)
				.toList();
	}

	private void reindexFromLogs() {
		if (!Files.exists(logBaseDir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(logBaseDir)) {
			paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".log"))
					.forEach(this::collectRunSummaries);
		} catch (IOException ignored) {
			// Read-model refresh is best-effort; stale cache is acceptable for this slice.
		}
	}

	private void collectRunSummaries(Path logPath) {
		try (Stream<String> lines = Files.lines(logPath)) {
			lines.map(line -> parser.parse(line, logPath))
					.filter(java.util.Optional::isPresent)
					.map(java.util.Optional::get)
					.forEach(registry::upsert);
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




