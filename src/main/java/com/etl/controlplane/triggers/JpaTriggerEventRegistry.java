package com.etl.controlplane.triggers;

import com.etl.controlplane.persistence.jpa.JpaControlPlanePkAllocator;
import com.etl.controlplane.persistence.jpa.entity.Schedule;
import com.etl.controlplane.persistence.jpa.entity.TriggerEvent;
import com.etl.controlplane.persistence.jpa.entity.TriggerSource;
import com.etl.controlplane.persistence.jpa.entity.RunRecord;
import com.etl.controlplane.persistence.jpa.repository.RunRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.ScheduleRepository;
import com.etl.controlplane.persistence.jpa.repository.TriggerEventRepository;
import com.etl.controlplane.persistence.jpa.repository.TriggerSourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * R3 bridge: exposes a JPA-selected mode while preserving proven trigger semantics.
 */
@Repository
@ConditionalOnProperty(name = "controlplane.triggers.persistence.mode", havingValue = "jpa")
public class JpaTriggerEventRegistry implements TriggerEventRegistry {

	private final TriggerEventRepository triggerEventRepository;
	private final TriggerSourceRepository triggerSourceRepository;
	private final RunRecordRepository runRecordRepository;
	private final ScheduleRepository scheduleRepository;
	private final JpaControlPlanePkAllocator pkAllocator;
	private final int retentionPerJob;
	private final String auditActor;

	public JpaTriggerEventRegistry(TriggerEventRepository triggerEventRepository,
	                              TriggerSourceRepository triggerSourceRepository,
	                              RunRecordRepository runRecordRepository,
	                              ScheduleRepository scheduleRepository,
	                              JpaControlPlanePkAllocator pkAllocator,
	                              @Value("${controlplane.triggers.retention-per-job:100}") int retentionPerJob,
	                              @Value("${spring.application.name:spring-etl-engine}") String applicationName) {
		this.triggerEventRepository = triggerEventRepository;
		this.triggerSourceRepository = triggerSourceRepository;
		this.runRecordRepository = runRecordRepository;
		this.scheduleRepository = scheduleRepository;
		this.pkAllocator = pkAllocator;
		this.retentionPerJob = Math.max(1, retentionPerJob);
		this.auditActor = resolveAuditActor(applicationName);
	}

	@Override
	public TriggerEventView recordAccepted(String jobKey, String reason, String requestedBy, String message) {
		return recordAcceptedInternal(null, "MANUAL", jobKey, reason, requestedBy, message);
	}

	@Override
	public TriggerEventView recordAcceptedForSchedule(String scheduleId, String jobKey, String reason, String requestedBy, String message) {
		return recordAcceptedInternal(scheduleId, "SCHEDULE", jobKey, reason, requestedBy, message);
	}

	@Override
	public List<TriggerEventView> listByJobKey(String jobKey, int limit) {
		return listByJobKey(jobKey, 0, limit);
	}

	@Override
	public List<TriggerEventView> listByJobKey(String jobKey, int offset, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		String normalizedJobKey = normalize(jobKey);
		int safeOffset = Math.max(0, offset);
		List<TriggerEvent> events = triggerEventRepository.findByJobKeyOrderByTriggerEventPkDesc(normalizedJobKey);
		return sliceAndMap(events, safeOffset, limit);
	}

	@Override
	public long countByJobKey(String jobKey) {
		return triggerEventRepository.countByJobKey(normalize(jobKey));
	}

	@Override
	public List<TriggerEventView> listByScheduleId(String scheduleId, int limit) {
		return listByScheduleId(scheduleId, 0, limit);
	}

	@Override
	public List<TriggerEventView> listByScheduleId(String scheduleId, int offset, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		String normalizedScheduleId = normalize(scheduleId).toLowerCase(Locale.ROOT);
		if (normalizedScheduleId.isBlank()) {
			return List.of();
		}
		Optional<Schedule> schedule = scheduleRepository.findByScheduleIdIgnoreCase(normalizedScheduleId);
		if (schedule.isEmpty() || schedule.get().getSchedulePk() == null) {
			return List.of();
		}
		int safeOffset = Math.max(0, offset);
		List<TriggerEvent> events = triggerEventRepository.findBySchedulePkOrderByTriggerEventPkDesc(schedule.get().getSchedulePk());
		return sliceAndMap(events, safeOffset, limit);
	}

