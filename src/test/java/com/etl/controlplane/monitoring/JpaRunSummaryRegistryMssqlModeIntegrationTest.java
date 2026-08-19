package com.etl.controlplane.monitoring;

import com.etl.controlplane.ControlPlaneApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
		classes = ControlPlaneApiApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.main.web-application-type=none",
				"controlplane.scheduler.enabled=false",
				"controlplane.job-launch.enabled=false",
				"controlplane.triggers.persistence.mode=jpa",
				"controlplane.runs.persistence.mode=jpa",
				"controlplane.schedules.persistence.mode=jpa",
				"spring.datasource.url=jdbc:h2:mem:jpa-run-summary-mssql;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
				"spring.datasource.username=sa",
				"spring.datasource.password=",
				"spring.datasource.driver-class-name=org.h2.Driver",
				"spring.jpa.hibernate.ddl-auto=create-drop",
				"spring.flyway.enabled=false"
		}
)
class JpaRunSummaryRegistryMssqlModeIntegrationTest {

	@Autowired
	private RunSummaryRegistry runSummaryRegistry;

	@Test
	void persistsAndReadsRunSummaryAndRecoveryInSqlServerCompatibilityMode() {
		long jobExecutionId = 601L;
		RunSummaryView runSummary = new RunSummaryView(
				"customer-load",
				jobExecutionId,
				"COMPLETED",
				LocalDateTime.of(2026, 8, 19, 11, 0),
				LocalDateTime.of(2026, 8, 19, 11, 2),
				120L,
				20L,
				19L,
				1L,
				"explicit-job",
				"rerun-from-start",
				"C:/logs/2026-08-19/customer-load.log"
		);

		runSummaryRegistry.upsert(runSummary);

		List<RunSummaryView> latest = runSummaryRegistry.latestRuns(5);
		assertFalse(latest.isEmpty());
		assertTrue(latest.stream().anyMatch(view -> jobExecutionId == view.jobExecutionId()));

		RunSummaryView loaded = runSummaryRegistry.findByJobExecutionId(jobExecutionId).orElseThrow();
		assertEquals("COMPLETED", loaded.status());
		assertEquals("customer-load", loaded.scenario());

		RunRecoveryView recovery = runSummaryRegistry.findRecoveryByJobExecutionId(jobExecutionId).orElseThrow();
		assertEquals("rr-" + jobExecutionId, recovery.runRecordId());
		assertFalse(recovery.checkpointAnchors().isEmpty());
	}
}

