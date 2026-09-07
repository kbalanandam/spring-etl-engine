package com.etl.controlplane.schedules;

import com.etl.controlplane.persistence.jpa.JpaControlPlanePkAllocator;
import com.etl.controlplane.persistence.jpa.entity.Schedule;
import com.etl.controlplane.persistence.jpa.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * R3 bridge: keeps schedule persistence behavior stable while JPA repositories are introduced.
 */
@Repository
@ConditionalOnProperty(name = "controlplane.schedules.persistence.mode", havingValue = "jpa")
public class JpaScheduleRegistry implements ScheduleRegistry {

	private final ScheduleRepository scheduleRepository;
	private final JpaControlPlanePkAllocator pkAllocator;
	private final String auditActor;

	public JpaScheduleRegistry(ScheduleRepository scheduleRepository,
	                          JpaControlPlanePkAllocator pkAllocator,
	                          @Value("${spring.application.name:spring-etl-engine}") String applicationName) {
		this.scheduleRepository = scheduleRepository;
		this.pkAllocator = pkAllocator;
		this.auditActor = resolveAuditActor(applicationName);
	}

	@Override
	public ScheduleView upsert(ScheduleView schedule) {
		String normalizedScheduleId = normalize(schedule.scheduleId()).toLowerCase(Locale.ROOT);
		Optional<Schedule> existing = scheduleRepository.findByScheduleIdIgnoreCase(normalizedScheduleId);
		Schedule entity = existing.orElseGet(Schedule::new);
		if (entity.getSchedulePk() == null) {
			entity.setSchedulePk(pkAllocator.nextPk("controlplane_schedule_pk"));
			entity.setCreatedBy(auditActor);
		}
		entity.setScheduleId(normalizedScheduleId);
		entity.setScheduleKey(normalize(schedule.scheduleKey()));
		entity.setSelectedJobKey(normalize(schedule.selectedJobKey()));
		entity.setExpression(normalize(schedule.expression()));
		entity.setTimezone(normalize(schedule.timezone()));
		entity.setEnabled(schedule.enabled());
		entity.setPaused(schedule.paused());
		entity.setDescription(schedule.description());
		entity.setCreatedAt(firstNonNull(entity.getCreatedAt(), schedule.createdAt(), LocalDateTime.now(ZoneOffset.UTC)));
		entity.setUpdatedAt(firstNonNull(schedule.updatedAt(), LocalDateTime.now(ZoneOffset.UTC)));
		entity.setUpdatedBy(auditActor);
		entity.setWatcherKey(schedule.watcherKey());
		entity.setLastAcceptedDueAt(toLocalDateTime(schedule.lastAcceptedDueAt()));
		Schedule saved = scheduleRepository.save(entity);
		return toView(saved);
	}

	@Override
	public Optional<ScheduleView> findByScheduleId(String scheduleId) {
		String normalized = normalize(scheduleId).toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return Optional.empty();
		}
		return scheduleRepository.findByScheduleIdIgnoreCase(normalized).map(this::toView);
	}

	@Override
	public Optional<ScheduleView> findByScheduleKey(String scheduleKey) {
		String normalized = normalize(scheduleKey);
		if (normalized.isBlank()) {
			return Optional.empty();
		}
		return scheduleRepository.findByScheduleKeyIgnoreCase(normalized).map(this::toView);
	}

	@Override
	public List<ScheduleView> list(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		List<ScheduleView> schedules = scheduleRepository.findAllByOrderByUpdatedAtDescScheduleIdDesc()
				.stream()
				.map(this::toView)
				.toList();
		return schedules.size() <= limit ? schedules : schedules.subList(0, limit);
	}

	@Override
	public boolean tryAdvanceLastAcceptedDueAt(String scheduleId, Instant dueAt) {
		if (dueAt == null) {
			return false;
		}
		Optional<Schedule> existing = scheduleRepository.findByScheduleIdIgnoreCase(normalize(scheduleId).toLowerCase(Locale.ROOT));
		if (existing.isEmpty()) {
			return false;
		}
		Schedule schedule = existing.get();
		LocalDateTime dueAtUtc = toLocalDateTime(dueAt);
		LocalDateTime current = schedule.getLastAcceptedDueAt();
		if (current != null && !dueAtUtc.isAfter(current)) {
			return false;
		}
		schedule.setLastAcceptedDueAt(dueAtUtc);
		schedule.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
		schedule.setUpdatedBy(auditActor);
		scheduleRepository.save(schedule);
		return true;
	}

	private ScheduleView toView(Schedule schedule) {
		return new ScheduleView(
				schedule.getScheduleId(),
				schedule.getScheduleKey(),
				schedule.getSelectedJobKey(),
				schedule.getExpression(),
				schedule.getTimezone(),
				Boolean.TRUE.equals(schedule.getEnabled()),
				Boolean.TRUE.equals(schedule.getPaused()),
				schedule.getDescription(),
				schedule.getCreatedAt(),
				schedule.getUpdatedAt(),
				schedule.getWatcherKey(),
				toInstant(schedule.getLastAcceptedDueAt())
		);
	}

	private LocalDateTime toLocalDateTime(Instant value) {
		return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
	}

	private Instant toInstant(LocalDateTime value) {
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private String resolveAuditActor(String applicationName) {
		String normalized = applicationName == null ? "" : applicationName.trim();
		return normalized.isBlank() ? "spring-etl-engine" : normalized;
	}

	@SafeVarargs
	private <T> T firstNonNull(T... values) {
		for (T value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}
}


