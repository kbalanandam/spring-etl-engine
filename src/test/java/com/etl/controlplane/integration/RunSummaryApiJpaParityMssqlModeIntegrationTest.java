package com.etl.controlplane.integration;

import com.etl.controlplane.ControlPlaneApiApplication;
import com.etl.controlplane.monitoring.RunSummaryRegistry;
import com.etl.controlplane.monitoring.RunSummaryView;
import com.etl.controlplane.persistence.jpa.entity.ArtifactRecord;
import com.etl.controlplane.persistence.jpa.entity.RunRecord;
import com.etl.controlplane.persistence.jpa.entity.StepRecord;
import com.etl.controlplane.persistence.jpa.repository.ArtifactRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.RunRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.StepRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ControlPlaneApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "controlplane.scheduler.enabled=false",
                "controlplane.job-launch.enabled=false",
                "controlplane.triggers.persistence.mode=jpa",
                "controlplane.runs.persistence.mode=jpa",
                "controlplane.schedules.persistence.mode=jpa",
                "spring.datasource.url=jdbc:h2:mem:run-summary-api-jpa-parity-mssql;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false"
        }
)
@AutoConfigureMockMvc
class RunSummaryApiJpaParityMssqlModeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RunSummaryRegistry runSummaryRegistry;

    @Autowired
    private RunRecordRepository runRecordRepository;

    @Autowired
    private StepRecordRepository stepRecordRepository;

    @Autowired
    private ArtifactRecordRepository artifactRecordRepository;

    @Test
    void stepAndArtifactEndpointsReturnJpaBackedParityShapeInMssqlMode() throws Exception {
        long jobExecutionId = 901L;
        runSummaryRegistry.upsert(new RunSummaryView(
                "customer-load",
                jobExecutionId,
                "COMPLETED",
                LocalDateTime.of(2026, 8, 19, 16, 0),
                LocalDateTime.of(2026, 8, 19, 16, 2),
                120L,
                40L,
                39L,
                1L,
                "explicit-job",
                "rerun-from-start",
                "C:/logs/2026-08-19/customer-load.log"
        ));

        RunRecord runRecord = runRecordRepository
                .findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(jobExecutionId)
                .orElseThrow();

        StepRecord stepRecord = new StepRecord();
        stepRecord.setStepRecordPk(16001L);
        stepRecord.setStepRecordId("sr-" + runRecord.getRunRecordPk() + "-api-mssql");
        stepRecord.setRunRecordPk(runRecord.getRunRecordPk());
        stepRecord.setStepName("load-customers");
        stepRecord.setStepStatus("COMPLETED");
        stepRecord.setStartedAt(LocalDateTime.of(2026, 8, 19, 16, 0, 10));
        stepRecord.setFinishedAt(LocalDateTime.of(2026, 8, 19, 16, 1, 40));
        stepRecord.setDurationSeconds(90L);
        stepRecord.setReadCount(40L);
        stepRecord.setWriteCount(39L);
        stepRecord.setFilterCount(1L);
        stepRecord.setSkipCount(0L);
        stepRecord.setRollbackCount(0L);
        stepRecord.setRejectedCount(1L);
        stepRecord.setCreatedAt(LocalDateTime.of(2026, 8, 19, 16, 2));
        stepRecord.setUpdatedAt(LocalDateTime.of(2026, 8, 19, 16, 2));
        stepRecord.setCreatedBy("test");
        stepRecord.setUpdatedBy("test");
        stepRecordRepository.save(stepRecord);

        ArtifactRecord artifactRecord = new ArtifactRecord();
        artifactRecord.setArtifactRecordPk(17001L);
        artifactRecord.setArtifactRecordId("ar-" + runRecord.getRunRecordPk() + "-api-mssql");
        artifactRecord.setRunRecordPk(runRecord.getRunRecordPk());
        artifactRecord.setStepRecordId(stepRecord.getStepRecordId());
        artifactRecord.setArtifactRole("STEP_REJECT_OUTPUT");
        artifactRecord.setArtifactPath("C:/output/rejects/customer-load-mssql.csv");
        artifactRecord.setCreatedAt(LocalDateTime.of(2026, 8, 19, 16, 2));
        artifactRecord.setUpdatedAt(LocalDateTime.of(2026, 8, 19, 16, 2));
        artifactRecord.setCreatedBy("test");
        artifactRecord.setUpdatedBy("test");
        artifactRecordRepository.save(artifactRecord);

        mockMvc.perform(get("/api/v1/runs/{jobExecutionId}/step-records", jobExecutionId)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].stepRecordId").value(stepRecord.getStepRecordId()))
                .andExpect(jsonPath("$.items[0].runRecordId").value("rr-" + jobExecutionId))
                .andExpect(jsonPath("$.items[0].stepName").value("load-customers"))
                .andExpect(jsonPath("$.items[0].stepStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].readCount").value(40))
                .andExpect(jsonPath("$.items[0].rejectedCount").value(1));

        mockMvc.perform(get("/api/v1/runs/{jobExecutionId}/artifact-records", jobExecutionId)
                        .param("limit", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].artifactRecordId").value(artifactRecord.getArtifactRecordId()))
                .andExpect(jsonPath("$.items[0].runRecordId").value("rr-" + jobExecutionId))
                .andExpect(jsonPath("$.items[0].stepRecordId").value(stepRecord.getStepRecordId()))
                .andExpect(jsonPath("$.items[0].artifactRole").value("STEP_REJECT_OUTPUT"))
                .andExpect(jsonPath("$.items[0].artifactPath").value("C:/output/rejects/customer-load-mssql.csv"));
    }
}

