package com.etl.controlplane.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads RUN_SUMMARY evidence from scenario log files and projects it as a simple read model.
 */
@Component
public class RunSummaryReadModelService {
	private static final long DEFAULT_MAX_LOG_FILE_SIZE_BYTES = 5_000_000L;
	private static final int DEFAULT_MAX_LOG_FILES_PER_REFRESH = 500;

	private final Path logBaseDir;
	private final RunSummaryLogParser parser;
	private final RunSummaryRegistry registry;
	private final long maxLogFileSizeBytes;
	private final int maxLogFilesPerRefresh;

	@Autowired
	public RunSummaryReadModelService(@Value("${etl.logging.base-dir:logs}") String logBaseDir,
	                                  RunSummaryRegistry registry,
	                                  @Value("${controlplane.runs.max-log-file-size-bytes:5000000}") long maxLogFileSizeBytes,
	                                  @Value("${controlplane.runs.max-log-files-per-refresh:500}") int maxLogFilesPerRefresh) {
		this(Path.of(logBaseDir), new RunSummaryLogParser(), registry, maxLogFileSizeBytes, maxLogFilesPerRefresh);
	}

	RunSummaryReadModelService(Path logBaseDir, RunSummaryLogParser parser) {
		this(logBaseDir, parser, new InMemoryRunSummaryRegistry(), DEFAULT_MAX_LOG_FILE_SIZE_BYTES, DEFAULT_MAX_LOG_FILES_PER_REFRESH);
	}

	RunSummaryReadModelService(Path logBaseDir, RunSummaryLogParser parser, RunSummaryRegistry registry) {
		this(logBaseDir, parser, registry, DEFAULT_MAX_LOG_FILE_SIZE_BYTES, DEFAULT_MAX_LOG_FILES_PER_REFRESH);
	}

	RunSummaryReadModelService(Path logBaseDir,
	                          RunSummaryLogParser parser,
	                          RunSummaryRegistry registry,
	                          long maxLogFileSizeBytes,
	                          int maxLogFilesPerRefresh) {
		this.logBaseDir = logBaseDir;
		this.parser = parser;
		this.registry = registry;
		this.maxLogFileSizeBytes = Math.max(1L, maxLogFileSizeBytes);
		this.maxLogFilesPerRefresh = Math.max(1, maxLogFilesPerRefresh);
	}

	public List<RunSummaryView> latestRuns(int limit) {
		return latestRunsFiltered(limit, null, null, ZoneId.systemDefault());
	}

	public List<RunSummaryView> latestRunsFiltered(int limit,
	                                              String jobFilter,
	                                              LocalDate startDate,
	                                              ZoneId selectedZoneId) {
		if (limit <= 0) {
			return List.of();
		}
		reindexFromLogs();
		ZoneId effectiveZone = selectedZoneId == null ? ZoneId.systemDefault() : selectedZoneId;
		String normalizedJobFilter = normalizeToken(jobFilter);
		return registry.latestRuns(Integer.MAX_VALUE).stream()
				.filter(run -> matchesJobFilter(run, normalizedJobFilter))
				.filter(run -> matchesStartDate(run, startDate, effectiveZone))
				.limit(limit)
				.toList();
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
					.filter(this::isScenarioRunLog)
					.filter(this::isWithinSizeLimit)
					.limit(maxLogFilesPerRefresh)
					.forEach(this::collectRunSummaries);
		} catch (IOException ignored) {
			// Read-model refresh is best-effort; stale cache is acceptable for this slice.
		}
	}

	private boolean isScenarioRunLog(Path path) {
		Path relativePath;
		try {
			relativePath = logBaseDir.relativize(path);
		} catch (IllegalArgumentException ex) {
			return true;
		}
		if (relativePath.getNameCount() < 2) {
			return false;
		}
		return !"startup".equalsIgnoreCase(relativePath.getName(0).toString());
	}

	private boolean isWithinSizeLimit(Path path) {
		try {
			return Files.size(path) <= maxLogFileSizeBytes;
		} catch (IOException ex) {
			return false;
		}
	}

	private void collectRunSummaries(Path logPath) {
		try (Stream<String> lines = Files.lines(logPath)) {
			lines.map(line -> parser.parse(line, logPath))
					.filter(java.util.Optional::isPresent)
					.map(java.util.Optional::get)
					.forEach(registry::upsert);
		} catch (IOException | UncheckedIOException ignored) {
			// Read-model collection is best-effort for now; unavailable files are skipped.
		}
	}

	private boolean matchesJob(RunSummaryView run, String normalizedJobKey, String normalizedDisplayName) {
		String scenario = normalize(run.scenario());
		return scenario.equalsIgnoreCase(normalizedJobKey)
				|| (!normalizedDisplayName.isBlank() && scenario.equalsIgnoreCase(normalizedDisplayName));
	}

	private boolean matchesJobFilter(RunSummaryView run, String normalizedJobFilter) {
		if (normalizedJobFilter.isBlank()) {
			return true;
		}
		return normalizeToken(run.scenario()).equals(normalizedJobFilter);
	}

	private boolean matchesStartDate(RunSummaryView run, LocalDate startDate, ZoneId selectedZoneId) {
		if (startDate == null) {
			return true;
		}
		if (run.startTime() == null) {
			return false;
		}
		LocalDate runDateInSelectedZone = run.startTime()
				.atZone(ZoneId.systemDefault())
				.withZoneSameInstant(selectedZoneId)
				.toLocalDate();
		return !runDateInSelectedZone.isBefore(startDate);
	}

	private String normalizeToken(String value) {
		return normalize(value).toLowerCase()
				.replaceAll("[^a-z0-9]", "");
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}




