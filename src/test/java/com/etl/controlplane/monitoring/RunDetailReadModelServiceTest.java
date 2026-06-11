package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunDetailReadModelServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void assemblesRunDetailFromStructuredScenarioLogEvidence() throws IOException {
		Path logPath = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T10:00:00.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:101] [step:n/a] logger - RUN_EVENT event=job_started scenario=Customer Load mainFlow=orders-flow subFlow=default-subflow recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=101 startTime=2026-05-27T10:00:00 runMode=explicit-job jobConfigPath=C:/jobs/customer-load/job-config.yaml",
				"2026-05-27T10:00:01.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:101] [step:normalize-orders] logger - STEP_EVENT event=step_started mainFlow=orders-flow subFlow=normalize-orders-subflow recoveryPolicy=rerun-from-start stepName=normalize-orders stepExecutionId=201 stepSubFlowOrder=0 dependsOnSubFlows=none consumesHandoffAliases=none producesHandoffAliases=OrdersValidated upstreamSteps=none linkTypes=none linkControlSummary=none stepSummary=Normalize orders step",
				"2026-05-27T10:00:05.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:101] [step:normalize-orders] logger - STEP_EVENT event=step_finished mainFlow=orders-flow subFlow=normalize-orders-subflow recoveryPolicy=rerun-from-start stepName=normalize-orders stepExecutionId=201 status=COMPLETED readCount=10 writeCount=8 filterCount=0 skipCount=0 rollbackCount=0 rejectedCount=2 rejectOutputPath=output/rejects/orders.csv archivedSourcePath=archive/orders.csv stepSubFlowOrder=0 dependsOnSubFlows=none consumesHandoffAliases=none producesHandoffAliases=OrdersValidated upstreamSteps=none linkTypes=none linkControlSummary=none stepSummary=Normalize orders step",
				"2026-05-27T10:00:06.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:101] [step:publish-orders] logger - STEP_EVENT event=step_started mainFlow=orders-flow subFlow=publish-orders-subflow recoveryPolicy=rerun-from-start stepName=publish-orders stepExecutionId=202 stepSubFlowOrder=1 dependsOnSubFlows=normalize-orders-subflow consumesHandoffAliases=OrdersValidated producesHandoffAliases=none upstreamSteps=normalize-orders linkTypes=ORDER_ONLY linkControlSummary=requiresCompleted stepSummary=Publish orders step",
				"2026-05-27T10:00:10.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:101] [step:publish-orders] logger - STEP_EVENT event=step_finished mainFlow=orders-flow subFlow=publish-orders-subflow recoveryPolicy=rerun-from-start stepName=publish-orders stepExecutionId=202 status=FAILED readCount=8 writeCount=8 filterCount=0 skipCount=0 rollbackCount=1 rejectedCount=0 rejectOutputPath= archivedSourcePath= stepSubFlowOrder=1 dependsOnSubFlows=normalize-orders-subflow consumesHandoffAliases=OrdersValidated producesHandoffAliases=none upstreamSteps=normalize-orders linkTypes=ORDER_ONLY linkControlSummary=requiresCompleted stepSummary=Publish orders step",
				"2026-05-27T10:00:10.100+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:101] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=Customer Load mainFlow=orders-flow subFlow=default-subflow recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=101 status=FAILED startTime=2026-05-27T10:00:00 endTime=2026-05-27T10:00:10 durationSeconds=10 sourceCount=10 writtenCount=8 rejectedCount=2 handoffReadCount=8 handoffWriteCount=8 executedStepCount=2 rollupMode=operator-oriented failureCount=1",
				"2026-05-27T10:00:10.200+00:00 ERROR [main] [scenario:Customer Load] [run:20260527-100000-000] [job:101] [step:n/a] logger - JOB_FAILURE event=job_failure scenario=Customer Load mainFlow=orders-flow subFlow=default-subflow recoveryPolicy=rerun-from-start failureCategory=target_write exceptionType=IllegalStateException rootCause=constraint failed message=constraint failed"
		);

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		RunDetailReadModelService detailService = new RunDetailReadModelService(summaryService, new StructuredLogEventParser());

		Optional<RunDetailView> detail = detailService.findRunDetailByJobExecutionId(101L);
		assertTrue(detail.isPresent());
		assertEquals(101L, detail.orElseThrow().run().jobExecutionId());
		assertEquals(2, detail.orElseThrow().steps().size());
		assertEquals("normalize-orders", detail.orElseThrow().steps().get(0).stepName());
		assertEquals("COMPLETED", detail.orElseThrow().steps().get(0).status());
		assertEquals("publish-orders", detail.orElseThrow().steps().get(1).stepName());
		assertEquals("FAILED", detail.orElseThrow().steps().get(1).status());
		assertEquals(2, detail.orElseThrow().artifacts().size());
		assertEquals("reject-output", detail.orElseThrow().artifacts().get(0).role());
		assertEquals("archived-source", detail.orElseThrow().artifacts().get(1).role());
		assertEquals("target_write", detail.orElseThrow().failureSummary().category());
		assertEquals("constraint failed", detail.orElseThrow().failureSummary().message());
		assertEquals(logPath.toString(), detail.orElseThrow().evidenceLinks().get(0).href());
		assertEquals(logPath + "#L6", detail.orElseThrow().evidenceLinks().get(1).href());
		assertEquals("run-summary", detail.orElseThrow().evidenceLinks().get(1).type());
		assertEquals(logPath + "#L2", detail.orElseThrow().evidenceLinks().get(2).href());
		assertEquals("step-event", detail.orElseThrow().evidenceLinks().get(2).type());
		assertEquals(logPath + "#L7", detail.orElseThrow().evidenceLinks().get(3).href());
		assertEquals("job-failure", detail.orElseThrow().evidenceLinks().get(3).type());
	}

	@Test
	void omitsFailureEvidenceLinkWhenRunHasNoJobFailureEvent() throws IOException {
		createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T10:00:01.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:202] [step:normalize-orders] logger - STEP_EVENT event=step_started mainFlow=orders-flow subFlow=normalize-orders-subflow recoveryPolicy=rerun-from-start stepName=normalize-orders stepExecutionId=301 stepSubFlowOrder=0 dependsOnSubFlows=none consumesHandoffAliases=none producesHandoffAliases=OrdersValidated upstreamSteps=none linkTypes=none linkControlSummary=none stepSummary=Normalize orders step",
				"2026-05-27T10:00:05.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:202] [step:normalize-orders] logger - STEP_EVENT event=step_finished mainFlow=orders-flow subFlow=normalize-orders-subflow recoveryPolicy=rerun-from-start stepName=normalize-orders stepExecutionId=301 status=COMPLETED readCount=10 writeCount=10 filterCount=0 skipCount=0 rollbackCount=0 rejectedCount=0 rejectOutputPath= archivedSourcePath= stepSubFlowOrder=0 dependsOnSubFlows=none consumesHandoffAliases=none producesHandoffAliases=OrdersValidated upstreamSteps=none linkTypes=none linkControlSummary=none stepSummary=Normalize orders step",
				"2026-05-27T10:00:10.100+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:202] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=Customer Load mainFlow=orders-flow subFlow=default-subflow recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=202 status=COMPLETED startTime=2026-05-27T10:00:00 endTime=2026-05-27T10:00:10 durationSeconds=10 sourceCount=10 writtenCount=10 rejectedCount=0 handoffReadCount=10 handoffWriteCount=10 executedStepCount=1 rollupMode=operator-oriented failureCount=0"
		);

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		RunDetailReadModelService detailService = new RunDetailReadModelService(summaryService, new StructuredLogEventParser());

		Optional<RunDetailView> detail = detailService.findRunDetailByJobExecutionId(202L);
		assertTrue(detail.isPresent());
		assertEquals(3, detail.orElseThrow().evidenceLinks().size());
		assertEquals("run-summary", detail.orElseThrow().evidenceLinks().get(1).type());
		assertEquals("step-event", detail.orElseThrow().evidenceLinks().get(2).type());
	}

	@Test
	void reconcilesDetailWithPersistedStepAndArtifactRecords() throws IOException {
		Path logPath = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T10:00:00.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:404] [step:n/a] logger - RUN_EVENT event=job_started scenario=Customer Load mainFlow=orders-flow subFlow=default-subflow recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=404 startTime=2026-05-27T10:00:00 runMode=explicit-job",
				"2026-05-27T10:00:01.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:404] [step:normalize-orders] logger - STEP_EVENT event=step_started stepName=normalize-orders stepExecutionId=801",
				"2026-05-27T10:00:05.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:404] [step:normalize-orders] logger - STEP_EVENT event=step_finished stepName=normalize-orders stepExecutionId=801 status=FAILED readCount=10 writeCount=8 rejectedCount=2 rejectOutputPath=output/rejects/orders.csv archivedSourcePath=archive/orders.csv",
				"2026-05-27T10:00:10.100+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:404] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=Customer Load mainFlow=orders-flow subFlow=default-subflow recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=404 status=FAILED startTime=2026-05-27T10:00:00 endTime=2026-05-27T10:00:10 durationSeconds=10 sourceCount=10 writtenCount=8 rejectedCount=2"
		);

		InMemoryRunSummaryRegistry registry = new InMemoryRunSummaryRegistry();
		registry.upsert(new RunSummaryView(
				"Customer Load",
				404L,
				"FAILED",
				LocalDateTime.parse("2026-05-27T10:00:00"),
				LocalDateTime.parse("2026-05-27T10:00:10"),
				10L,
				10L,
				8L,
				2L,
				logPath.toString()
		));
		RunSummaryRegistry stubRegistry = new RunSummaryRegistry() {
			@Override
			public void upsert(RunSummaryView runSummary) {
				registry.upsert(runSummary);
			}

			@Override
			public List<RunSummaryView> latestRuns(int limit) {
				return registry.latestRuns(limit);
			}

			@Override
			public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
				return registry.findByJobExecutionId(jobExecutionId);
			}

			@Override
			public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
				return Optional.empty();
			}

			@Override
			public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
				if (jobExecutionId != 404L) {
					return List.of();
				}
				return List.of(
						new RunStepRecordView("sr-404-1", "rr-404", "normalize-orders", "COMPLETED",
								LocalDateTime.parse("2026-05-27T10:00:01"), LocalDateTime.parse("2026-05-27T10:00:05"), 4L,
								12L, 12L, 0L, 0L, 0L, null),
						new RunStepRecordView("sr-404-2", "rr-404", "publish-orders", "COMPLETED",
								LocalDateTime.parse("2026-05-27T10:00:06"), LocalDateTime.parse("2026-05-27T10:00:09"), 3L,
								12L, 12L, 0L, 0L, 0L, 0L)
				);
			}

			@Override
			public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
				if (jobExecutionId != 404L) {
					return List.of();
				}
				return List.of(
						new RunArtifactRecordView("ar-reject-404", "rr-404", "sr-404-1", "STEP_REJECT_OUTPUT", "output/rejects/orders.csv", LocalDateTime.parse("2026-05-27T10:00:05")),
						new RunArtifactRecordView("ar-log-404", "rr-404", null, "RUN_LOG", logPath.toString(), LocalDateTime.parse("2026-05-27T10:00:10"))
				);
			}

			@Override
			public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
				return List.of();
			}
		};

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser(), registry);
		RunDetailReadModelService detailService = new RunDetailReadModelService(summaryService, stubRegistry, new StructuredLogEventParser());

		Optional<RunDetailView> detail = detailService.findRunDetailByJobExecutionId(404L);
		assertTrue(detail.isPresent());
		assertEquals(1, detail.orElseThrow().steps().size());
		assertEquals("COMPLETED", detail.orElseThrow().steps().get(0).status());
		assertEquals(12L, detail.orElseThrow().steps().get(0).readCount());
		assertEquals(2L, detail.orElseThrow().steps().get(0).rejectedCount());
		assertEquals(2, detail.orElseThrow().artifacts().size());
		assertEquals("reject-output", detail.orElseThrow().artifacts().get(0).role());
		assertEquals("archived-source", detail.orElseThrow().artifacts().get(1).role());
	}

	@Test
	void ignoresPersistedStepsAndArtifactsThatDoNotBelongToLogDerivedRunStepSet() throws IOException {
		Path logPath = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T10:00:00.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:505] [step:n/a] logger - RUN_EVENT event=job_started scenario=Customer Load mainFlow=customer-load-main-flow subFlow=default-subflow recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=505 startTime=2026-05-27T10:00:00 runMode=explicit-job",
				"2026-05-27T10:00:01.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:505] [step:customers-step] logger - STEP_EVENT event=step_started stepName=customers-step stepExecutionId=901",
				"2026-05-27T10:00:02.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:505] [step:customers-step] logger - STEP_EVENT event=step_finished stepName=customers-step stepExecutionId=901 status=COMPLETED readCount=3 writeCount=3 rejectedCount=0",
				"2026-05-27T10:00:03.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:505] [step:departments-step] logger - STEP_EVENT event=step_started stepName=departments-step stepExecutionId=902",
				"2026-05-27T10:00:04.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:505] [step:departments-step] logger - STEP_EVENT event=step_finished stepName=departments-step stepExecutionId=902 status=COMPLETED readCount=3 writeCount=3 rejectedCount=0",
				"2026-05-27T10:00:05.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:505] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=Customer Load mainFlow=customer-load-main-flow subFlow=default-subflow recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=505 status=COMPLETED startTime=2026-05-27T10:00:00 endTime=2026-05-27T10:00:05 durationSeconds=5 sourceCount=6 writtenCount=6 rejectedCount=0"
		);

		InMemoryRunSummaryRegistry registry = new InMemoryRunSummaryRegistry();
		registry.upsert(new RunSummaryView(
				"Customer Load",
				505L,
				"COMPLETED",
				LocalDateTime.parse("2026-05-27T10:00:00"),
				LocalDateTime.parse("2026-05-27T10:00:05"),
				5L,
				6L,
				6L,
				0L,
				logPath.toString()
		));
		RunSummaryRegistry stubRegistry = new RunSummaryRegistry() {
			@Override
			public void upsert(RunSummaryView runSummary) {
				registry.upsert(runSummary);
			}

			@Override
			public List<RunSummaryView> latestRuns(int limit) {
				return registry.latestRuns(limit);
			}

			@Override
			public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
				return registry.findByJobExecutionId(jobExecutionId);
			}

			@Override
			public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
				return Optional.empty();
			}

			@Override
			public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
				if (jobExecutionId != 505L) {
					return List.of();
				}
				return List.of(
						new RunStepRecordView("sr-505-1", "rr-505", "customers-step", "COMPLETED",
								LocalDateTime.parse("2026-05-27T10:00:01"), LocalDateTime.parse("2026-05-27T10:00:02"), 1L,
								3L, 3L, 0L, 0L, 0L, 0L),
						new RunStepRecordView("sr-505-2", "rr-505", "departments-step", "COMPLETED",
								LocalDateTime.parse("2026-05-27T10:00:03"), LocalDateTime.parse("2026-05-27T10:00:04"), 1L,
								3L, 3L, 0L, 0L, 0L, 0L),
						new RunStepRecordView("sr-505-3", "rr-505", "nested-tag-validation-csv-step", "COMPLETED",
								LocalDateTime.parse("2026-05-27T10:00:01"), LocalDateTime.parse("2026-05-27T10:00:04"), 3L,
								36616L, 36583L, 33L, 0L, 0L, 0L)
				);
			}

			@Override
			public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
				if (jobExecutionId != 505L) {
					return List.of();
				}
				return List.of(
						new RunArtifactRecordView("ar-log-505", "rr-505", null, "RUN_LOG", logPath.toString(), LocalDateTime.parse("2026-05-27T10:00:05")),
						new RunArtifactRecordView("ar-foreign-505", "rr-505", "sr-505-3", "STEP_REJECT_OUTPUT", "output/rejects/foreign.csv", LocalDateTime.parse("2026-05-27T10:00:04"))
				);
			}

			@Override
			public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
				return List.of();
			}
		};

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser(), registry);
		RunDetailReadModelService detailService = new RunDetailReadModelService(summaryService, stubRegistry, new StructuredLogEventParser());

		Optional<RunDetailView> detail = detailService.findRunDetailByJobExecutionId(505L);
		assertTrue(detail.isPresent());
		assertEquals(List.of("customers-step", "departments-step"), detail.orElseThrow().steps().stream().map(StepRecordView::stepName).toList());
		assertEquals(0, detail.orElseThrow().artifacts().size());
	}

	@Test
	void doesNotAddSecondRejectArtifactFromPersistedRecordsWhenDetailAlreadyHasStepRejectEvidence() throws IOException {
		Path logPath = createLog(
				tempDir.resolve("2026-05-27/customer-load.log"),
				"2026-05-27T10:00:00.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:606] [step:n/a] logger - RUN_EVENT event=job_started scenario=Customer Load mainFlow=orders-flow subFlow=default-subflow recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=606 startTime=2026-05-27T10:00:00 runMode=explicit-job",
				"2026-05-27T10:00:01.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:606] [step:nested-tag-validation-csv-step] logger - STEP_EVENT event=step_started stepName=nested-tag-validation-csv-step stepExecutionId=1001",
				"2026-05-27T10:00:05.000+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:606] [step:nested-tag-validation-csv-step] logger - STEP_EVENT event=step_finished stepName=nested-tag-validation-csv-step stepExecutionId=1001 status=COMPLETED readCount=36616 writeCount=36583 filterCount=33 skipCount=0 rollbackCount=0 rejectedCount=33 rejectOutputPath=output/rejects/tags.csv",
				"2026-05-27T10:00:10.100+00:00 INFO [main] [scenario:Customer Load] [run:20260527-100000-000] [job:606] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=Customer Load mainFlow=orders-flow subFlow=default-subflow recoveryPolicy=rerun-from-start jobName=etlJob jobExecutionId=606 status=COMPLETED startTime=2026-05-27T10:00:00 endTime=2026-05-27T10:00:10 durationSeconds=10 sourceCount=36616 writtenCount=36583 rejectedCount=33"
		);

		InMemoryRunSummaryRegistry registry = new InMemoryRunSummaryRegistry();
		registry.upsert(new RunSummaryView(
				"Customer Load",
				606L,
				"COMPLETED",
				LocalDateTime.parse("2026-05-27T10:00:00"),
				LocalDateTime.parse("2026-05-27T10:00:10"),
				10L,
				36616L,
				36583L,
				33L,
				logPath.toString()
		));

		RunSummaryRegistry stubRegistry = new RunSummaryRegistry() {
			@Override
			public void upsert(RunSummaryView runSummary) {
				registry.upsert(runSummary);
			}

			@Override
			public List<RunSummaryView> latestRuns(int limit) {
				return registry.latestRuns(limit);
			}

			@Override
			public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
				return registry.findByJobExecutionId(jobExecutionId);
			}

			@Override
			public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
				return Optional.empty();
			}

			@Override
			public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
				if (jobExecutionId != 606L) {
					return List.of();
				}
				return List.of(
						new RunStepRecordView("sr-606-1", "rr-606", "nested-tag-validation-csv-step", "COMPLETED",
								LocalDateTime.parse("2026-05-27T10:00:01"), LocalDateTime.parse("2026-05-27T10:00:05"), 4L,
								36616L, 36583L, 33L, 0L, 0L, 0L)
				);
			}

			@Override
			public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
				if (jobExecutionId != 606L) {
					return List.of();
				}
				return List.of(
						new RunArtifactRecordView("ar-reject-606", "rr-606", "sr-606-1", "STEP_REJECT_OUTPUT", "", LocalDateTime.parse("2026-05-27T10:00:05")),
						new RunArtifactRecordView("ar-log-606", "rr-606", null, "RUN_LOG", logPath.toString(), LocalDateTime.parse("2026-05-27T10:00:10"))
				);
			}

			@Override
			public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
				return List.of();
			}
		};

		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser(), registry);
		RunDetailReadModelService detailService = new RunDetailReadModelService(summaryService, stubRegistry, new StructuredLogEventParser());

		Optional<RunDetailView> detail = detailService.findRunDetailByJobExecutionId(606L);
		assertTrue(detail.isPresent());
		assertEquals(1, detail.orElseThrow().artifacts().size());
		assertEquals("reject-output", detail.orElseThrow().artifacts().get(0).role());
		assertEquals(33L, detail.orElseThrow().artifacts().get(0).recordCount());
	}

	@Test
	void returnsEmptyWhenRunSummaryDoesNotExist() {
		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser());
		RunDetailReadModelService detailService = new RunDetailReadModelService(summaryService, new StructuredLogEventParser());

		assertEquals(Optional.empty(), detailService.findRunDetailByJobExecutionId(999L));
	}

	@Test
	void marksEvidenceAsUnavailableWhenLogFileWasRolledOrDeleted() {
		InMemoryRunSummaryRegistry registry = new InMemoryRunSummaryRegistry();
		registry.upsert(new RunSummaryView(
				"Customer Load",
				303L,
				"FAILED",
				null,
				null,
				null,
				null,
				null,
				null,
				tempDir.resolve("2026-05-27/missing.log").toString()
		));
		RunSummaryReadModelService summaryService = new RunSummaryReadModelService(tempDir, new RunSummaryLogParser(), registry);
		RunDetailReadModelService detailService = new RunDetailReadModelService(summaryService, new StructuredLogEventParser());

		Optional<RunDetailView> detail = detailService.findRunDetailByJobExecutionId(303L);
		assertTrue(detail.isPresent());
		assertEquals(2, detail.orElseThrow().evidenceLinks().size());
		assertEquals("log-file", detail.orElseThrow().evidenceLinks().get(0).type());
		assertEquals("log-file-missing", detail.orElseThrow().evidenceLinks().get(1).type());
	}

	private Path createLog(Path path, String... lines) throws IOException {
		Files.createDirectories(path.getParent());
		Files.write(path, List.of(lines));
		return path;
	}
}

