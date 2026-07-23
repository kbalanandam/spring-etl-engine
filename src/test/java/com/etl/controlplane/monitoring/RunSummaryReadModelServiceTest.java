package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunSummaryReadModelServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void readsAndSortsRunSummariesFromScenarioLogs() throws IOException {
		Path logFileA = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:02:03.001+00:00 INFO [main] [scenario:customer-load] [run:20260527-110203-001] [job:1001] [step:n/a] com.etl.job.listener.JobCompletionNotificationListener - RUN_SUMMARY event=run_summary scenario=customer-load mainFlow=Main subFlow=Sub runMode=explicit-job recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=1001 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0 handoffReadCount=0 handoffWriteCount=0 executedStepCount=1 rollupMode=STEP_SUM failureCount=0",
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
		assertEquals("explicit-job", runs.get(1).runMode());
		assertEquals("rerun-from-start", runs.get(1).recoveryPolicy());
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
	void projectsStartedRunBeforeSummaryAndThenOverwritesWithTerminalSummary() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/custom-steps.log"),
				"2026-05-27T10:00:00.000+00:00 INFO [main] [scenario:custom-steps] [run:20260527-100000-000] [job:3001] [step:n/a] logger - RUN_EVENT event=job_started scenario=custom-steps mainFlow=Main subFlow=Sub recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=3001 startTime=2026-05-27T10:00:00 runMode=explicit-job",
				"2026-05-27T10:02:05.000+00:00 INFO [main] [scenario:custom-steps] [run:20260527-100000-000] [job:3001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=custom-steps mainFlow=Main subFlow=Sub runMode=explicit-job recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=3001 status=COMPLETED startTime=2026-05-27T10:00:00 endTime=2026-05-27T10:02:05 durationSeconds=125 sourceCount=40 writtenCount=40 rejectedCount=0 handoffReadCount=0 handoffWriteCount=0 executedStepCount=1 rollupMode=STEP_SUM failureCount=0"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		List<RunSummaryView> runs = service.latestRuns(10);

		assertEquals(1, runs.size());
		assertEquals(3001L, runs.get(0).jobExecutionId());
		assertEquals("COMPLETED", runs.get(0).status());
		assertEquals(40L, runs.get(0).writtenCount());
	}

	@Test
	void projectsStartedRunWhenOnlyJobStartedEventExists() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/custom-steps-started.log"),
				"2026-05-27T10:00:00.000+00:00 INFO [main] [scenario:custom-steps] [run:20260527-100000-000] [job:3002] [step:n/a] logger - RUN_EVENT event=job_started scenario=custom-steps mainFlow=Main subFlow=Sub recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=3002 startTime=2026-05-27T10:00:00 runMode=explicit-job"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		List<RunSummaryView> runs = service.latestRuns(10);

		assertEquals(1, runs.size());
		assertEquals(3002L, runs.get(0).jobExecutionId());
		assertEquals("STARTED", runs.get(0).status());
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
	void latestRunsFilteredFreshBypassesThrottleAndReturnsUpdatedProjection() throws IOException {
		Path logPath = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:00:00.000+00:00 INFO [main] [scenario:customer-load] [run:20260527-110000-000] [job:160] [step:n/a] logger - RUN_EVENT event=job_started scenario=customer-load jobExecutionId=160 startTime=2026-05-27T11:00:00 runMode=explicit-job"
		);

		InMemoryRunSummaryRegistry registry = new InMemoryRunSummaryRegistry();
		RunSummaryReadModelService service = new RunSummaryReadModelService(
				tempDir,
				new RunSummaryLogParser(),
				registry,
				5_000_000L,
				500,
				60_000L
		);

		List<RunSummaryView> firstRead = service.latestRunsFiltered(10, null, null, null, null, ZoneId.of("UTC"));
		assertEquals(1, firstRead.size());
		assertEquals("STARTED", firstRead.get(0).status());

		Files.writeString(logPath,
				"2026-05-27T11:02:03.001+00:00 INFO [main] [scenario:customer-load] [run:20260527-110000-000] [job:160] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=160 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0"
		);

		List<RunSummaryView> staleRead = service.latestRunsFiltered(10, null, null, null, null, ZoneId.of("UTC"));
		assertEquals("STARTED", staleRead.get(0).status());

		List<RunSummaryView> refreshedRead = service.latestRunsFilteredFresh(10, null, null, null, null, null, ZoneId.of("UTC"));
		assertEquals("COMPLETED", refreshedRead.get(0).status());
		assertEquals(10L, refreshedRead.get(0).writtenCount());
	}

	@Test
	void syncRunFromLogPathRefreshesOnlyTargetExecutionAndKeepsExistingRuns() throws IOException {
		Path logPath = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:00:00.000+00:00 INFO [main] [scenario:customer-load] [run:1] [job:2001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=2001 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:00:01 durationSeconds=1 sourceCount=1 writtenCount=1 rejectedCount=0",
				"2026-05-27T11:02:00.000+00:00 INFO [main] [scenario:customer-load] [run:2] [job:2002] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=2002 status=COMPLETED startTime=2026-05-27T11:02:00 endTime=2026-05-27T11:02:02 durationSeconds=2 sourceCount=2 writtenCount=2 rejectedCount=0"
		);

		InMemoryRunSummaryRegistry registry = new InMemoryRunSummaryRegistry();
		registry.upsert(new RunSummaryView(
				"existing-run",
				9000L,
				"COMPLETED",
				LocalDateTime.parse("2026-05-26T10:00:00"),
				LocalDateTime.parse("2026-05-26T10:00:01"),
				1L,
				1L,
				1L,
				0L,
				"logs/2026-05-26/existing-run.log"
		));
		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser(), registry);

		boolean synced = service.syncRunFromLogPath(logPath, 2002L);

		assertEquals(true, synced);
		assertEquals(Optional.empty(), registry.findByJobExecutionId(2001L));
		assertEquals(true, registry.findByJobExecutionId(2002L).isPresent());
		assertEquals(true, registry.findByJobExecutionId(9000L).isPresent());
	}

	@Test
	void findRunByJobExecutionIdForcesRefreshWhenProjectionIsStillStarted() throws IOException {
		Path logPath = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:02:03.001+00:00 INFO [main] [scenario:customer-load] [run:20260527-110203-001] [job:160] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=160 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0"
		);

		InMemoryRunSummaryRegistry staleRegistry = new InMemoryRunSummaryRegistry();
		staleRegistry.upsert(new RunSummaryView(
				"customer-load",
				160L,
				"STARTED",
				LocalDateTime.parse("2026-05-27T11:00:00"),
				null,
				null,
				null,
				null,
				null,
				"explicit-job",
				"rerun-from-start",
				logPath.toString()
		));

		RunSummaryReadModelService service = new RunSummaryReadModelService(
				tempDir,
				new RunSummaryLogParser(),
				staleRegistry,
				5_000_000L,
				500,
				60_000L
		);

		Optional<RunSummaryView> run = service.findRunByJobExecutionId(160L);
		assertEquals(true, run.isPresent());
		assertEquals("COMPLETED", run.orElseThrow().status());
		assertEquals(10L, run.orElseThrow().writtenCount());
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
	void appliesIndependentJobAndStartDateFilters() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T09:00:00.000+00:00 INFO [main] [scenario:customer-load] [run:1] [job:2001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=2001 status=COMPLETED startTime=2026-05-27T09:00:00 endTime=2026-05-27T09:00:01 durationSeconds=1 sourceCount=1 writtenCount=1 rejectedCount=0",
				"2026-05-27T09:10:00.000+00:00 INFO [main] [scenario:other-job] [run:2] [job:2002] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=other-job jobExecutionId=2002 status=COMPLETED startTime=2026-05-28T09:10:00 endTime=2026-05-28T09:10:02 durationSeconds=2 sourceCount=2 writtenCount=2 rejectedCount=0"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());

		List<RunSummaryView> dateOnly = service.latestRunsFiltered(10, null, null, null, LocalDate.parse("2026-05-28"), ZoneId.of("UTC"));
		assertEquals(1, dateOnly.size());
		assertEquals(2002L, dateOnly.get(0).jobExecutionId());

		List<RunSummaryView> jobOnly = service.latestRunsFiltered(10, "customer-load", null, null, null, ZoneId.of("UTC"));
		assertEquals(1, jobOnly.size());
		assertEquals(2001L, jobOnly.get(0).jobExecutionId());

		List<RunSummaryView> combined = service.latestRunsFiltered(10, "customer-load", null, null, LocalDate.parse("2026-05-28"), ZoneId.of("UTC"));
		assertEquals(0, combined.size());
	}

	@Test
	void appliesRunModeAndRecoveryPolicyFilters() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T09:00:00.000+00:00 INFO [main] [scenario:customer-load] [run:1] [job:2001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load runMode=explicit-job recoveryPolicy=rerun-from-start jobExecutionId=2001 status=COMPLETED startTime=2026-05-27T09:00:00 endTime=2026-05-27T09:00:01 durationSeconds=1 sourceCount=1 writtenCount=1 rejectedCount=0",
				"2026-05-27T09:10:00.000+00:00 INFO [main] [scenario:customer-load] [run:2] [job:2002] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load runMode=demo-fallback recoveryPolicy=rerun-from-start jobExecutionId=2002 status=COMPLETED startTime=2026-05-27T09:10:00 endTime=2026-05-27T09:10:02 durationSeconds=2 sourceCount=2 writtenCount=2 rejectedCount=0"
		);

		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());

		List<RunSummaryView> modeOnly = service.latestRunsFiltered(10, null, "explicit-job", null, null, ZoneId.of("UTC"));
		assertEquals(1, modeOnly.size());
		assertEquals(2001L, modeOnly.get(0).jobExecutionId());

		List<RunSummaryView> modeAndPolicy = service.latestRunsFiltered(10, null, "demo fallback", "rerun from start", null, ZoneId.of("UTC"));
		assertEquals(1, modeAndPolicy.size());
		assertEquals(2002L, modeAndPolicy.get(0).jobExecutionId());

		List<RunSummaryView> noMatch = service.latestRunsFiltered(10, null, "explicit-job", "resume-from-checkpoint", null, ZoneId.of("UTC"));
		assertEquals(0, noMatch.size());
	}

	@Test
	void usesRequestedLimitForUnfilteredRunsWithoutFullRegistryScan() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T09:00:00.000+00:00 INFO [main] [scenario:customer-load] [run:1] [job:2001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=2001 status=COMPLETED startTime=2026-05-27T09:00:00 endTime=2026-05-27T09:00:01 durationSeconds=1 sourceCount=1 writtenCount=1 rejectedCount=0",
				"2026-05-27T09:10:00.000+00:00 INFO [main] [scenario:customer-load] [run:2] [job:2002] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=2002 status=COMPLETED startTime=2026-05-27T09:10:00 endTime=2026-05-27T09:10:02 durationSeconds=2 sourceCount=2 writtenCount=2 rejectedCount=0"
		);

		TrackingRunSummaryRegistry trackingRegistry = new TrackingRunSummaryRegistry();
		RunSummaryReadModelService service = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser(), trackingRegistry);

		List<RunSummaryView> runs = service.latestRunsFiltered(1, null, null, null, null, ZoneId.of("UTC"));

		assertEquals(1, runs.size());
		assertEquals(1, trackingRegistry.lastLatestRunsLimit.get());
	}

	@Test
	void preservesUnfilteredHistoryWhenRegistryBrieflyReturnsEmpty() {
		IntermittentEmptyRegistry registry = new IntermittentEmptyRegistry();
		registry.upsert(new RunSummaryView(
				"customer-load",
				701L,
				"COMPLETED",
				LocalDateTime.parse("2026-07-22T11:00:00"),
				LocalDateTime.parse("2026-07-22T11:00:03"),
				3L,
				6L,
				6L,
				0L,
				"explicit-job",
				"rerun-from-start",
				"logs/2026-07-22/customer-load.log"
		));

		RunSummaryReadModelService service = new RunSummaryReadModelService(
				tempDir,
				new RunSummaryLogParser(),
				registry,
				5_000_000L,
				500,
				60_000L
		);

		List<RunSummaryView> firstRead = service.latestRunsFiltered(10, null, null, null, null, ZoneId.of("UTC"));
		assertEquals(1, firstRead.size());
		assertEquals(701L, firstRead.get(0).jobExecutionId());

		// Simulate a transient empty read for both the emptiness probe and the data fetch.
		registry.returnEmptyLatestRunsTimes(2);
		List<RunSummaryView> secondRead = service.latestRunsFiltered(10, null, null, null, null, ZoneId.of("UTC"));
		assertEquals(1, secondRead.size());
		assertEquals(701L, secondRead.get(0).jobExecutionId());
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

	@Test
	void resumesFromPersistedCheckpointAfterServiceRestart() throws IOException {
		Path logPath = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:00:00.000+00:00 INFO [main] [scenario:customer-load] [run:20260527-110000-000] [job:160] [step:n/a] logger - RUN_EVENT event=job_started scenario=customer-load jobExecutionId=160 startTime=2026-05-27T11:00:00 runMode=explicit-job"
		);

		DurableCheckpointTestRegistry registry = new DurableCheckpointTestRegistry();
		RunSummaryReadModelService firstService = new RunSummaryReadModelService(
				tempDir,
				new RunSummaryLogParser(),
				registry,
				5_000_000L,
				500,
				0L
		);

		assertEquals("STARTED", firstService.latestRuns(10).get(0).status());
		String normalizedPath = logPath.toAbsolutePath().normalize().toString();
		RunSummaryRegistry.LogReadCheckpoint initialCheckpoint = registry.findLogCheckpoint(normalizedPath).orElseThrow();

		Files.writeString(
				logPath,
				System.lineSeparator() + "2026-05-27T11:02:03.001+00:00 INFO [main] [scenario:customer-load] [run:20260527-110000-000] [job:160] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=160 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:02:03 durationSeconds=123 sourceCount=10 writtenCount=10 rejectedCount=0",
				StandardOpenOption.APPEND
		);

		registry.returnEmptyLatestRunsOnce();
		RunSummaryReadModelService restartedService = new RunSummaryReadModelService(
				tempDir,
				new RunSummaryLogParser(),
				registry,
				5_000_000L,
				500,
				0L
		);

		List<RunSummaryView> refreshedRuns = restartedService.latestRuns(10);
		assertEquals(1, refreshedRuns.size());
		assertEquals("COMPLETED", refreshedRuns.get(0).status());
		assertEquals(10L, refreshedRuns.get(0).writtenCount());

		RunSummaryRegistry.LogReadCheckpoint updatedCheckpoint = registry.findLogCheckpoint(normalizedPath).orElseThrow();
		assertTrue(updatedCheckpoint.offsetBytes() > initialCheckpoint.offsetBytes());
	}

	@Test
	void resetsPersistedCheckpointWhenLogFileShrinks() throws IOException {
		Path logPath = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T11:00:00.000+00:00 INFO [main] [scenario:customer-load] [run:1] [job:2001] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=2001 status=COMPLETED startTime=2026-05-27T11:00:00 endTime=2026-05-27T11:00:05 durationSeconds=5 sourceCount=1 writtenCount=1 rejectedCount=0",
				"2026-05-27T11:10:00.000+00:00 INFO [main] [scenario:customer-load] [run:2] [job:2002] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=2002 status=COMPLETED startTime=2026-05-27T11:10:00 endTime=2026-05-27T11:10:05 durationSeconds=5 sourceCount=2 writtenCount=2 rejectedCount=0"
		);

		DurableCheckpointTestRegistry registry = new DurableCheckpointTestRegistry();
		RunSummaryReadModelService firstService = new RunSummaryReadModelService(
				tempDir,
				new RunSummaryLogParser(),
				registry,
				5_000_000L,
				500,
				0L
		);
		firstService.latestRuns(10);

		String normalizedPath = logPath.toAbsolutePath().normalize().toString();
		RunSummaryRegistry.LogReadCheckpoint initialCheckpoint = registry.findLogCheckpoint(normalizedPath).orElseThrow();

		Files.writeString(
				logPath,
				"2026-05-27T11:20:00.000+00:00 INFO [main] [scenario:customer-load] [run:3] [job:2003] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=customer-load jobExecutionId=2003 status=COMPLETED startTime=2026-05-27T11:20:00 endTime=2026-05-27T11:20:02 durationSeconds=2 sourceCount=3 writtenCount=3 rejectedCount=0"
		);

		registry.returnEmptyLatestRunsOnce();
		RunSummaryReadModelService restartedService = new RunSummaryReadModelService(
				tempDir,
				new RunSummaryLogParser(),
				registry,
				5_000_000L,
				500,
				0L
		);
		restartedService.latestRuns(10);

		assertTrue(registry.findByJobExecutionId(2003L).isPresent());
		RunSummaryRegistry.LogReadCheckpoint updatedCheckpoint = registry.findLogCheckpoint(normalizedPath).orElseThrow();
		assertTrue(updatedCheckpoint.offsetBytes() <= Files.size(logPath));
		assertTrue(updatedCheckpoint.offsetBytes() < initialCheckpoint.offsetBytes());
	}

	private Path createLog(Path path, String... lines) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, List.of(lines));
		return path;
	}

	private static class TrackingRunSummaryRegistry implements RunSummaryRegistry {
		private final InMemoryRunSummaryRegistry delegate = new InMemoryRunSummaryRegistry();
		private final AtomicInteger lastLatestRunsLimit = new AtomicInteger(-1);

		@Override
		public void upsert(RunSummaryView runSummary) {
			delegate.upsert(runSummary);
		}

		@Override
		public List<RunSummaryView> latestRuns(int limit) {
			lastLatestRunsLimit.set(limit);
			return delegate.latestRuns(limit);
		}

		@Override
		public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
			return delegate.findByJobExecutionId(jobExecutionId);
		}

		@Override
		public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
			return delegate.findRecoveryByJobExecutionId(jobExecutionId);
		}

		@Override
		public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
			return delegate.listStepRecordsByJobExecutionId(jobExecutionId, limit);
		}

		@Override
		public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
			return delegate.listArtifactRecordsByJobExecutionId(jobExecutionId, limit);
		}

		@Override
		public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
			return delegate.listArtifactRecordsByStepRecordId(stepRecordId, limit);
		}
	}

	private static class DurableCheckpointTestRegistry implements RunSummaryRegistry {
		private final InMemoryRunSummaryRegistry delegate = new InMemoryRunSummaryRegistry();
		private final ConcurrentHashMap<String, LogReadCheckpoint> checkpoints = new ConcurrentHashMap<>();
		private final AtomicInteger emptyLatestRunsCallsRemaining = new AtomicInteger(0);

		void returnEmptyLatestRunsOnce() {
			emptyLatestRunsCallsRemaining.set(1);
		}

		@Override
		public void upsert(RunSummaryView runSummary) {
			delegate.upsert(runSummary);
		}

		@Override
		public Optional<LogReadCheckpoint> findLogCheckpoint(String logPath) {
			return Optional.ofNullable(checkpoints.get(logPath));
		}

		@Override
		public void upsertLogCheckpoint(String logPath, long offsetBytes, long fileSizeBytes, long fileLastModifiedMillis) {
			checkpoints.put(logPath, new LogReadCheckpoint(logPath, offsetBytes, fileSizeBytes, fileLastModifiedMillis));
		}

		@Override
		public List<RunSummaryView> latestRuns(int limit) {
			if (emptyLatestRunsCallsRemaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
				return List.of();
			}
			return delegate.latestRuns(limit);
		}

		@Override
		public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
			return delegate.findByJobExecutionId(jobExecutionId);
		}

		@Override
		public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
			return delegate.findRecoveryByJobExecutionId(jobExecutionId);
		}

		@Override
		public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
			return delegate.listStepRecordsByJobExecutionId(jobExecutionId, limit);
		}

		@Override
		public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
			return delegate.listArtifactRecordsByJobExecutionId(jobExecutionId, limit);
		}

		@Override
		public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
			return delegate.listArtifactRecordsByStepRecordId(stepRecordId, limit);
		}
	}

	private static class IntermittentEmptyRegistry implements RunSummaryRegistry {
		private final InMemoryRunSummaryRegistry delegate = new InMemoryRunSummaryRegistry();
		private final AtomicInteger emptyLatestRunsCallsRemaining = new AtomicInteger(0);

		void returnEmptyLatestRunsTimes(int count) {
			emptyLatestRunsCallsRemaining.set(Math.max(0, count));
		}

		@Override
		public void upsert(RunSummaryView runSummary) {
			delegate.upsert(runSummary);
		}

		@Override
		public List<RunSummaryView> latestRuns(int limit) {
			if (emptyLatestRunsCallsRemaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
				return List.of();
			}
			return delegate.latestRuns(limit);
		}

		@Override
		public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
			return delegate.findByJobExecutionId(jobExecutionId);
		}

		@Override
		public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
			return delegate.findRecoveryByJobExecutionId(jobExecutionId);
		}

		@Override
		public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
			return delegate.listStepRecordsByJobExecutionId(jobExecutionId, limit);
		}

		@Override
		public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
			return delegate.listArtifactRecordsByJobExecutionId(jobExecutionId, limit);
		}

		@Override
		public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
			return delegate.listArtifactRecordsByStepRecordId(stepRecordId, limit);
		}
	}
}



