package com.etl.controlplane.api;

import com.etl.controlplane.jobs.JobBundleReadModelService;
import com.etl.controlplane.jobs.JobBundleSummaryView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobBundleController.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.main.web-application-type=servlet")
class JobBundleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private JobBundleReadModelService jobBundleReadModelService;

	@Test
	void returnsBundleList() throws Exception {
		when(jobBundleReadModelService.listBundles()).thenReturn(List.of(
				new JobBundleSummaryView("customer-load", "Customer Load",
						"src/main/resources/config-jobs/customer-load/job-config.yaml", "READY", List.of())
		));

		mockMvc.perform(get("/api/v1/jobs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].jobKey").value("customer-load"))
				.andExpect(jsonPath("$.items[0].readinessStatus").value("READY"));

		verify(jobBundleReadModelService).listBundles();
	}

	@Test
	void returnsBundleDetail() throws Exception {
		when(jobBundleReadModelService.findBundle(eq("customer-load"))).thenReturn(Optional.of(
				new JobBundleSummaryView("customer-load", "Customer Load",
						"src/main/resources/config-jobs/customer-load/job-config.yaml", "READY", List.of())
		));

		mockMvc.perform(get("/api/v1/jobs/customer-load"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobKey").value("customer-load"));

		verify(jobBundleReadModelService).findBundle(eq("customer-load"));
	}

	@Test
	void returnsNotFoundWhenJobDoesNotExist() throws Exception {
		when(jobBundleReadModelService.findBundle(eq("missing-job"))).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/jobs/missing-job"))
				.andExpect(status().isNotFound());

		verify(jobBundleReadModelService).findBundle(eq("missing-job"));
	}
}