	@Override
	public long countByScheduleId(String scheduleId) {
		String normalizedScheduleId = normalize(scheduleId).toLowerCase(Locale.ROOT);
		if (normalizedScheduleId.isBlank()) {
			return 0L;
		}
		Optional<Schedule> schedule = scheduleRepository.findByScheduleIdIgnoreCase(normalizedScheduleId);
		if (schedule.isEmpty() || schedule.get().getSchedulePk() == null) {
			return 0L;
		}
		return triggerEventRepository.countBySchedulePk(schedule.get().getSchedulePk());
	}

	private TriggerEventView recordAcceptedInternal(String scheduleId,
	                                              String triggerOrigin,
	                                              String jobKey,
	                                              String reason,
	                                              String requestedBy,
	                                              String message) {
		String normalizedJobKey = normalize(jobKey);
		String normalizedReason = normalize(reason);
		String normalizedRequestedBy = normalize(requestedBy);
		String normalizedScheduleId = normalize(scheduleId).toLowerCase(Locale.ROOT);
		Long schedulePk = resolveSchedulePk(normalizedScheduleId);
		String normalizedTriggerOrigin = normalizeTriggerOrigin(triggerOrigin, schedulePk, null);

		Instant requestedAt = Instant.now();
		LocalDateTime requestedAtUtc = LocalDateTime.ofInstant(requestedAt, ZoneOffset.UTC);
		TriggerEvent triggerEvent = new TriggerEvent();
		triggerEvent.setTriggerEventPk(pkAllocator.nextPk("controlplane_trigger_event_pk"));
		triggerEvent.setTriggerSourcePk(resolveOrCreateTriggerSourcePk(normalizedTriggerOrigin, requestedAtUtc));
		triggerEvent.setTriggerEventId("te-" + UUID.randomUUID());
		triggerEvent.setJobKey(normalizedJobKey);
		triggerEvent.setDecisionStatus("ACCEPTED");
		triggerEvent.setReason(normalizedReason);
		triggerEvent.setRequestedBy(normalizedRequestedBy);
		triggerEvent.setRequestedAt(requestedAtUtc);
		triggerEvent.setLaunchedRunPk(null);
		triggerEvent.setLaunchedRunId(null);
		triggerEvent.setMessage(message);
		triggerEvent.setTriggerOrigin(normalizedTriggerOrigin);
		triggerEvent.setSchedulePk(schedulePk);
		triggerEvent.setExternalOriginKey(null);
		triggerEvent.setUpdatedAt(requestedAtUtc);
		triggerEvent.setCreatedBy(auditActor);
		triggerEvent.setUpdatedBy(auditActor);
		triggerEventRepository.save(triggerEvent);

		pruneOverflow(normalizedJobKey);

		return new TriggerEventView(
				triggerEvent.getTriggerEventId(),
				normalizedJobKey,
				"ACCEPTED",
				normalizedReason,
				normalizedRequestedBy,
				requestedAt,
				null,
				message,
				normalizedTriggerOrigin
		);
	}

	private Long resolveSchedulePk(String normalizedScheduleId) {
		if (normalizedScheduleId.isBlank()) {
			return null;
		}
		return scheduleRepository.findByScheduleIdIgnoreCase(normalizedScheduleId)
				.map(Schedule::getSchedulePk)
				.orElse(null);
	}

	private Long resolveOrCreateTriggerSourcePk(String triggerOrigin, LocalDateTime now) {
		String sourceCode = normalizeTriggerOrigin(triggerOrigin, null, null);
		Optional<TriggerSource> existing = triggerSourceRepository.findBySourceCodeIgnoreCase(sourceCode);
		if (existing.isPresent() && existing.get().getTriggerSourcePk() != null) {
			return existing.get().getTriggerSourcePk();
		}

		TriggerSource seed = new TriggerSource();
		seed.setTriggerSourcePk(defaultTriggerSourcePk(sourceCode));
		seed.setSourceCode(sourceCode);
		seed.setDisplayName(sourceCode.substring(0, 1) + sourceCode.substring(1).toLowerCase(Locale.ROOT));
		seed.setDescription(defaultTriggerSourceDescription(sourceCode));
		seed.setActive(true);
		seed.setCreatedAt(now);
		seed.setUpdatedAt(now);
		seed.setCreatedBy(auditActor);
		seed.setUpdatedBy(auditActor);

		TriggerSource saved = triggerSourceRepository.save(seed);
		return saved.getTriggerSourcePk();
	}

