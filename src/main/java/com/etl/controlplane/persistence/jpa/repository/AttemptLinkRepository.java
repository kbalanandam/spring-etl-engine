package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.AttemptLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttemptLinkRepository extends JpaRepository<AttemptLink, Long> {

	List<AttemptLink> findByRunRecordPkOrderByCreatedAtDescAttemptLinkPkDesc(Long runRecordPk);
}

