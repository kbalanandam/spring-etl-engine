package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.ControlPlanePkSequence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlPlanePkSequenceRepository extends JpaRepository<ControlPlanePkSequence, String> {
}

