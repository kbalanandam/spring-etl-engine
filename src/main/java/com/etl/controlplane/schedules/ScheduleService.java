package com.etl.controlplane.schedules;

import org.springframework.beans.factory.annotation.Autowired;
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
	private final ScheduleDefinitionValidator definitionValidator;

	@Autowired
	public ScheduleService(ScheduleRegistry registry, ScheduleDefinitionValidator definitionValidator) {
		this.registry = registry;
		this.definitionValidator = definitionValidator;
	}

	ScheduleService(ScheduleRegistry registry) {
		this(registry, ScheduleDefinitionValidator.permissive());
	}

	public ScheduleView createSchedule(String scheduleKey,
	                                   String selectedJobKey,
	                                   String expression,
	                                   String timezone,
	                                   boolean enabled,
	                                   String description) {
		String normalizedScheduleKey = normalizeScheduleKey(scheduleKey);
		if (registry.findByScheduleKey(normalizedScheduleKey).isPresent()) {
			throw new IllegalStateException("schedule_key already exists: " + normalizedScheduleKey);
		}
		String normalizedJobKey = normalizeSelectedJobKey(selectedJobKey);
		String normalizedExpression = normalizeRequired(expression, "invalid_expression", "expression is required");
		String normalizedTimezone = normalizeRequired(timezone, "invalid_timezone", "timezone is required");
		definitionValidator.validateDefinition(normalizedJobKey, normalizedExpression, normalizedTimezone);
		LocalDateTime now = LocalDateTime.now();
		ScheduleView schedule = new ScheduleView(
				"sch-" + UUID.randomUUID(),
				normalizedScheduleKey,
				normalizedJobKey,
				normalizedExpression,
				normalizedTimezone,
				enabled,
				false,
				normalizeOptional(description),
				now,
				now,
				null,
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
		String normalizedJobKey = normalizeSelectedJobKey(selectedJobKey);
		String normalizedExpression = normalizeRequired(expression, "invalid_expression", "expression is required");
		String normalizedTimezone = normalizeRequired(timezone, "invalid_timezone", "timezone is required");
		definitionValidator.validateDefinition(normalizedJobKey, normalizedExpression, normalizedTimezone);
		return findByScheduleId(scheduleId)
				.map(existing -> registry.upsert(new ScheduleView(
						existing.scheduleId(),
						existing.scheduleKey(),
						normalizedJobKey,
						normalizedExpression,
						normalizedTimezone,
						enabled,
						existing.paused() && enabled,
						normalizeOptional(description),
						existing.createdAt(),
						LocalDateTime.now(),
						existing.watcherKey(),
						existing.lastAcceptedDueAt()
				)));
	}

	public Optional<ScheduleView> markLastAcceptedDueAt(String scheduleId, java.time.Instant dueAt) {
		if (dueAt == null) {
			return Optional.empty();
		}
		if (!registry.tryAdvanceLastAcceptedDueAt(scheduleId == null ? "" : scheduleId.trim(), dueAt)) {
			return Optional.empty();
		}
		return findByScheduleId(scheduleId);
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
						existing.watcherKey(),
						existing.lastAcceptedDueAt()
				)));
	}

	private String normalizeRequired(String value, String reasonToken, String message) {
		String normalized = normalizeOptional(value);
		if (normalized.isBlank()) {
			throw new ScheduleValidationException(reasonToken, message);
		}
		return normalized;
	}

	private String normalizeScheduleKey(String value) {
		String normalized = normalizeRequired(value, "schedule_key_required", "scheduleKey is required");
		return normalized.toLowerCase();
	}

	private String normalizeSelectedJobKey(String value) {
		String normalized = normalizeRequired(value, "selected_job_key_required", "selectedJobKey is required");
		return normalized.toLowerCase();
	}

	private String normalizeOptional(String value) {
		return value == null ? "" : value.trim();
	}
}


