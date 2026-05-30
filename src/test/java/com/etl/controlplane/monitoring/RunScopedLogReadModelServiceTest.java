package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunScopedLogReadModelServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void returnsOnlyLinesForSelectedJobExecutionId() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T10:00:00.000+00:00 INFO [main] [scenario:Customer Load] [run:run-101] [job:101] [step:n/a] logger - RUN_EVENT event=job_started scenario=Customer Load jobExecutionId=101",
				"2026-05-27T10:00:01.000+00:00 INFO [main] [scenario:Customer Load] [run:run-101] [job:101] [step:n/a] logger - STEP_EVENT event=step_started stepName=load-customers stepExecutionId=5001",
				"java.lang.IllegalStateException: boom",
				"2026-05-27T10:00:02.000+00:00 INFO [main] [scenario:Customer Load] [run:run-102] [job:102] [step:n/a] logger - RUN_EVENT event=job_started scenario=Customer Load jobExecutionId=102",
				"2026-05-27T10:00:03.000+00:00 INFO [main] [scenario:Customer Load] [run:run-101] [job:101] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=Customer Load jobExecutionId=101 status=FAILED startTime=2026-05-27T10:00:00 endTime=2026-05-27T10:00:03 durationSeconds=3 sourceCount=10 writtenCount=8 rejectedCount=2"
		);

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		RunScopedLogReadModelService logService = new RunScopedLogReadModelService(summaryService, new StructuredLogEventParser(), 200);

		Optional<RunScopedLogView> view = logService.findRunScopedLogByJobExecutionId(101L, 200);
		assertTrue(view.isPresent());
		assertEquals(4, view.orElseThrow().lines().size());
		assertEquals("RUN_EVENT", view.orElseThrow().lines().get(0).recordType());
		assertEquals("RAW", view.orElseThrow().lines().get(2).recordType());
		assertEquals("RUN_SUMMARY", view.orElseThrow().lines().get(3).recordType());
	}

	@Test
	void appliesLimitAndFlagsTruncation() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T10:00:00.000+00:00 INFO [main] [scenario:Customer Load] [run:run-101] [job:101] [step:n/a] logger - RUN_EVENT event=job_started scenario=Customer Load jobExecutionId=101",
				"2026-05-27T10:00:01.000+00:00 INFO [main] [scenario:Customer Load] [run:run-101] [job:101] [step:n/a] logger - STEP_EVENT event=step_started stepName=load-customers stepExecutionId=5001",
				"2026-05-27T10:00:03.000+00:00 INFO [main] [scenario:Customer Load] [run:run-101] [job:101] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=Customer Load jobExecutionId=101 status=FAILED startTime=2026-05-27T10:00:00 endTime=2026-05-27T10:00:03 durationSeconds=3 sourceCount=10 writtenCount=8 rejectedCount=2"
		);

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		RunScopedLogReadModelService logService = new RunScopedLogReadModelService(summaryService, new StructuredLogEventParser(), 200);

		Optional<RunScopedLogView> view = logService.findRunScopedLogByJobExecutionId(101L, 2);
		assertTrue(view.isPresent());
		assertEquals(2, view.orElseThrow().lines().size());
		assertTrue(view.orElseThrow().truncated());
	}

	private Path createLog(Path path, String... lines) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, List.of(lines));
		return path;
	}
}