	private List<TriggerEventView> sliceAndMap(List<TriggerEvent> events, int offset, int limit) {
		if (offset >= events.size()) {
			return List.of();
		}
		int toIndex = Math.min(events.size(), offset + limit);
		return events.subList(offset, toIndex).stream().map(this::toView).toList();
	}

	private TriggerEventView toView(TriggerEvent entity) {
		Instant requestedAt = entity.getRequestedAt() == null
				? null
				: entity.getRequestedAt().toInstant(ZoneOffset.UTC);
		String launchedRunId = resolveLaunchedRunId(entity);
		String triggerOrigin = normalizeTriggerOrigin(entity.getTriggerOrigin(), entity.getSchedulePk(), entity.getExternalOriginKey());
		return new TriggerEventView(
				entity.getTriggerEventId(),
				entity.getJobKey(),
				entity.getDecisionStatus(),
				entity.getReason(),
				entity.getRequestedBy(),
				requestedAt,
				launchedRunId,
				entity.getMessage(),
				triggerOrigin
		);
	}

	private String resolveLaunchedRunId(TriggerEvent entity) {
		String explicitLaunchedRunId = normalize(entity.getLaunchedRunId());
		if (!explicitLaunchedRunId.isBlank()) {
			return explicitLaunchedRunId;
		}
		if (entity.getLaunchedRunPk() != null) {
			String byRunRecordPk = runRecordRepository.findById(entity.getLaunchedRunPk())
					.map(RunRecord::getJobExecutionId)
					.map(String::valueOf)
					.map(this::normalize)
					.orElse("");
			if (!byRunRecordPk.isBlank()) {
				return byRunRecordPk;
			}
		}
		String normalizedTriggerEventId = normalize(entity.getTriggerEventId());
		if (!normalizedTriggerEventId.isBlank()) {
			String byTriggerEventId = runRecordRepository.findFirstByTriggerEventIdIgnoreCaseOrderByStartedAtDescRunRecordPkDesc(normalizedTriggerEventId)
					.map(RunRecord::getJobExecutionId)
					.map(String::valueOf)
					.map(this::normalize)
					.orElse("");
			if (!byTriggerEventId.isBlank()) {
				return byTriggerEventId;
			}
		}
		if (entity.getTriggerEventPk() != null) {
			return runRecordRepository.findFirstByTriggerEventPkOrderByStartedAtDescRunRecordPkDesc(entity.getTriggerEventPk())
					.map(RunRecord::getJobExecutionId)
					.map(String::valueOf)
					.map(this::normalize)
					.filter(value -> !value.isBlank())
					.orElse(null);
		}
		return null;
	}

	private void pruneOverflow(String jobKey) {
		long count = triggerEventRepository.countByJobKey(jobKey);
		if (count <= retentionPerJob) {
			return;
		}
		List<TriggerEvent> events = triggerEventRepository.findByJobKeyOrderByTriggerEventPkDesc(jobKey);
		if (events.size() <= retentionPerJob) {
			return;
		}
		events.subList(retentionPerJob, events.size()).forEach(triggerEventRepository::delete);
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private String resolveAuditActor(String applicationName) {
		String normalized = applicationName == null ? "" : applicationName.trim();
		return normalized.isBlank() ? "spring-etl-engine" : normalized;
	}

	private String normalizeTriggerOrigin(String triggerOrigin, Long schedulePk, String externalOriginKey) {
		String normalizedOrigin = normalize(triggerOrigin).toUpperCase(Locale.ROOT);
		if ("SCHEDULE".equals(normalizedOrigin) || "EVENT".equals(normalizedOrigin) || "MANUAL".equals(normalizedOrigin)) {
			return normalizedOrigin;
		}
		if (schedulePk != null) {
			return "SCHEDULE";
		}
		if (!normalize(externalOriginKey).isBlank()) {
			return "EVENT";
		}
		return "MANUAL";
	}

	private long defaultTriggerSourcePk(String sourceCode) {
		return switch (sourceCode) {
			case "MANUAL" -> 1L;
			case "SCHEDULE" -> 2L;
			case "EVENT" -> 3L;
			default -> pkAllocator.nextPk("controlplane_trigger_source_pk");
		};
	}

	private String defaultTriggerSourceDescription(String sourceCode) {
		return switch (sourceCode) {
			case "MANUAL" -> "Ad hoc operator or API-triggered launch";
			case "SCHEDULE" -> "Native scheduler-origin launch";
			case "EVENT" -> "File watcher or external event-origin launch";
			default -> "Control-plane trigger source";
		};
	}
}




