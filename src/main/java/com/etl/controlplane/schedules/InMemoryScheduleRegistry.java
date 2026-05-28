package com.etl.controlplane.schedules;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback registry for schedules.
 */
@Component
@ConditionalOnProperty(name = "controlplane.schedules.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryScheduleRegistry implements ScheduleRegistry {

	private final Map<String, ScheduleView> schedulesById = new ConcurrentHashMap<>();
	private final Map<String, String> scheduleIdByKey = new ConcurrentHashMap<>();

	@Override
	public ScheduleView upsert(ScheduleView schedule) {
		schedulesById.put(schedule.scheduleId(), schedule);
		scheduleIdByKey.put(normalize(schedule.scheduleKey()), schedule.scheduleId());
		return schedule;
	}

	@Override
	public Optional<ScheduleView> findByScheduleId(String scheduleId) {
		return Optional.ofNullable(schedulesById.get(normalize(scheduleId)));
	}

	@Override
	public Optional<ScheduleView> findByScheduleKey(String scheduleKey) {
		String scheduleId = scheduleIdByKey.get(normalize(scheduleKey));
		if (scheduleId == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(schedulesById.get(scheduleId));
	}

	@Override
	public List<ScheduleView> list(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		return schedulesById.values().stream()
				.sorted(Comparator
						.comparing(ScheduleView::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(ScheduleView::scheduleId, Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(limit)
				.toList();
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}
}

