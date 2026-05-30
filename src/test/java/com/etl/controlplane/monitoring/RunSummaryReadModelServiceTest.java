package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunSummaryReadModelServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void readsAndSortsRunSummariesFromScenarioLogs() throws IOException {
		Path logFileA = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:02:03.001+00:00 INFO [main] [scenario:customer-load] [run:20260527-110203-001] [job:1001] [step:n/a] com.etl.job.listener.JobCompletionNotificationListener - RUN_SUMMARY event=run_summary scenario=customer-load mainFlow=Main subFlow=Sub recoveryPolicy=none jobName=etlJob jobExecutionId=1001 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0 handoffReadCount=0 handoffWriteCount=0 executedStepCount=1 rollupMode=STEP_SUM failureCount=0",
				"noise"
		);
		createLog(
				tempDir.resolve("2026-05-27/customer-delta.log"),
				"2026-05-27T12:15:09.000+00:00 INFO [main] [scenario:customer-delta] [run:20260527-121509-000] [job:1002] [step:n/a] com.etl.job.listener.JobCompletionNotificationListener - RUN_SUMMARY event=run_summary scenario=customer-delta mainFlow=Main subFlow=Sub recoveryPolicy=none jobName=etlJob jobExecutionId=1002 status=FAILED startTime=2026-05-27T12:10:00 endTime=2026-05-27T12:15:09 durationSeconds=309 sourceCount=20 writtenCount=15 rejectedCount=5 handoffReadCount=0 handoffWriteCount=0 executedStepCount=2 rollupMode=STEP_SUM failureCount=1"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		List<RunSummaryView> runs = service.latestRuns(10);

		assertEquals(2, runs.size());
		assertEquals("customer-delta", runs.get(0).scenario());
		assertEquals(1002L, runs.get(0).jobExecutionId());
		assertEquals("FAILED", runs.get(0).status());
		assertEquals(20L, runs.get(0).sourceCount());
		assertEquals(15L, runs.get(0).writtenCount());
		assertEquals(5L, runs.get(0).rejectedCount());
		assertEquals(logFileA.toString(), runs.get(1).logPath());
	}

	@Test
	void appliesLimitAndSkipsUnknownLogShapes() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/ops.log"),
				"random line",
				"2026-05-27T09:00:00.000+00:00 INFO [main] [scenario:ops] [run:1] [job:2001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=ops jobExecutionId=2001 status=COMPLETED startTime=2026-05-27T09:00:00 endTime=2026-05-27T09:00:01 durationSeconds=1 sourceCount=1 writtenCount=1 rejectedCount=0",
				"2026-05-27T09:10:00.000+00:00 INFO [main] [scenario:ops] [run:2] [job:2002] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=ops jobExecutionId=2002 status=COMPLETED startTime=2026-05-27T09:10:00 endTime=2026-05-27T09:10:02 durationSeconds=2 sourceCount=2 writtenCount=2 rejectedCount=0"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		List<RunSummaryView> runs = service.latestRuns(1);

		assertEquals(1, runs.size());
		assertEquals(2002L, runs.get(0).jobExecutionId());
	}

	@Test
	void findsRunByJobExecutionId() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:02:03.001+00:00 INFO [main] [scenario:customer-load] [run:20260527-110203-001] [job:1001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=1001 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());

		Optional<RunSummaryView> run = service.findRunByJobExecutionId(1001L);
		assertEquals(true, run.isPresent());
		assertEquals("customer-load", run.orElseThrow().scenario());
	}

	@Test
	void returnsEmptyWhenRunIdIsMissing() {
		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		assertEquals(Optional.empty(), service.findRunByJobExecutionId(9999L));
	}

	@Test
	void returnsRecentRunsForMatchingJobKeyOrDisplayName() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T09:00:00.000+00:00 INFO [main] [scenario:customer-load] [run:1] [job:2001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=2001 status=COMPLETED startTime=2026-05-27T09:00:00 endTime=2026-05-27T09:00:01 durationSeconds=1 sourceCount=1 writtenCount=1 rejectedCount=0",
				"2026-05-27T09:10:00.000+00:00 INFO [main] [scenario:Customer Load] [run:2] [job:2002] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=Customer Load jobExecutionId=2002 status=COMPLETED startTime=2026-05-27T09:10:00 endTime=2026-05-27T09:10:02 durationSeconds=2 sourceCount=2 writtenCount=2 rejectedCount=0",
				"2026-05-27T09:20:00.000+00:00 INFO [main] [scenario:other-job] [run:3] [job:2003] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=other-job jobExecutionId=2003 status=COMPLETED startTime=2026-05-27T09:20:00 endTime=2026-05-27T09:20:02 durationSeconds=2 sourceCount=2 writtenCount=2 rejectedCount=0"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		List<RunSummaryView> runs = service.latestRunsForJob("customer-load", "Customer Load", 10);

		assertEquals(2, runs.size());
		assertEquals(2002L, runs.get(0).jobExecutionId());
		assertEquals(2001L, runs.get(1).jobExecutionId());
	}

	@Test
	void skipsUnreadableLogFilesAndStillReturnsValidRuns() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:02:03.001+00:00 INFO [main] [scenario:customer-load] [run:20260527-110203-001] [job:1001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=1001 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0"
		);
		Path unreadableLog = tempDir.resolve("2026-05-27/bad-encoding.log");
		Files.createDirectories(unreadableLog.getParent());
		Files.write(unreadableLog, new byte[]{(byte) 0xC3, (byte) 0x28});

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		List<RunSummaryView> runs = service.latestRuns(10);

		assertEquals(1, runs.size());
		assertEquals(1001L, runs.get(0).jobExecutionId());
	}

	@Test
	void ignoresStartupLogsDuringRunSummaryIndexing() throws IOException {
		createLog(
				tempDir.resolve("startup/startup.log"),
				"2026-05-27T08:00:00.000+00:00 INFO [main] [scenario:startup] [run:n/a] [job:3001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=startup jobExecutionId=3001 status=COMPLETED startTime=2026-05-27T08:00:00 endTime=2026-05-27T08:00:01 durationSeconds=1 sourceCount=1 writtenCount=1 rejectedCount=0"
		);
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:02:03.001+00:00 INFO [main] [scenario:customer-load] [run:20260527-110203-001] [job:1001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=1001 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		List<RunSummaryView> runs = service.latestRuns(10);

		assertEquals(1, runs.size());
		assertEquals(1001L, runs.get(0).jobExecutionId());
	}

	@Test
	void skipsOversizedLogFilesDuringIndexing() throws IOException {
		Path oversizedLog = tempDir.resolve("2026-05-27/oversized.log");
		Files.createDirectories(oversizedLog.getParent());
		String oversizedLine = "2026-05-27T11:02:03.001+00:00 INFO [main] [scenario:customer-load] [run:1] [job:1001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=1001 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0 " + "x".repeat(4000);
		Files.writeString(oversizedLog, oversizedLine);

		createLog(
				tempDir.resolve("2026-05-27/small.log"),
				"2026-05-27T12:02:03.001+00:00 INFO [main] [scenario:customer-delta] [run:2] [job:1002] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-delta jobExecutionId=1002 status=COMPLETED startTime=2026-05-27T12:00:00 endTime=2026-05-27T12:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(
				tempDir,
				new RunSummaryLogParser(),
				new InMemoryRunSummaryRegistry(),
				2000,
				100
		);
		List<RunSummaryView> runs = service.latestRuns(10);

		assertEquals(1, runs.size());
		assertEquals(1002L, runs.get(0).jobExecutionId());
	}

	private Path createLog(Path path, String... lines) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, List.of(lines));
		return path;
	}
}



