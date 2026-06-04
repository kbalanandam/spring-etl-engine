package com.etl.controlplane.integration;

import com.etl.controlplane.ControlPlaneApiApplication;
import com.etl.controlplane.api.JobBundleController;
import com.etl.controlplane.api.RunSummaryController;
import com.etl.controlplane.api.ScheduleController;
import com.etl.controlplane.api.SystemController;
import com.etl.controlplane.ui.OperatorUiController;
import com.etl.runner.EtlJobRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ControlPlaneApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("controlplane")
class ControlPlaneApiLauncherIntegrationTest {

	private static final Path TEST_ROOT;
	private static final Path JOBS_ROOT;
	private static final Path LOG_ROOT;
	private static final Path DB_PATH;

	static {
		try {
			TEST_ROOT = Files.createTempDirectory("controlplane-api-int-");
			JOBS_ROOT = TEST_ROOT.resolve("config-jobs");
			LOG_ROOT = TEST_ROOT.resolve("logs");
			DB_PATH = TEST_ROOT.resolve("controlplane.db");
		} catch (IOException ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("controlplane.jobs.root", () -> JOBS_ROOT.toString());
		registry.add("etl.logging.base-dir", () -> LOG_ROOT.toString());
		registry.add("controlplane.triggers.persistence.mode", () -> "jdbc");
		registry.add("controlplane.runs.persistence.mode", () -> "jdbc");
		registry.add("controlplane.schedules.persistence.mode", () -> "jdbc");
		registry.add("controlplane.scheduler.enabled", () -> "false");
		registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath().toString().replace('\\', '/'));
		registry.add("spring.datasource.username", () -> "");
		registry.add("spring.datasource.password", () -> "");
		registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
		registry.add("spring.sql.init.mode", () -> "never");
		registry.add("spring.batch.jdbc.initialize-schema", () -> "never");
		registry.add("spring.sql.init.schema-locations", () -> "");
		registry.add("controlplane.triggers.persistence.mode-marker-path",
				() -> TEST_ROOT.resolve("trigger-persistence-mode.marker").toString());
	}

	@Autowired
	private JobBundleController jobBundleController;

	@Autowired
	private RunSummaryController runSummaryController;

	@Autowired
	private ScheduleController scheduleController;

	@Autowired
	private SystemController systemController;

	@Autowired
	private OperatorUiController operatorUiController;

	private MockMvc mockMvc;

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeAll
	static void setupFixtures() throws IOException {
		createJobBundle();
		createScenarioLog();
	}

	@BeforeEach
	void setupMockMvc() {
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(jobBundleController, runSummaryController, scheduleController, systemController, operatorUiController)
				.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
				.build();
	}

	@Test
	void startsWithoutEtlWorkerBeans() {
		assertEquals(0, applicationContext.getBeanNamesForType(EtlJobRunner.class).length);
	}

