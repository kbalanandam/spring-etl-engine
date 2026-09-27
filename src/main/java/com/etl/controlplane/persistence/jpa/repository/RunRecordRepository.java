package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.RunRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RunRecordRepository extends JpaRepository<RunRecord, Long> {

	Optional<RunRecord> findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(Long jobExecutionId);

	Optional<RunRecord> findFirstByTriggerEventIdIgnoreCaseOrderByStartedAtDescRunRecordPkDesc(String triggerEventId);

	Optional<RunRecord> findFirstByTriggerEventPkOrderByStartedAtDescRunRecordPkDesc(Long triggerEventPk);

	List<RunRecord> findBySelectedJobKeyOrderByStartedAtDescJobExecutionIdDesc(String selectedJobKey);

	Optional<RunRecord> findByRunRecordId(String runRecordId);
}

