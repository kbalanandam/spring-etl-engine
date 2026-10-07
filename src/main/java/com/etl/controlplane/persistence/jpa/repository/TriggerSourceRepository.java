package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.TriggerSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TriggerSourceRepository extends JpaRepository<TriggerSource, Long> {

	Optional<TriggerSource> findBySourceCodeIgnoreCase(String sourceCode);
}

