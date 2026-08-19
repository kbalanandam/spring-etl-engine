package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.CheckpointAnchor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckpointAnchorRepository extends JpaRepository<CheckpointAnchor, Long> {

	List<CheckpointAnchor> findByRunRecordPkOrderByCreatedAtDescCheckpointAnchorPkDesc(Long runRecordPk);

	Optional<CheckpointAnchor> findByCheckpointAnchorId(String checkpointAnchorId);
}