	@Test
	void jobsRunsAndSchedulesEndpointsWorkEndToEndWithJdbcPersistence() throws Exception {
		mockMvc.perform(get("/api/v1/jobs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(1))
				.andExpect(jsonPath("$.items[0].jobKey").value("customer-load"));

		mockMvc.perform(post("/api/v1/jobs/customer-load:trigger-now")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\":\"manual_operator_request\",\"requestedBy\":\"integration-test\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.decisionStatus").value("ACCEPTED"));

		mockMvc.perform(get("/api/v1/jobs/customer-load/trigger-events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(1));

		mockMvc.perform(get("/api/v1/runs").param("limit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(1))
				.andExpect(jsonPath("$.items[0].jobExecutionId").value(901))
				.andExpect(jsonPath("$.items[0].runMode").value("explicit-job"))
				.andExpect(jsonPath("$.items[0].recoveryPolicy").value("rerun-from-start"));

		mockMvc.perform(get("/api/v1/runs")
				.param("job", "customer-load")
				.param("startDate", "2026-05-27")
				.param("timezone", "UTC"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(1));

		mockMvc.perform(get("/api/v1/runs")
				.param("startDate", "2026-05-28")
				.param("timezone", "UTC"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(0));

		mockMvc.perform(get("/api/v1/runs/901/detail"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.run.jobExecutionId").value(901))
				.andExpect(jsonPath("$.evidenceLinks[1].href").value(LOG_ROOT.resolve("2026-05-27/customer-load.log").toString() + "#L2"));

		mockMvc.perform(get("/api/v1/runs/901/step-records").param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray());

		mockMvc.perform(get("/api/v1/runs/901/artifact-records").param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(1))
				.andExpect(jsonPath("$.items[0].artifactRole").value("RUN_LOG"));

		mockMvc.perform(get("/api/v1/runs/901/log").param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobExecutionId").value(901))
				.andExpect(jsonPath("$.lines[0].recordType").value("STEP_EVENT"))
				.andExpect(jsonPath("$.lines[1].recordType").value("RUN_SUMMARY"));

		mockMvc.perform(get("/api/v1/system/info"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.schedulerEnabled").value(false))
				.andExpect(jsonPath("$.schedulerMissedRunPolicy").value("SKIP"))
				.andExpect(jsonPath("$.schedulerOverlapPolicy").value("ALLOW"));

		MvcResult createScheduleResult = mockMvc.perform(post("/api/v1/schedules")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"scheduleKey\":\"daily-customers\",\"selectedJobKey\":\"customer-load\",\"expression\":\"0 0 * * *\",\"timezone\":\"UTC\",\"enabled\":true,\"description\":\"daily\"}"))
				.andExpect(status().isCreated())
				.andReturn();

		String scheduleId = readScheduleId(createScheduleResult);
		assertNotNull(scheduleId);
		assertTrue(!scheduleId.isBlank());

		mockMvc.perform(get("/api/v1/schedules/" + scheduleId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scheduleKey").value("daily-customers"))
				.andExpect(jsonPath("$.lastAcceptedDueAt").isEmpty())
				.andExpect(jsonPath("$.nextDueAt").exists());

		mockMvc.perform(get("/api/v1/schedules/" + scheduleId + "/trigger-events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(0));

		mockMvc.perform(get("/operator"))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertTrue(result.getResponse().getRedirectedUrl().startsWith("/operator/index.html?v=")));
	}

	private String readScheduleId(MvcResult result) throws IOException {
		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode scheduleId = root.get("scheduleId");
		return scheduleId == null ? null : scheduleId.asText();
	}

	private static void createJobBundle() throws IOException {
		Path bundleDir = JOBS_ROOT.resolve("customer-load");
		Files.createDirectories(bundleDir);
		Files.write(bundleDir.resolve("job-config.yaml"), List.of(
				"name: Customer Load",
				"steps:",
				"  - name: load-customers",
				"    source: customer-source",
				"    target: customer-target"
		));
	}

	private static void createScenarioLog() throws IOException {
		Path logFile = LOG_ROOT.resolve("2026-05-27/customer-load.log");
		Files.createDirectories(logFile.getParent());
		Files.write(logFile, List.of(
				"2026-05-27T10:00:01.000+00:00 INFO [main] [scenario:Customer Load] [run:run-901] [job:901] [step:load-customers] logger - STEP_EVENT event=step_finished stepName=load-customers stepExecutionId=5001 status=COMPLETED readCount=10 writeCount=10 filterCount=0 skipCount=0 rollbackCount=0 rejectedCount=0 rejectOutputPath= archivedSourcePath=",
				"2026-05-27T10:00:02.000+00:00 INFO [main] [scenario:Customer Load] [run:run-901] [job:901] [step:n/a] logger - RUN_SUMMARY event=run_summary scenario=Customer Load runMode=explicit-job recoveryPolicy=rerun-from-start jobExecutionId=901 status=COMPLETED startTime=2026-05-27T10:00:00 endTime=2026-05-27T10:00:02 durationSeconds=2 sourceCount=10 writtenCount=10 rejectedCount=0"
		));
	}
}




