package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.ArtifactRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtifactRecordRepository extends JpaRepository<ArtifactRecord, Long> {

	List<ArtifactRecord> findByRunRecordPkOrderByCreatedAtDescArtifactRecordIdDesc(Long runRecordPk);

	List<ArtifactRecord> findByStepRecordIdOrderByCreatedAtDescArtifactRecordIdDesc(String stepRecordId);
}


