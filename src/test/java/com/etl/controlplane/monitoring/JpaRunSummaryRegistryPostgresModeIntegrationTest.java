package com.etl.controlplane.monitoring;

import com.etl.controlplane.ControlPlaneApiApplication;
import com.etl.controlplane.persistence.jpa.entity.ArtifactRecord;
import com.etl.controlplane.persistence.jpa.entity.RunRecord;
import com.etl.controlplane.persistence.jpa.entity.StepRecord;
import com.etl.controlplane.persistence.jpa.repository.ArtifactRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.RunRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.StepRecordRepository;
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
                "spring.datasource.url=jdbc:h2:mem:jpa-run-summary-postgres;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false"
        }
)
class JpaRunSummaryRegistryPostgresModeIntegrationTest {

    @Autowired
    private RunSummaryRegistry runSummaryRegistry;

    @Autowired
    private RunRecordRepository runRecordRepository;

    @Autowired
    private StepRecordRepository stepRecordRepository;

    @Autowired
    private ArtifactRecordRepository artifactRecordRepository;

    @Test
    void persistsAndReadsRunSummaryAndRecoveryInPostgresCompatibilityMode() {
        long jobExecutionId = 801L;
        RunSummaryView runSummary = new RunSummaryView(
                "customer-load",
                jobExecutionId,
                "COMPLETED",
                LocalDateTime.of(2026, 8, 19, 14, 0),
                LocalDateTime.of(2026, 8, 19, 14, 2),
                120L,
                30L,
                29L,
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

        RunRecord runRecord = runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(jobExecutionId).orElseThrow();
        seedStepAndArtifact(runRecord, "postgres");

        List<RunStepRecordView> stepRecords = runSummaryRegistry.listStepRecordsByJobExecutionId(jobExecutionId, 10);
        assertEquals(1, stepRecords.size());
        assertEquals("extract-orders-postgres", stepRecords.get(0).stepName());
        assertEquals("rr-" + jobExecutionId, stepRecords.get(0).runRecordId());

        List<RunArtifactRecordView> artifactsByRun = runSummaryRegistry.listArtifactRecordsByJobExecutionId(jobExecutionId, 10);
        assertEquals(1, artifactsByRun.size());
        assertEquals("STEP_REJECT_OUTPUT", artifactsByRun.get(0).artifactRole());
        assertEquals("rr-" + jobExecutionId, artifactsByRun.get(0).runRecordId());

        List<RunArtifactRecordView> artifactsByStep = runSummaryRegistry.listArtifactRecordsByStepRecordId("sr-" + runRecord.getRunRecordPk() + "-postgres", 10);
        assertEquals(1, artifactsByStep.size());
        assertEquals("STEP_REJECT_OUTPUT", artifactsByStep.get(0).artifactRole());
    }

    private void seedStepAndArtifact(RunRecord runRecord, String suffix) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 14, 3);

        StepRecord stepRecord = new StepRecord();
        stepRecord.setStepRecordPk(11100L + runRecord.getRunRecordPk());
        stepRecord.setStepRecordId("sr-" + runRecord.getRunRecordPk() + "-" + suffix);
        stepRecord.setRunRecordPk(runRecord.getRunRecordPk());
        stepRecord.setStepName("extract-orders-" + suffix);
        stepRecord.setStepStatus("COMPLETED");
        stepRecord.setStartedAt(now.minusMinutes(1));
        stepRecord.setFinishedAt(now);
        stepRecord.setDurationSeconds(60L);
        stepRecord.setReadCount(30L);
        stepRecord.setWriteCount(29L);
        stepRecord.setFilterCount(1L);
        stepRecord.setSkipCount(0L);
        stepRecord.setRollbackCount(0L);
        stepRecord.setRejectedCount(1L);
        stepRecord.setCreatedAt(now);
        stepRecord.setUpdatedAt(now);
        stepRecord.setCreatedBy("test");
        stepRecord.setUpdatedBy("test");
        stepRecordRepository.save(stepRecord);

        ArtifactRecord artifactRecord = new ArtifactRecord();
        artifactRecord.setArtifactRecordPk(11200L + runRecord.getRunRecordPk());
        artifactRecord.setArtifactRecordId("ar-" + runRecord.getRunRecordPk() + "-" + suffix);
        artifactRecord.setRunRecordPk(runRecord.getRunRecordPk());
        artifactRecord.setStepRecordId(stepRecord.getStepRecordId());
        artifactRecord.setArtifactRole("STEP_REJECT_OUTPUT");
        artifactRecord.setArtifactPath("C:/output/rejects/orders-" + suffix + ".csv");
        artifactRecord.setCreatedAt(now);
        artifactRecord.setUpdatedAt(now);
        artifactRecord.setCreatedBy("test");
        artifactRecord.setUpdatedBy("test");
        artifactRecordRepository.save(artifactRecord);
    }
}

