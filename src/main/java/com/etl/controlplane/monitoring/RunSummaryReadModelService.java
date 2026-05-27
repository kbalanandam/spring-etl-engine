package com.etl.controlplane.monitoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads RUN_SUMMARY evidence from scenario log files and projects it as a simple read model.
 */
@Component
public class RunSummaryReadModelService {

	private final Path logBaseDir;
	private final RunSummaryLogParser parser;

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
}

