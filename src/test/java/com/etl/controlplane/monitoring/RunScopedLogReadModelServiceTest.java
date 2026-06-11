package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

	@Test
	void excludesRawContinuationAfterAnotherJobLineTakesOver() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				structuredLine("2026-05-27T10:00:00.000+00:00", 101L, "RUN_EVENT", "job_started"),
				"java.lang.IllegalStateException: stack for job 101",
				structuredLine("2026-05-27T10:00:01.000+00:00", 102L, "RUN_EVENT", "job_started"),
				"java.lang.IllegalStateException: stack for job 102",
				structuredLine("2026-05-27T10:00:02.000+00:00", 101L, "RUN_SUMMARY", "run_summary")
		);

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		RunScopedLogReadModelService logService = new RunScopedLogReadModelService(summaryService, new StructuredLogEventParser(), 200);

		RunScopedLogView scopedView = logService.findRunScopedLogByJobExecutionId(101L, 200).orElseThrow();
		assertEquals(3, scopedView.lines().size());
		assertEquals("RUN_EVENT", scopedView.lines().get(0).recordType());
		assertEquals("RAW", scopedView.lines().get(1).recordType());
		assertEquals("RUN_SUMMARY", scopedView.lines().get(2).recordType());
		assertFalse(scopedView.lines().stream().anyMatch(line -> line.message().contains("job 102")));
	}

	@Test
	void usesHardDefaultLimitWhenRequestedLimitIsNonPositive() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				structuredLine("2026-05-27T10:00:00.000+00:00", 101L, "RUN_EVENT", "job_started"),
				structuredLine("2026-05-27T10:00:01.000+00:00", 101L, "STEP_EVENT", "step_started"),
				structuredLine("2026-05-27T10:00:02.000+00:00", 101L, "STEP_EVENT", "step_started"),
				structuredLine("2026-05-27T10:00:03.000+00:00", 101L, "STEP_EVENT", "step_started"),
				structuredLine("2026-05-27T10:00:04.000+00:00", 101L, "RUN_SUMMARY", "run_summary")
		);

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		RunScopedLogReadModelService logService = new RunScopedLogReadModelService(summaryService, new StructuredLogEventParser(), 2);

		RunScopedLogView scopedView = logService.findRunScopedLogByJobExecutionId(101L, 0).orElseThrow();
		assertEquals(5, scopedView.lines().size());
		assertFalse(scopedView.truncated());
	}

	@Test
	void clampsRequestedLimitToConfiguredMaximum() throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add(structuredLine("2026-05-27T09:59:59.000+00:00", 101L, "RUN_SUMMARY", "run_summary"));
		for (int i = 0; i < 1005; i++) {
			lines.add(structuredLine("2026-05-27T10:00:00.000+00:00", 101L, "STEP_EVENT", "step_progress"));
		}
		createLog(tempDir.resolve("2026-05-27/customer-load.log"), lines.toArray(String[]::new));

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		RunScopedLogReadModelService logService = new RunScopedLogReadModelService(summaryService, new StructuredLogEventParser(), 200);

		RunScopedLogView scopedView = logService.findRunScopedLogByJobExecutionId(101L, 5000).orElseThrow();
		assertEquals(1000, scopedView.lines().size());
		assertTrue(scopedView.truncated());
	}

	@Test
	void returnsEmptyLogViewWhenRunExistsButLogFileIsMissing() {
		RunSummaryReadModelService summaryService = mock(RunSummaryReadModelService.class);
		RunSummaryView summaryView = new RunSummaryView(
				"customer-load",
				101L,
				"COMPLETED",
				LocalDateTime.parse("2026-05-27T10:00:00"),
				LocalDateTime.parse("2026-05-27T10:00:10"),
				10L,
				10L,
				10L,
				0L,
				"explicit-job",
				"rerun-from-start",
				"manual",
				tempDir.resolve("missing.log").toString()
		);
		when(summaryService.findRunByJobExecutionId(101L)).thenReturn(Optional.of(summaryView));

		RunScopedLogReadModelService logService = new RunScopedLogReadModelService(summaryService, new StructuredLogEventParser(), 200);

		RunScopedLogView scopedView = logService.findRunScopedLogByJobExecutionId(101L, 200).orElseThrow();
		assertEquals(0, scopedView.totalLines());
		assertFalse(scopedView.truncated());
		assertTrue(scopedView.lines().isEmpty());
	}

	private String structuredLine(String timestamp, long jobExecutionId, String recordType, String eventName) {
		return timestamp + " INFO [main] [scenario:Customer Load] [run:run-" + jobExecutionId + "] [job:" + jobExecutionId + "] [step:n/a] logger - "
				+ recordType + " event=" + eventName + " scenario=Customer Load jobExecutionId=" + jobExecutionId;
	}

	private void createLog(Path path, String... lines) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, List.of(lines));
	}
}

