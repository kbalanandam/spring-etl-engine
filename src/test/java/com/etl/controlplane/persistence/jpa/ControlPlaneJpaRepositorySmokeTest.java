package com.etl.controlplane.persistence.jpa;

import com.etl.controlplane.persistence.jpa.entity.ArtifactRecord;
import com.etl.controlplane.persistence.jpa.entity.AttemptLink;
import com.etl.controlplane.persistence.jpa.entity.CheckpointAnchor;
import com.etl.controlplane.persistence.jpa.entity.RunRecord;
import com.etl.controlplane.persistence.jpa.entity.RunSummary;
import com.etl.controlplane.persistence.jpa.entity.StepRecord;
import com.etl.controlplane.persistence.jpa.entity.TriggerEvent;
import com.etl.controlplane.persistence.jpa.entity.TriggerSource;
import com.etl.controlplane.persistence.jpa.repository.ArtifactRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.AttemptLinkRepository;
import com.etl.controlplane.persistence.jpa.repository.CheckpointAnchorRepository;
import com.etl.controlplane.persistence.jpa.repository.RunRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.RunSummaryRepository;
import com.etl.controlplane.persistence.jpa.repository.StepRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.TriggerEventRepository;
import com.etl.controlplane.persistence.jpa.repository.TriggerSourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ControlPlaneJpaRepositorySmokeTest {

	@Autowired
	private TriggerSourceRepository triggerSourceRepository;

	@Autowired
	private TriggerEventRepository triggerEventRepository;

	@Autowired
	private RunRecordRepository runRecordRepository;

	@Autowired
	private RunSummaryRepository runSummaryRepository;

	@Autowired
	private StepRecordRepository stepRecordRepository;

	@Autowired
	private ArtifactRecordRepository artifactRecordRepository;

	@Autowired
	private AttemptLinkRepository attemptLinkRepository;

	@Autowired
	private CheckpointAnchorRepository checkpointAnchorRepository;

	@Test
	void persistsRetainedHistoryWithStableExternalIds() {
		LocalDateTime now = LocalDateTime.now();

		TriggerSource source = new TriggerSource();
		source.setTriggerSourcePk(1L);
		source.setSourceCode("MANUAL");
		source.setDisplayName("Manual");
		source.setDescription("manual launch");
		source.setActive(true);
		source.setCreatedAt(now);
		source.setUpdatedAt(now);
		triggerSourceRepository.save(source);

		TriggerEvent event = new TriggerEvent();
		event.setTriggerEventPk(10L);
		event.setTriggerSourcePk(1L);
		event.setTriggerEventId("te-fixed-001");
		event.setJobKey("customer-load");
		event.setDecisionStatus("ACCEPTED");
		event.setReason("manual");
		event.setRequestedBy("tester");
		event.setRequestedAt(now);
		event.setTriggerOrigin("MANUAL");
		event.setUpdatedAt(now);
		triggerEventRepository.save(event);

		RunRecord runRecord = new RunRecord();
		runRecord.setRunRecordPk(20L);
		runRecord.setRunRecordId("rr-1001");
		runRecord.setJobExecutionId(1001L);
		runRecord.setTriggerEventPk(10L);
		runRecord.setTriggerEventId("te-fixed-001");
		runRecord.setSelectedJobKey("customer-load");
		runRecord.setScenario("customer-load");
		runRecord.setRunStatus("COMPLETED");
		runRecord.setStartedAt(now.minusMinutes(2));
		runRecord.setFinishedAt(now);
		runRecord.setCreatedAt(now);
		runRecord.setUpdatedAt(now);
		runRecordRepository.save(runRecord);

		RunSummary runSummary = new RunSummary();
		runSummary.setRunSummaryPk(30L);
		runSummary.setRunRecordPk(20L);
		runSummary.setJobExecutionId(1001L);
		runSummary.setScenario("customer-load");
		runSummary.setStatus("COMPLETED");
		runSummary.setStartTime(now.minusMinutes(2));
		runSummary.setEndTime(now);
		runSummary.setLastSeenAt(now);
		runSummary.setLogPath("logs/2026-08-19/customer-load.log");
		runSummaryRepository.save(runSummary);

		StepRecord step = new StepRecord();
		step.setStepRecordPk(40L);
		step.setStepRecordId("sr-20-1");
		step.setRunRecordPk(20L);
		step.setStepName("loadCustomers");
		step.setStepStatus("COMPLETED");
		step.setStartedAt(now.minusMinutes(2));
		step.setFinishedAt(now.minusMinutes(1));
		step.setCreatedAt(now);
		step.setUpdatedAt(now);
		stepRecordRepository.save(step);

		ArtifactRecord artifact = new ArtifactRecord();
		artifact.setArtifactRecordPk(50L);
		artifact.setArtifactRecordId("ar-log-20");
		artifact.setRunRecordPk(20L);
		artifact.setStepRecordId("sr-20-1");
		artifact.setArtifactRole("RUN_LOG");
		artifact.setArtifactPath("logs/2026-08-19/customer-load.log");
		artifact.setCreatedAt(now);
		artifactRecordRepository.save(artifact);

		AttemptLink link = new AttemptLink();
		link.setAttemptLinkPk(60L);
		link.setAttemptLinkId("al-20");
		link.setRunRecordPk(20L);
		link.setLinkKind("INITIAL");
		link.setCreatedAt(now);
		attemptLinkRepository.save(link);

		CheckpointAnchor anchor = new CheckpointAnchor();
		anchor.setCheckpointAnchorPk(70L);
		anchor.setCheckpointAnchorId("ca-log-20");
		anchor.setRunRecordPk(20L);
		anchor.setStepRecordId("sr-20-1");
		anchor.setAnchorKind("RUN_LOG");
		anchor.setAnchorRef("logs/2026-08-19/customer-load.log");
		anchor.setCreatedAt(now);
		anchor.setUpdatedAt(now);
		checkpointAnchorRepository.save(anchor);

		assertTrue(triggerEventRepository.findByTriggerEventId("te-fixed-001").isPresent());
		assertEquals("rr-1001", runRecordRepository.findByRunRecordId("rr-1001").orElseThrow().getRunRecordId());
		assertEquals("sr-20-1", stepRecordRepository.findByRunRecordPkOrderByStartedAtAscStepRecordIdAsc(20L).get(0).getStepRecordId());
		assertEquals("ar-log-20", artifactRecordRepository.findByRunRecordPkOrderByCreatedAtDescArtifactRecordIdDesc(20L).get(0).getArtifactRecordId());
		assertEquals("al-20", attemptLinkRepository.findByRunRecordPkOrderByCreatedAtDescAttemptLinkPkDesc(20L).get(0).getAttemptLinkId());
		assertEquals("ca-log-20", checkpointAnchorRepository.findByRunRecordPkOrderByCreatedAtDescCheckpointAnchorPkDesc(20L).get(0).getCheckpointAnchorId());
		assertEquals("customer-load", runSummaryRepository.findByJobExecutionId(1001L).orElseThrow().getScenario());
	}

	@Configuration
	@EntityScan(basePackages = "com.etl.controlplane.persistence.jpa.entity")
	@EnableJpaRepositories(basePackages = "com.etl.controlplane.persistence.jpa.repository")
	static class TestConfig {
	}
}


