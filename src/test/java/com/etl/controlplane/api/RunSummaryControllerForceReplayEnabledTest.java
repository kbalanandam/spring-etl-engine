package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunDetailReadModelService;
import com.etl.controlplane.monitoring.RunScopedLogReadModelService;
import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.monitoring.RunSummaryRegistry;
import com.etl.controlplane.triggers.TriggerSourceCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RunSummaryController.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"spring.main.web-application-type=servlet",
		"controlplane.runs.allow-force-refresh=true"
})
class RunSummaryControllerForceReplayEnabledTest {

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

	@MockitoBean
	private TriggerSourceCatalog triggerSourceCatalog;

	@BeforeEach
	void setUp() {
		when(runSummaryReadModelService.freshnessSnapshot())
				.thenReturn(new RunSummaryReadModelService.ReadModelFreshness(true, 456L));
	}

	@Test
	void appliesForceReplayWhenRefreshAndForceReplayAreTrue() throws Exception {
		when(runSummaryReadModelService.latestRunsFilteredFresh(eq(25), isNull(), isNull(), isNull(), isNull(), isNull(), eq(ZoneId.systemDefault())))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/v1/runs")
				.param("refresh", "true")
				.param("forceReplay", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.freshness.refreshRequested").value(true))
				.andExpect(jsonPath("$.freshness.forceReplayApplied").value(true))
				.andExpect(jsonPath("$.freshness.reindexInProgress").value(true))
				.andExpect(jsonPath("$.freshness.lastReindexEpochMs").value(456));

		verify(runSummaryReadModelService).latestRunsFilteredFresh(eq(25), isNull(), isNull(), isNull(), isNull(), isNull(), eq(ZoneId.systemDefault()));
	}
}

