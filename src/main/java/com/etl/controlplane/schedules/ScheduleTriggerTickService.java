package com.etl.controlplane.schedules;

import com.etl.controlplane.triggers.TriggerEventRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Optional schedule tick evaluator that records schedule-origin trigger events.
 */
@Service
@ConditionalOnProperty(name = "controlplane.scheduler.enabled", havingValue = "true")
public class ScheduleTriggerTickService {

	private static final Logger log = LoggerFactory.getLogger(ScheduleTriggerTickService.class);

	private final ScheduleService scheduleService;
	private final TriggerEventRegistry triggerEventRegistry;
	private final Clock clock;
	private final long lookbackSeconds;
	private final String reason;
	private final String requestedBy;
	private final MissedRunPolicy missedRunPolicy;
	private final OverlapPolicy overlapPolicy;
	private final int maxCatchUpIterations;
	private final boolean launchEnabled;
	private final String jobsRoot;

	@Autowired
	public ScheduleTriggerTickService(
			ScheduleService scheduleService,
			TriggerEventRegistry triggerEventRegistry,
			@Value("${controlplane.scheduler.poll-interval-ms:30000}") long pollIntervalMs,
			@Value("${controlplane.scheduler.missed-run-policy:SKIP}") String missedRunPolicy,
			@Value("${controlplane.scheduler.overlap-policy:ALLOW}") String overlapPolicy,
			@Value("${controlplane.scheduler.max-catch-up-iterations:2000}") int maxCatchUpIterations,
			@Value("${controlplane.scheduler.launch-enabled:false}") boolean launchEnabled,
			@Value("${controlplane.jobs.root:src/main/resources/config-jobs}") String jobsRoot,
			@Value("${controlplane.scheduler.trigger-reason:schedule_tick}") String reason,
			@Value("${controlplane.scheduler.requested-by:scheduler}") String requestedBy) {
		this(scheduleService,
				triggerEventRegistry,
				pollIntervalMs,
				reason,
				requestedBy,
				Clock.systemUTC(),
				MissedRunPolicy.from(missedRunPolicy),
				OverlapPolicy.from(overlapPolicy),
				Math.max(1, maxCatchUpIterations),
				launchEnabled,
				jobsRoot);
	}

	ScheduleTriggerTickService(ScheduleService scheduleService,
	                           TriggerEventRegistry triggerEventRegistry,
	                           long pollIntervalMs,
	                           String reason,
	                           String requestedBy,
	                           Clock clock) {
		this(scheduleService,
				triggerEventRegistry,
				pollIntervalMs,
				reason,
				requestedBy,
				clock,
				MissedRunPolicy.SKIP,
				OverlapPolicy.ALLOW,
				2000,
				false,
				"src/main/resources/config-jobs");
	}

	ScheduleTriggerTickService(ScheduleService scheduleService,
	                           TriggerEventRegistry triggerEventRegistry,
	                           long pollIntervalMs,
	                           String reason,
	                           String requestedBy,
	                           Clock clock,
	                           MissedRunPolicy missedRunPolicy,
	                           int maxCatchUpIterations) {
		this(scheduleService,
				triggerEventRegistry,
				pollIntervalMs,
				reason,
				requestedBy,
				clock,
				missedRunPolicy,
				OverlapPolicy.ALLOW,
				maxCatchUpIterations,
				false,
				"src/main/resources/config-jobs");
	}

	ScheduleTriggerTickService(ScheduleService scheduleService,
	                           TriggerEventRegistry triggerEventRegistry,
	                           long pollIntervalMs,
	                           String reason,
	                           String requestedBy,
	                           Clock clock,
	                           MissedRunPolicy missedRunPolicy,
	                           OverlapPolicy overlapPolicy,
	                           int maxCatchUpIterations) {
		this(scheduleService,
				triggerEventRegistry,
				pollIntervalMs,
				reason,
				requestedBy,
				clock,
				missedRunPolicy,
				overlapPolicy,
				maxCatchUpIterations,
				false,
				"src/main/resources/config-jobs");
	}

