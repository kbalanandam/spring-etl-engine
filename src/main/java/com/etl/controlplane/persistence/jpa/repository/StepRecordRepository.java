package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.StepRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StepRecordRepository extends JpaRepository<StepRecord, Long> {

	List<StepRecord> findByRunRecordPkOrderByStartedAtAscStepRecordIdAsc(Long runRecordPk);
}

