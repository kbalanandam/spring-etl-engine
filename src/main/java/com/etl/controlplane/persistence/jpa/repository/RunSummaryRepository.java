package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.RunSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RunSummaryRepository extends JpaRepository<RunSummary, Long> {

	Optional<RunSummary> findByJobExecutionId(Long jobExecutionId);
}

