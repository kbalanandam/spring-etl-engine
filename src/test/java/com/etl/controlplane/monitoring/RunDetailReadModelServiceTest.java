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