	ScheduleTriggerTickService(ScheduleService scheduleService,
	                           TriggerEventRegistry triggerEventRegistry,
	                           long pollIntervalMs,
	                           String reason,
	                           String requestedBy,
	                           Clock clock,
	                           MissedRunPolicy missedRunPolicy,
	                           OverlapPolicy overlapPolicy,
	                           int maxCatchUpIterations,
	                           boolean launchEnabled,
	                           String jobsRoot) {
		this.scheduleService = scheduleService;
		this.triggerEventRegistry = triggerEventRegistry;
		this.lookbackSeconds = Math.max(1L, (pollIntervalMs / 1000L) + 1L);
		this.reason = normalize(reason);
		this.requestedBy = normalize(requestedBy);
		this.clock = clock;
		this.missedRunPolicy = missedRunPolicy == null ? MissedRunPolicy.SKIP : missedRunPolicy;
		this.overlapPolicy = overlapPolicy == null ? OverlapPolicy.ALLOW : overlapPolicy;
		this.maxCatchUpIterations = Math.max(1, maxCatchUpIterations);
		this.launchEnabled = launchEnabled;
		this.jobsRoot = normalize(jobsRoot);
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
		List<ZonedDateTime> dueAts = applyOverlapPolicy(resolveDueAts(cron, now, lastAccepted, zoneId));
		if (dueAts.isEmpty()) {
			return;
		}
		for (ZonedDateTime dueAt : dueAts) {
			Instant dueInstant = dueAt.toInstant();
			if (lastAccepted != null && !dueInstant.isAfter(lastAccepted)) {
				continue;
			}
			String message = "Schedule trigger accepted for scheduleId='" + schedule.scheduleId()
					+ "' scheduleKey='" + schedule.scheduleKey()
					+ "' dueAt='" + dueAt + "'.";
			if (scheduleService.markLastAcceptedDueAt(schedule.scheduleId(), dueInstant).isEmpty()) {
				log.debug("SCHEDULE_TICK event=schedule_duplicate_suppressed scheduleId={} dueAt={}", schedule.scheduleId(), dueAt);
				continue;
			}
			triggerEventRegistry.recordAcceptedForSchedule(schedule.scheduleId(), schedule.selectedJobKey(), reason, requestedBy, message);
			log.info("SCHEDULE_TICK event=schedule_trigger_recorded scheduleId={} selectedJobKey={} dueAt={}",
					schedule.scheduleId(), schedule.selectedJobKey(), dueAt);
			launchScheduledJob(schedule, dueAt);
			lastAccepted = dueInstant;
		}
	}

	private void launchScheduledJob(ScheduleView schedule, ZonedDateTime dueAt) {
		if (!launchEnabled) {
			return;
		}

		String selectedJobKey = normalize(schedule.selectedJobKey()).toLowerCase();
		if (selectedJobKey.isBlank()) {
			log.warn("SCHEDULE_TICK event=schedule_launch_skipped scheduleId={} reason=missing_selected_job_key", schedule.scheduleId());
			return;
		}

		Path jobConfigPath = Path.of(jobsRoot).resolve(selectedJobKey).resolve("job-config.yaml").normalize();
		if (!Files.isRegularFile(jobConfigPath)) {
			log.warn("SCHEDULE_TICK event=schedule_launch_skipped scheduleId={} selectedJobKey={} reason=job_config_missing jobConfigPath={}",
					schedule.scheduleId(), selectedJobKey, jobConfigPath);
			return;
		}

		String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		String classPath = normalize(System.getProperty("java.class.path"));
		if (classPath.isBlank()) {
			log.warn("SCHEDULE_TICK event=schedule_launch_skipped scheduleId={} selectedJobKey={} reason=classpath_unavailable",
					schedule.scheduleId(), selectedJobKey);
			return;
		}

		ProcessBuilder processBuilder = new ProcessBuilder(
				javaExecutable,
				"-Detl.config.job=" + jobConfigPath,
				"-Detl.config.allow-demo-fallback=false",
				"-cp",
				classPath,
				"com.etl.ETLEngineApplication");
		processBuilder.redirectErrorStream(true);
		processBuilder.inheritIO();

		try {
			Process process = processBuilder.start();
			log.info("SCHEDULE_TICK event=schedule_launch_started scheduleId={} selectedJobKey={} dueAt={} pid={} jobConfigPath={}",
					schedule.scheduleId(), selectedJobKey, dueAt, process.pid(), jobConfigPath);
			Thread completionWatcher = new Thread(
					() -> waitForLaunchCompletion(schedule.scheduleId(), selectedJobKey, process),
					"schedule-launch-wait-" + schedule.scheduleId());
			completionWatcher.setDaemon(true);
			completionWatcher.start();
		} catch (IOException ex) {
			log.error("SCHEDULE_TICK event=schedule_launch_failed scheduleId={} selectedJobKey={} dueAt={} reason=process_start_failed message={}",
					schedule.scheduleId(), selectedJobKey, dueAt, ex.getMessage(), ex);
		}
	}

