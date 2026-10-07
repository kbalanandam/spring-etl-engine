package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.LogCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogCheckpointRepository extends JpaRepository<LogCheckpoint, String> {
}

