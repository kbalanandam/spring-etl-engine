package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.monitoring.RunSummaryView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

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

	@MockBean
	private RunSummaryReadModelService runSummaryReadModelService;

	@Test
	void returnsRunsUsingDefaultLimit() throws Exception {
		when(runSummaryReadModelService.latestRuns(eq(25))).thenReturn(List.of(
				new RunSummaryView("customer-load", 101L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L, "logs/2026-05-27/customer-load.log")
		));

		mockMvc.perform(get("/api/v1/runs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].scenario").value("customer-load"))
				.andExpect(jsonPath("$.items[0].jobExecutionId").value(101));

		verify(runSummaryReadModelService).latestRuns(eq(25));
	}

	@Test
	void clampsLimitToAcceptedRange() throws Exception {
		when(runSummaryReadModelService.latestRuns(eq(200))).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/runs").param("limit", "999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray());

		verify(runSummaryReadModelService).latestRuns(eq(200));
	}
}


