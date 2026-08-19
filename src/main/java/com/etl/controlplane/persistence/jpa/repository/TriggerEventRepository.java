package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.TriggerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TriggerEventRepository extends JpaRepository<TriggerEvent, Long> {

	Optional<TriggerEvent> findByTriggerEventId(String triggerEventId);

	List<TriggerEvent> findByJobKeyOrderByRequestedAtDescTriggerEventIdDesc(String jobKey);

	long countByJobKey(String jobKey);

	List<TriggerEvent> findBySchedulePkOrderByRequestedAtDescTriggerEventIdDesc(Long schedulePk);
}

