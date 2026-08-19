package com.etl.controlplane.persistence.jpa.repository;

import com.etl.controlplane.persistence.jpa.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

	Optional<Schedule> findByScheduleId(String scheduleId);

	Optional<Schedule> findByScheduleKey(String scheduleKey);
}

