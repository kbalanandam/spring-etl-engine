package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunDetailReadModelService;
import com.etl.controlplane.monitoring.RunDetailView;
import com.etl.controlplane.monitoring.EvidenceLinkView;
import com.etl.controlplane.monitoring.StepRecordView;
import com.etl.controlplane.monitoring.ArtifactRecordView;
import com.etl.controlplane.monitoring.FailureSummaryView;
import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.monitoring.RunSummaryView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
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
	private RunDetailReadModelService runDetailReadModelService;

	@Test
	void returnsRunsUsingDefaultLimit() throws Exception {
		when(runSummaryReadModelService.latestRuns(eq(25))).thenReturn(List.of(
				new RunSummaryView("customer-load", 101L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L, "logs/2026-05-27/customer-load.log")
		));

		mockMvc.perform(get("/api/v1/runs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].scenario").value("customer-load"))
				.andExpect(jsonPath("$.items[0].jobExecutionId").value(101))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(25))
				.andExpect(jsonPath("$.totalItems").value(1));

		verify(runSummaryReadModelService).latestRuns(eq(25));
	}

	@Test
	void clampsLimitToAcceptedRange() throws Exception {
		when(runSummaryReadModelService.latestRuns(eq(200))).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/runs").param("limit", "999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(200))
				.andExpect(jsonPath("$.totalItems").value(0));

		verify(runSummaryReadModelService).latestRuns(eq(200));
	}

	@Test
	void returnsRunByJobExecutionId() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(101L))).thenReturn(Optional.of(
				new RunSummaryView("customer-load", 101L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L, "logs/2026-05-27/customer-load.log")
		));

		mockMvc.perform(get("/api/v1/runs/101"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scenario").value("customer-load"))
				.andExpect(jsonPath("$.jobExecutionId").value(101));

		verify(runSummaryReadModelService).findRunByJobExecutionId(eq(101L));
	}

	@Test
	void returnsNotFoundWhenRunIdDoesNotExist() throws Exception {
		when(runSummaryReadModelService.findRunByJobExecutionId(eq(999L))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/runs/999"))
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
	void returnsNotFoundWhenRunDetailDoesNotExist() throws Exception {
		when(runDetailReadModelService.findRunDetailByJobExecutionId(eq(999L))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/runs/999/detail"))
				.andExpect(status().isNotFound());

		verify(runDetailReadModelService).findRunDetailByJobExecutionId(eq(999L));
	}
}





