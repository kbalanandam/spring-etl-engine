package com.etl.controlplane.schedules;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal schedule service used to prepare persistence-backed schedule workflows.
 */
@Component
public class ScheduleService {

	private final ScheduleRegistry registry;

	public ScheduleService(ScheduleRegistry registry) {
		this.registry = registry;
	}

	public ScheduleView createSchedule(String scheduleKey,
	                                   String selectedJobKey,
	                                   String expression,
	                                   String timezone,
	                                   boolean enabled,
	                                   String description) {
		String normalizedScheduleKey = normalizeKey(scheduleKey);
		if (registry.findByScheduleKey(normalizedScheduleKey).isPresent()) {
			throw new IllegalStateException("schedule_key already exists: " + normalizedScheduleKey);
		}
		LocalDateTime now = LocalDateTime.now();
		ScheduleView schedule = new ScheduleView(
				"sch-" + UUID.randomUUID(),
				normalizedScheduleKey,
				normalizeKey(selectedJobKey),
				normalizeRequired(expression, "expression"),
				normalizeRequired(timezone, "timezone"),
				enabled,
				false,
				normalizeOptional(description),
				now,
				now,
				null
		);
		return registry.upsert(schedule);
	}

	public Optional<ScheduleView> updateSchedule(String scheduleId,
	                                            String selectedJobKey,
	                                            String expression,
	                                            String timezone,
	                                            boolean enabled,
	                                            String description) {
		return findByScheduleId(scheduleId)
				.map(existing -> registry.upsert(new ScheduleView(
						existing.scheduleId(),
						existing.scheduleKey(),
						normalizeKey(selectedJobKey),
						normalizeRequired(expression, "expression"),
						normalizeRequired(timezone, "timezone"),
						enabled,
						existing.paused() && enabled,
						normalizeOptional(description),
						existing.createdAt(),
						LocalDateTime.now(),
						existing.watcherKey()
				)));
	}

	public Optional<ScheduleView> findByScheduleId(String scheduleId) {
		return registry.findByScheduleId(scheduleId == null ? "" : scheduleId.trim());
	}

	public Optional<ScheduleView> findByScheduleKey(String scheduleKey) {
		return registry.findByScheduleKey(scheduleKey);
	}

	public List<ScheduleView> list(int limit) {
		return registry.list(limit);
	}

	public Optional<ScheduleView> enable(String scheduleId) {
		return updateState(scheduleId, true, false);
	}

	public Optional<ScheduleView> disable(String scheduleId) {
		return updateState(scheduleId, false, false);
	}

	public Optional<ScheduleView> pause(String scheduleId) {
		return updateState(scheduleId, true, true);
	}

	public Optional<ScheduleView> resume(String scheduleId) {
		return updateState(scheduleId, true, false);
	}

	private Optional<ScheduleView> updateState(String scheduleId, boolean enabled, boolean paused) {
		return findByScheduleId(scheduleId)
				.map(existing -> registry.upsert(new ScheduleView(
						existing.scheduleId(),
						existing.scheduleKey(),
						existing.selectedJobKey(),
						existing.expression(),
						existing.timezone(),
						enabled,
						paused,
						existing.description(),
						existing.createdAt(),
						LocalDateTime.now(),
						existing.watcherKey()
				)));
	}

	private String normalizeRequired(String value, String fieldName) {
		String normalized = normalizeOptional(value);
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		return normalized;
	}

	private String normalizeKey(String value) {
		String normalized = normalizeRequired(value, "key");
		return normalized.toLowerCase();
	}

	private String normalizeOptional(String value) {
		return value == null ? "" : value.trim();
	}
}


