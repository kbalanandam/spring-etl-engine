package com.etl.controlplane.schedules;

import com.etl.controlplane.triggers.TriggerEventRegistry;
import com.etl.controlplane.triggers.TriggerEventView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleTriggerTickServiceTest {

	@Test
	void recordsDueScheduleTickOncePerDueInstant() {
		ScheduleService scheduleService = new ScheduleService(new InMemoryScheduleRegistry());
		RecordingTriggerRegistry triggerRegistry = new RecordingTriggerRegistry();
		ScheduleView schedule = scheduleService.createSchedule("every-minute", "customer-load", "* * * * *", "UTC", true, "minute tick");

		Clock fixedClock = Clock.fixed(Instant.parse("2026-05-28T10:00:20Z"), ZoneOffset.UTC);
		ScheduleTriggerTickService tickService = new ScheduleTriggerTickService(
				scheduleService,
				triggerRegistry,
				30000,
				"schedule_tick",
				"scheduler",
				fixedClock
		);

		tickService.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));
		tickService.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));

		assertEquals(1, triggerRegistry.recorded.size());
		assertEquals(schedule.selectedJobKey(), triggerRegistry.recorded.get(0).jobKey());
		assertEquals("schedule_tick", triggerRegistry.recorded.get(0).reason());
		assertEquals(1, triggerRegistry.listByScheduleId(schedule.scheduleId(), 10).size());
	}

	@Test
	void preservesDedupAcrossSchedulerServiceRecreation() {
		InMemoryScheduleRegistry registry = new InMemoryScheduleRegistry();
		ScheduleService scheduleService = new ScheduleService(registry);
		RecordingTriggerRegistry triggerRegistry = new RecordingTriggerRegistry();
		ScheduleView schedule = scheduleService.createSchedule("every-minute", "customer-load", "* * * * *", "UTC", true, "minute tick");

		Clock fixedClock = Clock.fixed(Instant.parse("2026-05-28T10:00:20Z"), ZoneOffset.UTC);
		ScheduleTriggerTickService first = new ScheduleTriggerTickService(
				scheduleService,
				triggerRegistry,
				30000,
				"schedule_tick",
				"scheduler",
				fixedClock
		);
		first.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));

		ScheduleTriggerTickService second = new ScheduleTriggerTickService(
				scheduleService,
				triggerRegistry,
				30000,
				"schedule_tick",
				"scheduler",
				fixedClock
		);
		second.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));

		assertEquals(1, triggerRegistry.recorded.size());
		ScheduleView persisted = scheduleService.findByScheduleId(schedule.scheduleId()).orElseThrow();
		assertEquals(Instant.parse("2026-05-28T10:00:00Z"), persisted.lastAcceptedDueAt());
	}

	@Test
	void suppressesDuplicateWhenWatermarkAlreadyClaimed() {
		InMemoryScheduleRegistry registry = new InMemoryScheduleRegistry();
		ScheduleService scheduleService = new ScheduleService(registry);
		RecordingTriggerRegistry triggerRegistry = new RecordingTriggerRegistry();
		ScheduleView schedule = scheduleService.createSchedule("every-minute", "customer-load", "* * * * *", "UTC", true, "minute tick");
		scheduleService.markLastAcceptedDueAt(schedule.scheduleId(), Instant.parse("2026-05-28T10:00:00Z"));

		Clock fixedClock = Clock.fixed(Instant.parse("2026-05-28T10:00:20Z"), ZoneOffset.UTC);
		ScheduleTriggerTickService tickService = new ScheduleTriggerTickService(
				scheduleService,
				triggerRegistry,
				30000,
				"schedule_tick",
				"scheduler",
				fixedClock
		);

		tickService.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));
		assertEquals(0, triggerRegistry.recorded.size());
	}

	@Test
	void skipPolicyIgnoresOldBacklogAndUsesCurrentDue() {
		InMemoryScheduleRegistry registry = new InMemoryScheduleRegistry();
		ScheduleService scheduleService = new ScheduleService(registry);
		RecordingTriggerRegistry triggerRegistry = new RecordingTriggerRegistry();
		ScheduleView schedule = scheduleService.createSchedule("every-minute", "customer-load", "* * * * *", "UTC", true, "minute tick");
		scheduleService.markLastAcceptedDueAt(schedule.scheduleId(), Instant.parse("2026-05-28T09:00:00Z"));

		Clock fixedClock = Clock.fixed(Instant.parse("2026-05-28T10:00:20Z"), ZoneOffset.UTC);
		ScheduleTriggerTickService tickService = new ScheduleTriggerTickService(
				scheduleService,
				triggerRegistry,
				30000,
				"schedule_tick",
				"scheduler",
				fixedClock,
				ScheduleTriggerTickService.MissedRunPolicy.SKIP,
				2000
		);

		tickService.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));
		ScheduleView persisted = scheduleService.findByScheduleId(schedule.scheduleId()).orElseThrow();
		assertEquals(Instant.parse("2026-05-28T10:00:00Z"), persisted.lastAcceptedDueAt());
		assertEquals(1, triggerRegistry.recorded.size());
	}

	@Test
	void catchUpOncePolicyAdvancesToLatestMissedDue() {
		InMemoryScheduleRegistry registry = new InMemoryScheduleRegistry();
		ScheduleService scheduleService = new ScheduleService(registry);
		RecordingTriggerRegistry triggerRegistry = new RecordingTriggerRegistry();
		ScheduleView schedule = scheduleService.createSchedule("every-minute", "customer-load", "* * * * *", "UTC", true, "minute tick");
		scheduleService.markLastAcceptedDueAt(schedule.scheduleId(), Instant.parse("2026-05-28T09:58:00Z"));

		Clock fixedClock = Clock.fixed(Instant.parse("2026-05-28T10:00:20Z"), ZoneOffset.UTC);
		ScheduleTriggerTickService tickService = new ScheduleTriggerTickService(
				scheduleService,
				triggerRegistry,
				30000,
				"schedule_tick",
				"scheduler",
				fixedClock,
				ScheduleTriggerTickService.MissedRunPolicy.CATCH_UP_ONCE,
				2000
		);

		tickService.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));
		ScheduleView persisted = scheduleService.findByScheduleId(schedule.scheduleId()).orElseThrow();
		assertEquals(Instant.parse("2026-05-28T10:00:00Z"), persisted.lastAcceptedDueAt());
		assertEquals(1, triggerRegistry.recorded.size());
	}

	@Test
	void catchUpAllPolicyDrainsBacklogWithinBound() {
		InMemoryScheduleRegistry registry = new InMemoryScheduleRegistry();
		ScheduleService scheduleService = new ScheduleService(registry);
		RecordingTriggerRegistry triggerRegistry = new RecordingTriggerRegistry();
		ScheduleView schedule = scheduleService.createSchedule("every-minute", "customer-load", "* * * * *", "UTC", true, "minute tick");
		scheduleService.markLastAcceptedDueAt(schedule.scheduleId(), Instant.parse("2026-05-28T09:58:00Z"));

		Clock fixedClock = Clock.fixed(Instant.parse("2026-05-28T10:00:20Z"), ZoneOffset.UTC);
		ScheduleTriggerTickService tickService = new ScheduleTriggerTickService(
				scheduleService,
				triggerRegistry,
				30000,
				"schedule_tick",
				"scheduler",
				fixedClock,
				ScheduleTriggerTickService.MissedRunPolicy.CATCH_UP_ALL,
				2000
		);

		tickService.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));
		ScheduleView persisted = scheduleService.findByScheduleId(schedule.scheduleId()).orElseThrow();
		assertEquals(Instant.parse("2026-05-28T10:00:00Z"), persisted.lastAcceptedDueAt());
		assertEquals(2, triggerRegistry.recorded.size());
	}

	@Test
	void skipsPausedOrDisabledSchedules() {
		ScheduleService scheduleService = new ScheduleService(new InMemoryScheduleRegistry());
		RecordingTriggerRegistry triggerRegistry = new RecordingTriggerRegistry();
		ScheduleView enabled = scheduleService.createSchedule("enabled-minute", "customer-load", "* * * * *", "UTC", true, "enabled");
		scheduleService.pause(enabled.scheduleId());
		scheduleService.createSchedule("disabled-minute", "customer-load", "* * * * *", "UTC", false, "disabled");

		Clock fixedClock = Clock.fixed(Instant.parse("2026-05-28T10:00:20Z"), ZoneOffset.UTC);
		ScheduleTriggerTickService tickService = new ScheduleTriggerTickService(
				scheduleService,
				triggerRegistry,
				30000,
				"schedule_tick",
				"scheduler",
				fixedClock
		);

		tickService.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));
		assertEquals(0, triggerRegistry.recorded.size());
	}

	@Test
	void skipsInvalidExpressions() {
		ScheduleService scheduleService = new ScheduleService(new InMemoryScheduleRegistry());
		RecordingTriggerRegistry triggerRegistry = new RecordingTriggerRegistry();
		scheduleService.createSchedule("bad-expression", "customer-load", "bad expression", "UTC", true, "invalid");

		Clock fixedClock = Clock.fixed(Instant.parse("2026-05-28T10:00:20Z"), ZoneOffset.UTC);
		ScheduleTriggerTickService tickService = new ScheduleTriggerTickService(
				scheduleService,
				triggerRegistry,
				30000,
				"schedule_tick",
				"scheduler",
				fixedClock
		);

		tickService.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));
		assertEquals(0, triggerRegistry.recorded.size());
	}

	@Test
	void nearSimultaneousPollersStillRecordExactlyOneTriggerForSameDueInstant() throws Exception {
		InMemoryScheduleRegistry registry = new InMemoryScheduleRegistry();
		ScheduleService scheduleService = new ScheduleService(registry);
		RecordingTriggerRegistry triggerRegistry = new RecordingTriggerRegistry();
		ScheduleView schedule = scheduleService.createSchedule("every-minute", "customer-load", "* * * * *", "UTC", true, "minute tick");

		Clock fixedClock = Clock.fixed(Instant.parse("2026-05-28T10:00:20Z"), ZoneOffset.UTC);
		ScheduleTriggerTickService first = new ScheduleTriggerTickService(scheduleService, triggerRegistry, 30000, "schedule_tick", "scheduler", fixedClock);
		ScheduleTriggerTickService second = new ScheduleTriggerTickService(scheduleService, triggerRegistry, 30000, "schedule_tick", "scheduler", fixedClock);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			executor.submit(() -> runConcurrentTick(first, fixedClock, ready, start));
			executor.submit(() -> runConcurrentTick(second, fixedClock, ready, start));
			ready.await(2, TimeUnit.SECONDS);
			start.countDown();
			executor.shutdown();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertEquals(1, triggerRegistry.recorded.size());
		assertEquals(1, triggerRegistry.listByScheduleId(schedule.scheduleId(), 10).size());
	}

	private static void runConcurrentTick(ScheduleTriggerTickService service,
	                                     Clock fixedClock,
	                                     CountDownLatch ready,
	                                     CountDownLatch start) {
		ready.countDown();
		awaitLatch(start);
		service.pollAndRecordDueSchedules(ZonedDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC));
	}

	private static void awaitLatch(CountDownLatch latch) {
		try {
			latch.await(2, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static final class RecordingTriggerRegistry implements TriggerEventRegistry {
		private final List<TriggerEventView> recorded = new ArrayList<>();
		private final java.util.Map<String, List<TriggerEventView>> bySchedule = new java.util.HashMap<>();

		@Override
		public TriggerEventView recordAccepted(String jobKey, String reason, String requestedBy, String message) {
			return recordAcceptedForSchedule("", jobKey, reason, requestedBy, message);
		}

		@Override
		public TriggerEventView recordAcceptedForSchedule(String scheduleId, String jobKey, String reason, String requestedBy, String message) {
			TriggerEventView event = new TriggerEventView(
					"te-" + (recorded.size() + 1),
					jobKey,
					"ACCEPTED",
					reason,
					requestedBy,
					Instant.parse("2026-05-28T10:00:20Z"),
					null,
					message
			);
			recorded.add(event);
			String normalizedScheduleId = scheduleId == null ? "" : scheduleId.trim();
			if (!normalizedScheduleId.isBlank()) {
				bySchedule.computeIfAbsent(normalizedScheduleId, ignored -> new ArrayList<>()).add(0, event);
			}
			return event;
		}

		@Override
		public List<TriggerEventView> listByJobKey(String jobKey, int limit) {
			return recorded.stream().filter(event -> event.jobKey().equals(jobKey)).limit(limit).toList();
		}

		@Override
		public List<TriggerEventView> listByScheduleId(String scheduleId, int limit) {
			return bySchedule.getOrDefault(scheduleId, List.of()).stream().limit(limit).toList();
		}
	}
}




