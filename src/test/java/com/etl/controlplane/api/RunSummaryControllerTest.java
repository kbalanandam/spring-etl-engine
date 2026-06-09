package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunDetailReadModelService;
import com.etl.controlplane.monitoring.RunDetailView;
import com.etl.controlplane.monitoring.EvidenceLinkView;
import com.etl.controlplane.monitoring.StepRecordView;
import com.etl.controlplane.monitoring.ArtifactRecordView;
import com.etl.controlplane.monitoring.FailureSummaryView;
import com.etl.controlplane.monitoring.RunScopedLogReadModelService;
import com.etl.controlplane.monitoring.RunScopedLogView;
import com.etl.controlplane.monitoring.RunLogLineView;
import com.etl.controlplane.monitoring.RunSummaryRegistry;
import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.monitoring.RunStepRecordView;
import com.etl.controlplane.monitoring.RunArtifactRecordView;
import com.etl.controlplane.monitoring.RunCheckpointAnchorView;
import com.etl.controlplane.monitoring.RunRecoveryView;
import com.etl.controlplane.monitoring.RunSummaryView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RunSummaryController.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.main.web-application-type=servlet")
class RunSummaryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RunSummaryReadModelService runSummaryReadModelService;

	@MockitoBean
	private RunSummaryRegistry runSummaryRegistry;

	@MockitoBean
	private RunDetailReadModelService runDetailReadModelService;

	@MockitoBean
	private RunScopedLogReadModelService runScopedLogReadModelService;

	@Test
	void returnsRunsUsingDefaultLimit() throws Exception {
		when(runSummaryReadModelService.latestRunsFiltered(eq(25), isNull(), isNull(), isNull(), isNull(), eq(ZoneId.systemDefault()))).thenReturn(List.of(
				new RunSummaryView("customer-load", 101L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L,
						"explicit-job", "rerun-from-start", "SCHEDULE", "logs/2026-05-27/customer-load.log")
		));

		mockMvc.perform(get("/api/v1/runs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].scenario").value("customer-load"))
				.andExpect(jsonPath("$.items[0].jobExecutionId").value(101))
				.andExpect(jsonPath("$.items[0].triggerOrigin").value("SCHEDULE"))
				.andExpect(jsonPath("$.items[0].runMode").value("explicit-job"))
				.andExpect(jsonPath("$.items[0].recoveryPolicy").value("rerun-from-start"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(25))
				.andExpect(jsonPath("$.totalItems").value(1));

		verify(runSummaryReadModelService).latestRunsFiltered(eq(25), isNull(), isNull(), isNull(), isNull(), eq(ZoneId.systemDefault()));
	}

	@Test
	void clampsLimitToAcceptedRange() throws Exception {
		when(runSummaryReadModelService.latestRunsFiltered(eq(200), isNull(), isNull(), isNull(), isNull(), eq(ZoneId.systemDefault()))).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/runs").param("limit", "999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(200))
				.andExpect(jsonPath("$.totalItems").value(0));

		verify(runSummaryReadModelService).latestRunsFiltered(eq(200), isNull(), isNull(), isNull(), isNull(), eq(ZoneId.systemDefault()));
	}

	@Test
	void passesJobDateAndTimezoneFiltersToService() throws Exception {
		when(runSummaryReadModelService.latestRunsFiltered(eq(50), eq("customer-load"), isNull(), isNull(), eq(LocalDate.parse("2026-05-27")), eq(ZoneId.of("UTC"))))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/v1/runs")
				.param("limit", "50")
				.param("job", "customer-load")
				.param("startDate", "2026-05-27")
				.param("timezone", "UTC"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(50));

		verify(runSummaryReadModelService).latestRunsFiltered(eq(50), eq("customer-load"), isNull(), isNull(), eq(LocalDate.parse("2026-05-27")), eq(ZoneId.of("UTC")));
	}

	@Test
	void passesRunModeAndRecoveryPolicyFiltersToService() throws Exception {
		when(runSummaryReadModelService.latestRunsFiltered(eq(25), isNull(), eq("explicit-job"), eq("rerun-from-start"), isNull(), eq(ZoneId.systemDefault())))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/v1/runs")
				.param("runMode", "explicit-job")
				.param("recoveryPolicy", "rerun-from-start"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(25));

		verify(runSummaryReadModelService).latestRunsFiltered(eq(25), isNull(), eq("explicit-job"), eq("rerun-from-start"), isNull(), eq(ZoneId.systemDefault()));
	}

	@Test
	void returnsBadRequestForInvalidStartDateFilter() throws Exception {
		mockMvc.perform(get("/api/v1/runs").param("startDate", "2026/05/27"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsBadRequestForInvalidTimezoneFilter() throws Exception {
		mockMvc.perform(get("/api/v1/runs").param("timezone", "Mars/Phobos"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsRunByJobExecutionId() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(101L))).thenReturn(Optional.of(
				new RunSummaryView("customer-load", 101L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L,
						"explicit-job", "rerun-from-start", "MANUAL", "logs/2026-05-27/customer-load.log")
		));

		mockMvc.perform(get("/api/v1/runs/101"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scenario").value("customer-load"))
				.andExpect(jsonPath("$.jobExecutionId").value(101))
				.andExpect(jsonPath("$.triggerOrigin").value("MANUAL"))
				.andExpect(jsonPath("$.runMode").value("explicit-job"))
				.andExpect(jsonPath("$.recoveryPolicy").value("rerun-from-start"));

		verify(runSummaryReadModelService).findRunByJobExecutionId(eq(101L));
	}

	@Test
	void returnsAdvisoryRecoveryViewByJobExecutionId() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(101L))).thenReturn(Optional.of(
				new RunSummaryView("customer-load", 101L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L,
						"explicit-job", "rerun-from-start", "logs/2026-05-27/customer-load.log")
		));
		when(runSummaryRegistry.findRecoveryByJobExecutionId(eq(101L))).thenReturn(Optional.of(
				new RunRecoveryView(
						101L,
						"rr-101",
						"al-101",
						"INITIAL",
						null,
						null,
						false,
						"resume-from-checkpoint is not supported in the current shipped runtime; rerun-from-start remains the active execution boundary.",
						List.of(new RunCheckpointAnchorView(
								"ca-log-101",
								null,
								"RUN_LOG",
								"logs/2026-05-27/customer-load.log",
								"COMPLETED",
								LocalDateTime.parse("2026-05-27T10:00:10"),
								LocalDateTime.parse("2026-05-27T10:00:10")
						))
				)
		));

		mockMvc.perform(get("/api/v1/runs/101/recovery"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recovery.jobExecutionId").value(101))
				.andExpect(jsonPath("$.recovery.runRecordId").value("rr-101"))
				.andExpect(jsonPath("$.recovery.resumeSupported").value(false))
				.andExpect(jsonPath("$.recovery.resumeBlockedReason").value(RunRecoveryView.RESUME_BLOCKED_REASON_CHECKPOINT_NOT_SHIPPED))
				.andExpect(jsonPath("$.recovery.checkpointAnchors[0].checkpointAnchorId").value("ca-log-101"))
				.andExpect(jsonPath("$.recovery.checkpointAnchors[0].anchorKind").value("RUN_LOG"));

		verify(runSummaryReadModelService).findRunByJobExecutionId(eq(101L));
		verify(runSummaryRegistry).findRecoveryByJobExecutionId(eq(101L));
	}

	@Test
	void returnsDefaultAdvisoryRecoveryWhenRegistryHasNoRecoveryRow() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(102L))).thenReturn(Optional.of(
				new RunSummaryView("customer-load", 102L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L,
						"explicit-job", "rerun-from-start", "logs/2026-05-27/customer-load.log")
		));
		when(runSummaryRegistry.findRecoveryByJobExecutionId(eq(102L))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/runs/102/recovery"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recovery.jobExecutionId").value(102))
				.andExpect(jsonPath("$.recovery.runRecordId").value("rr-102"))
				.andExpect(jsonPath("$.recovery.resumeSupported").value(false))
				.andExpect(jsonPath("$.recovery.resumeBlockedReason").value(RunRecoveryView.RESUME_BLOCKED_REASON_CHECKPOINT_NOT_SHIPPED))
				.andExpect(jsonPath("$.recovery.checkpointAnchors[0].checkpointAnchorId").value("ca-log-102"));

		verify(runSummaryReadModelService).findRunByJobExecutionId(eq(102L));
		verify(runSummaryRegistry).findRecoveryByJobExecutionId(eq(102L));
	}

	@Test
	void returnsNotFoundWhenRunIdDoesNotExist() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(999L))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/runs/999"))
				.andExpect(status().isNotFound());

		verify(runSummaryReadModelService).findRunByJobExecutionId(eq(999L));
	}

	@Test
	void returnsNotFoundWhenRecoveryRunIdDoesNotExist() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(999L))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/runs/999/recovery"))
				.andExpect(status().isNotFound());

		verify(runSummaryReadModelService).findRunByJobExecutionId(eq(999L));
	}

	@Test
	void returnsRunDetailByJobExecutionId() throws Exception {
		when(runDetailReadModelService.findRunDetailByJobExecutionId(eq(101L))).thenReturn(Optional.of(
				new RunDetailView(
						new RunSummaryView("customer-load", 101L, "FAILED", LocalDateTime.parse("2026-05-27T10:00:00"),
								LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 8L, 2L, "logs/2026-05-27/customer-load.log"),
						List.of(new StepRecordView("normalize-orders", 1, "COMPLETED", 201L, 10L, 8L, 0L, 0L, 0L, 2L,
								LocalDateTime.parse("2026-05-27T10:00:01"), LocalDateTime.parse("2026-05-27T10:00:05"),
								"normalize-orders-subflow", "Normalize orders step")),
						List.of(new ArtifactRecordView("reject-201", "reject-output", "Rejected records for normalize-orders",
								"output/rejects/orders.csv", LocalDateTime.parse("2026-05-27T10:00:05"), 2L, "normalize-orders")),
						new FailureSummaryView("config", "ConfigException", "boom", "boom"),
						List.of(new EvidenceLinkView("Scenario log", "logs/2026-05-27/customer-load.log", "log-file"))
				)
		));

		mockMvc.perform(get("/api/v1/runs/101/detail"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.run.jobExecutionId").value(101))
				.andExpect(jsonPath("$.steps[0].stepName").value("normalize-orders"))
				.andExpect(jsonPath("$.artifacts[0].role").value("reject-output"))
				.andExpect(jsonPath("$.failureSummary.category").value("config"))
				.andExpect(jsonPath("$.evidenceLinks[0].href").value("logs/2026-05-27/customer-load.log"));

		verify(runDetailReadModelService).findRunDetailByJobExecutionId(eq(101L));
	}

	@Test
	void returnsPersistedStepRecordsByRunId() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(101L))).thenReturn(Optional.of(
				new RunSummaryView("customer-load", 101L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L, "logs/2026-05-27/customer-load.log")
		));
		when(runSummaryRegistry.listStepRecordsByJobExecutionId(eq(101L), eq(25))).thenReturn(List.of(
				new RunStepRecordView("sr-101-1", "rr-101", "load-customers", "COMPLETED",
						LocalDateTime.parse("2026-05-27T10:00:01"), LocalDateTime.parse("2026-05-27T10:00:05"), 4L,
						10L, 10L, 0L, null, 0L, 0L)
		));

		mockMvc.perform(get("/api/v1/runs/101/step-records"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].stepRecordId").value("sr-101-1"))
				.andExpect(jsonPath("$.items[0].stepName").value("load-customers"))
				.andExpect(jsonPath("$.size").value(25));

		verify(runSummaryReadModelService).findRunByJobExecutionId(eq(101L));
		verify(runSummaryRegistry).listStepRecordsByJobExecutionId(eq(101L), eq(25));
	}

	@Test
	void returnsPersistedArtifactRecordsByRunIdWithClampedLimit() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(101L))).thenReturn(Optional.of(
				new RunSummaryView("customer-load", 101L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L, "logs/2026-05-27/customer-load.log")
		));
		when(runSummaryRegistry.listArtifactRecordsByJobExecutionId(eq(101L), eq(200))).thenReturn(List.of(
				new RunArtifactRecordView("ar-log-101", "rr-101", null, "RUN_LOG", "logs/2026-05-27/customer-load.log",
						LocalDateTime.parse("2026-05-27T10:00:10"))
		));

		mockMvc.perform(get("/api/v1/runs/101/artifact-records").param("limit", "999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].artifactRecordId").value("ar-log-101"))
				.andExpect(jsonPath("$.items[0].artifactRole").value("RUN_LOG"))
				.andExpect(jsonPath("$.size").value(200));

		verify(runSummaryReadModelService).findRunByJobExecutionId(eq(101L));
		verify(runSummaryRegistry).listArtifactRecordsByJobExecutionId(eq(101L), eq(200));
	}

	@Test
	void returnsNotFoundWhenPersistedStepRecordsRunIsMissing() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(999L))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/runs/999/step-records"))
				.andExpect(status().isNotFound());

		verify(runSummaryReadModelService).findRunByJobExecutionId(eq(999L));
	}

	@Test
	void returnsNotFoundWhenRunDetailDoesNotExist() throws Exception {
		when(runDetailReadModelService.findRunDetailByJobExecutionId(eq(999L))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/runs/999/detail"))
				.andExpect(status().isNotFound());

		verify(runDetailReadModelService).findRunDetailByJobExecutionId(eq(999L));
	}

	@Test
	void returnsRunScopedLogByJobExecutionId() throws Exception {
		when(runScopedLogReadModelService.findRunScopedLogByJobExecutionId(eq(101L), eq(50))).thenReturn(Optional.of(
				new RunScopedLogView(
						101L,
						"customer-load",
						"logs/2026-05-27/customer-load.log",
						2,
						false,
						List.of(
								new RunLogLineView(2, LocalDateTime.parse("2026-05-27T10:00:00"), "INFO", "STEP_EVENT", "step_started", "line 1", true),
								new RunLogLineView(3, LocalDateTime.parse("2026-05-27T10:00:05"), "ERROR", "JOB_FAILURE", "job_failure", "line 2", true)
						)
				)
		));

		mockMvc.perform(get("/api/v1/runs/101/log").param("limit", "50"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobExecutionId").value(101))
				.andExpect(jsonPath("$.scenario").value("customer-load"))
				.andExpect(jsonPath("$.lines[0].recordType").value("STEP_EVENT"))
				.andExpect(jsonPath("$.lines[1].level").value("ERROR"));

		verify(runScopedLogReadModelService).findRunScopedLogByJobExecutionId(eq(101L), eq(50));
	}

	@Test
	void returnsNotFoundWhenRunScopedLogDoesNotExist() throws Exception {
		when(runScopedLogReadModelService.findRunScopedLogByJobExecutionId(eq(999L), eq(null))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/runs/999/log"))
				.andExpect(status().isNotFound());

		verify(runScopedLogReadModelService).findRunScopedLogByJobExecutionId(eq(999L), eq(null));
	}
}





