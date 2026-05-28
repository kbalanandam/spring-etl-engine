package com.etl.controlplane.schedules;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
		String normalizedScheduleId = normalize(schedule.scheduleId());
		schedulesById.put(normalizedScheduleId, schedule);
		scheduleIdByKey.put(normalize(schedule.scheduleKey()), normalizedScheduleId);
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
		return Optional.ofNullable(schedulesById.get(normalize(scheduleId)));
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

	@Override
	public boolean tryAdvanceLastAcceptedDueAt(String scheduleId, Instant dueAt) {
		if (dueAt == null) {
			return false;
		}
		String normalizedScheduleId = normalize(scheduleId);
		if (normalizedScheduleId.isBlank()) {
			return false;
		}
		java.util.concurrent.atomic.AtomicBoolean advanced = new java.util.concurrent.atomic.AtomicBoolean(false);
		schedulesById.compute(normalizedScheduleId, (id, existing) -> {
			if (existing == null) {
				return null;
			}
			Instant current = existing.lastAcceptedDueAt();
			if (current != null && !dueAt.isAfter(current)) {
				return existing;
			}
			advanced.set(true);
			return new ScheduleView(
					existing.scheduleId(),
					existing.scheduleKey(),
					existing.selectedJobKey(),
					existing.expression(),
					existing.timezone(),
					existing.enabled(),
					existing.paused(),
					existing.description(),
					existing.createdAt(),
					LocalDateTime.now(),
					existing.watcherKey(),
					dueAt
			);
		});
		return advanced.get();
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}
}

