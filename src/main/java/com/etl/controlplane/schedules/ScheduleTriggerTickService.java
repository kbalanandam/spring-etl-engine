package com.etl.controlplane.schedules;

import com.etl.controlplane.triggers.TriggerEventRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Optional schedule tick evaluator that records schedule-origin trigger events.
 */
@Component
@ConditionalOnProperty(name = "controlplane.scheduler.enabled", havingValue = "true")
public class ScheduleTriggerTickService {

	private static final Logger log = LoggerFactory.getLogger(ScheduleTriggerTickService.class);

	private final ScheduleService scheduleService;
	private final TriggerEventRegistry triggerEventRegistry;
	private final Clock clock;
	private final long lookbackSeconds;
	private final String reason;
	private final String requestedBy;

	public ScheduleTriggerTickService(
			ScheduleService scheduleService,
			TriggerEventRegistry triggerEventRegistry,
			@Value("${controlplane.scheduler.poll-interval-ms:30000}") long pollIntervalMs,
			@Value("${controlplane.scheduler.trigger-reason:schedule_tick}") String reason,
			@Value("${controlplane.scheduler.requested-by:scheduler}") String requestedBy) {
		this(scheduleService, triggerEventRegistry, pollIntervalMs, reason, requestedBy, Clock.systemUTC());
	}

	ScheduleTriggerTickService(ScheduleService scheduleService,
	                           TriggerEventRegistry triggerEventRegistry,
	                           long pollIntervalMs,
	                           String reason,
	                           String requestedBy,
	                           Clock clock) {
		this.scheduleService = scheduleService;
		this.triggerEventRegistry = triggerEventRegistry;
		this.lookbackSeconds = Math.max(1L, (pollIntervalMs / 1000L) + 1L);
		this.reason = normalize(reason);
		this.requestedBy = normalize(requestedBy);
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${controlplane.scheduler.poll-interval-ms:30000}")
	public void pollAndRecordDueSchedules() {
		pollAndRecordDueSchedules(ZonedDateTime.now(clock));
	}

	void pollAndRecordDueSchedules(ZonedDateTime nowUtc) {
		List<ScheduleView> schedules = scheduleService.list(Integer.MAX_VALUE);
		for (ScheduleView schedule : schedules) {
			if (!schedule.enabled() || schedule.paused()) {
				continue;
			}
			recordIfDue(schedule, nowUtc);
		}
	}

	private void recordIfDue(ScheduleView schedule, ZonedDateTime nowUtc) {
		ZoneId zoneId;
		try {
			zoneId = ZoneId.of(normalizeTimezone(schedule.timezone()));
		} catch (Exception ex) {
			log.warn("SCHEDULE_TICK event=schedule_skipped scheduleId={} reason=invalid_timezone timezone={}",
					schedule.scheduleId(), schedule.timezone());
			return;
		}

		CronExpression cron;
		try {
			cron = CronExpression.parse(asSpringCron(schedule.expression()));
		} catch (Exception ex) {
			log.warn("SCHEDULE_TICK event=schedule_skipped scheduleId={} reason=invalid_expression expression={}",
					schedule.scheduleId(), schedule.expression());
			return;
		}

		ZonedDateTime now = nowUtc.withZoneSameInstant(zoneId);
		Instant lastAccepted = schedule.lastAcceptedDueAt();
		ZonedDateTime windowStart = lastAccepted == null
				? now.minusSeconds(lookbackSeconds)
				: ZonedDateTime.ofInstant(lastAccepted, zoneId).minusSeconds(1);
		ZonedDateTime dueAt = cron.next(windowStart);
		if (dueAt == null || dueAt.isAfter(now)) {
			return;
		}

		Instant dueInstant = dueAt.toInstant();
		if (lastAccepted != null && !dueInstant.isAfter(lastAccepted)) {
			return;
		}

		String message = "Schedule trigger accepted for scheduleId='" + schedule.scheduleId()
				+ "' scheduleKey='" + schedule.scheduleKey()
				+ "' dueAt='" + dueAt + "'.";
		if (scheduleService.markLastAcceptedDueAt(schedule.scheduleId(), dueInstant).isEmpty()) {
			log.debug("SCHEDULE_TICK event=schedule_duplicate_suppressed scheduleId={} dueAt={}", schedule.scheduleId(), dueAt);
			return;
		}
		triggerEventRegistry.recordAcceptedForSchedule(schedule.scheduleId(), schedule.selectedJobKey(), reason, requestedBy, message);
		log.info("SCHEDULE_TICK event=schedule_trigger_recorded scheduleId={} selectedJobKey={} dueAt={}",
				schedule.scheduleId(), schedule.selectedJobKey(), dueAt);
	}

	private String asSpringCron(String expression) {
		String normalized = normalize(expression);
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("expression is blank");
		}
		String[] fields = normalized.split("\\s+");
		if (fields.length == 5) {
			return "0 " + normalized;
		}
		if (fields.length == 6) {
			return normalized;
		}
		throw new IllegalArgumentException("unsupported cron field count");
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private String normalizeTimezone(String timezone) {
		String normalized = normalize(timezone);
		return normalized.isBlank() ? "UTC" : normalized;
	}
}


