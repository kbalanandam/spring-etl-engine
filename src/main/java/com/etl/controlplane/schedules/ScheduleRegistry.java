package com.etl.controlplane.schedules;

import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction for schedule records.
 */
public interface ScheduleRegistry {

	ScheduleView upsert(ScheduleView schedule);

	Optional<ScheduleView> findByScheduleId(String scheduleId);

	Optional<ScheduleView> findByScheduleKey(String scheduleKey);

	List<ScheduleView> list(int limit);
}

