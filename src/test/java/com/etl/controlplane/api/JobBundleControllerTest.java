package com.etl.controlplane.api;

import com.etl.controlplane.jobs.JobBundleReadModelService;
import com.etl.controlplane.jobs.JobBundleSummaryView;
import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.monitoring.RunSummaryView;
import com.etl.controlplane.triggers.TriggerEventRegistry;
import com.etl.controlplane.triggers.TriggerEventView;
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
import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobBundleController.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.main.web-application-type=servlet")
class JobBundleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JobBundleReadModelService jobBundleReadModelService;

	@MockitoBean
	private RunSummaryReadModelService runSummaryReadModelService;

	@MockitoBean
	private TriggerEventRegistry triggerEventRegistry;

	@Test
	void returnsBundleList() throws Exception {
		when(jobBundleReadModelService.listBundles()).thenReturn(List.of(
				new JobBundleSummaryView("customer-load", "Customer Load",
						"src/main/resources/config-jobs/customer-load/job-config.yaml", "READY", List.of())
		));

		mockMvc.perform(get("/api/v1/jobs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].jobKey").value("customer-load"))
				.andExpect(jsonPath("$.items[0].readinessStatus").value("READY"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalItems").value(1));

		verify(jobBundleReadModelService).listBundles();
	}

	@Test
	void returnsBundleDetail() throws Exception {
		when(jobBundleReadModelService.findBundle(eq("customer-load"))).thenReturn(Optional.of(
				new JobBundleSummaryView("customer-load", "Customer Load",
						"src/main/resources/config-jobs/customer-load/job-config.yaml", "READY", List.of())
		));
		when(runSummaryReadModelService.latestRunsForJob(eq("customer-load"), eq("Customer Load"), eq(10))).thenReturn(List.of(
				new RunSummaryView("Customer Load", 101L, "COMPLETED", LocalDateTime.parse("2026-05-27T10:00:00"),
						LocalDateTime.parse("2026-05-27T10:00:10"), 10L, 10L, 10L, 0L,
						"explicit-job", "rerun-from-start", "logs/2026-05-27/customer-load.log")
		));
		when(triggerEventRegistry.listByJobKey(eq("customer-load"), eq(20))).thenReturn(List.of(
				new TriggerEventView("te-123", "customer-load", "ACCEPTED", "manual_operator_request", "operator@example", Instant.parse("2026-05-27T10:15:30Z"), null, "accepted")
		));

		mockMvc.perform(get("/api/v1/jobs/customer-load"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.job.jobKey").value("customer-load"))
				.andExpect(jsonPath("$.recentRuns[0].jobExecutionId").value(101))
				.andExpect(jsonPath("$.recentRuns[0].runMode").value("explicit-job"))
				.andExpect(jsonPath("$.recentRuns[0].recoveryPolicy").value("rerun-from-start"))
				.andExpect(jsonPath("$.recentTriggerEvents[0].triggerEventId").value("te-123"));

		verify(jobBundleReadModelService).findBundle(eq("customer-load"));
		verify(runSummaryReadModelService).latestRunsForJob(eq("customer-load"), eq("Customer Load"), eq(10));
		verify(triggerEventRegistry).listByJobKey(eq("customer-load"), eq(20));
	}

	@Test
	void returnsNotFoundWhenJobDoesNotExist() throws Exception {
		when(jobBundleReadModelService.findBundle(eq("missing-job"))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/jobs/missing-job"))
				.andExpect(status().isNotFound());

		verify(jobBundleReadModelService).findBundle(eq("missing-job"));
	}

	@Test
	void triggerNowReturnsAcceptedPlaceholderForKnownJob() throws Exception {
		when(jobBundleReadModelService.findBundle(eq("customer-load"))).thenReturn(Optional.of(
				new JobBundleSummaryView("customer-load", "Customer Load",
						"src/main/resources/config-jobs/customer-load/job-config.yaml", "READY", List.of())
		));
		when(triggerEventRegistry.recordAccepted(eq("customer-load"), eq("manual_operator_request"), eq("operator@example"), eq("Trigger request accepted as placeholder for reason='manual_operator_request' requestedBy='operator@example'.")))
				.thenReturn(new TriggerEventView("te-123", "customer-load", "ACCEPTED", "manual_operator_request", "operator@example", Instant.parse("2026-05-27T10:15:30Z"), null, "Trigger request accepted as placeholder for reason='manual_operator_request' requestedBy='operator@example'."));

		mockMvc.perform(post("/api/v1/jobs/customer-load:trigger-now")
						.contentType("application/json")
						.content("{\"reason\":\"manual_operator_request\",\"requestedBy\":\"operator@example\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.jobKey").value("customer-load"))
				.andExpect(jsonPath("$.decisionStatus").value("ACCEPTED"))
				.andExpect(jsonPath("$.triggerEventId").value("te-123"));

		verify(jobBundleReadModelService).findBundle(eq("customer-load"));
		verify(triggerEventRegistry).recordAccepted(eq("customer-load"), eq("manual_operator_request"), eq("operator@example"), eq("Trigger request accepted as placeholder for reason='manual_operator_request' requestedBy='operator@example'."));
	}

	@Test
	void triggerNowReturnsNotFoundForUnknownJob() throws Exception {
		when(jobBundleReadModelService.findBundle(eq("missing-job"))).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/v1/jobs/missing-job:trigger-now"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.decisionStatus").value("NOT_FOUND"));

		verify(jobBundleReadModelService).findBundle(eq("missing-job"));
	}

	@Test
	void returnsTriggerEventsForKnownJob() throws Exception {
		when(jobBundleReadModelService.findBundle(eq("customer-load"))).thenReturn(Optional.of(
				new JobBundleSummaryView("customer-load", "Customer Load",
						"src/main/resources/config-jobs/customer-load/job-config.yaml", "READY", List.of())
		));
		when(triggerEventRegistry.listByJobKey(eq("customer-load"), eq(20))).thenReturn(List.of(
				new TriggerEventView("te-123", "customer-load", "ACCEPTED", "manual_operator_request", "operator@example", Instant.parse("2026-05-27T10:15:30Z"), null, "accepted")
		));

		mockMvc.perform(get("/api/v1/jobs/customer-load/trigger-events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].triggerEventId").value("te-123"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalItems").value(1));

		verify(jobBundleReadModelService).findBundle(eq("customer-load"));
		verify(triggerEventRegistry).listByJobKey(eq("customer-load"), eq(20));
	}

	@Test
	void triggerEventsEndpointClampsLimit() throws Exception {
		when(jobBundleReadModelService.findBundle(eq("customer-load"))).thenReturn(Optional.of(
				new JobBundleSummaryView("customer-load", "Customer Load",
						"src/main/resources/config-jobs/customer-load/job-config.yaml", "READY", List.of())
		));
		when(triggerEventRegistry.listByJobKey(eq("customer-load"), eq(200))).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/jobs/customer-load/trigger-events").param("limit", "999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(200));

		verify(triggerEventRegistry).listByJobKey(eq("customer-load"), eq(200));
	}

	@Test
	void returnsNotFoundForTriggerEventsWhenJobDoesNotExist() throws Exception {
		when(jobBundleReadModelService.findBundle(eq("missing-job"))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/jobs/missing-job/trigger-events"))
				.andExpect(status().isNotFound());

		verify(jobBundleReadModelService).findBundle(eq("missing-job"));
	}
}