	private void waitForLaunchCompletion(String scheduleId, String selectedJobKey, Process process) {
		try {
			int exitCode = process.waitFor();
			log.info("SCHEDULE_TICK event=schedule_launch_finished scheduleId={} selectedJobKey={} pid={} exitCode={}",
					scheduleId, selectedJobKey, process.pid(), exitCode);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("SCHEDULE_TICK event=schedule_launch_wait_interrupted scheduleId={} selectedJobKey={} pid={}",
					scheduleId, selectedJobKey, process.pid());
		}
	}

	private List<ZonedDateTime> applyOverlapPolicy(List<ZonedDateTime> dueAts) {
		if (dueAts == null || dueAts.isEmpty()) {
			return List.of();
		}
		if (overlapPolicy != OverlapPolicy.SERIALIZE || dueAts.size() == 1) {
			return dueAts;
		}
		// SERIALIZE mode intentionally drains backlog one due instant at a time.
		return List.of(dueAts.get(0));
	}

	private List<ZonedDateTime> resolveDueAts(CronExpression cron,
	                                         ZonedDateTime now,
	                                         Instant lastAccepted,
	                                         ZoneId zoneId) {
		if (missedRunPolicy == MissedRunPolicy.SKIP) {
			ZonedDateTime cursor = now.minusSeconds(lookbackSeconds);
			ZonedDateTime latestDue = null;
			for (int i = 0; i < maxCatchUpIterations; i++) {
				ZonedDateTime next = cron.next(cursor);
				if (next == null || next.isAfter(now)) {
					break;
				}
				latestDue = next;
				cursor = next;
			}
			return latestDue == null ? List.of() : List.of(latestDue);
		}

		if (missedRunPolicy == MissedRunPolicy.CATCH_UP_ONCE) {
			ZonedDateTime latestDue = latestDue(cron, now, lastAccepted, zoneId);
			return latestDue == null ? List.of() : List.of(latestDue);
		}

		ZonedDateTime anchor = lastAccepted == null
				? now.minusSeconds(lookbackSeconds)
				: ZonedDateTime.ofInstant(lastAccepted, zoneId);
		List<ZonedDateTime> dueAts = new java.util.ArrayList<>();
		ZonedDateTime cursor = anchor;
		for (int i = 0; i < maxCatchUpIterations; i++) {
			ZonedDateTime next = cron.next(cursor);
			if (next == null || next.isAfter(now)) {
				break;
			}
			dueAts.add(next);
			cursor = next;
		}
		return dueAts;
	}

	private ZonedDateTime latestDue(CronExpression cron,
	                               ZonedDateTime now,
	                               Instant lastAccepted,
	                               ZoneId zoneId) {
		ZonedDateTime anchor = lastAccepted == null
				? now.minusSeconds(lookbackSeconds)
				: ZonedDateTime.ofInstant(lastAccepted, zoneId);
		ZonedDateTime latestDue = null;
		ZonedDateTime cursor = anchor;
		for (int i = 0; i < maxCatchUpIterations; i++) {
			ZonedDateTime next = cron.next(cursor);
			if (next == null || next.isAfter(now)) {
				break;
			}
			latestDue = next;
			cursor = next;
		}
		return latestDue;
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

	enum MissedRunPolicy {
		SKIP,
		CATCH_UP_ONCE,
		CATCH_UP_ALL;

		static MissedRunPolicy from(String raw) {
			if (raw == null || raw.isBlank()) {
				return SKIP;
			}
			try {
				return MissedRunPolicy.valueOf(raw.trim().toUpperCase());
			} catch (IllegalArgumentException ex) {
				return SKIP;
			}
		}
	}

	enum OverlapPolicy {
		ALLOW,
		SERIALIZE;

		static OverlapPolicy from(String raw) {
			if (raw == null || raw.isBlank()) {
				return ALLOW;
			}
			try {
				return OverlapPolicy.valueOf(raw.trim().toUpperCase());
			} catch (IllegalArgumentException ex) {
				return ALLOW;
			}
		}
	}
}


