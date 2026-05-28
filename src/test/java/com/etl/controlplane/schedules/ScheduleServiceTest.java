package com.etl.controlplane.schedules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleServiceTest {

	@Test
	void createsScheduleAndPreventsDuplicateKeys() {
		ScheduleService service = new ScheduleService(new InMemoryScheduleRegistry());
		ScheduleView created = service.createSchedule("daily-customers", "customer-load", "0 0 * * *", "UTC", true, "daily run");

		assertEquals("daily-customers", created.scheduleKey());
		assertThrows(IllegalStateException.class,
				() -> service.createSchedule("daily-customers", "customer-load", "0 5 * * *", "UTC", true, "dup"));
	}

	@Test
	void updatesStateTransitions() {
		ScheduleService service = new ScheduleService(new InMemoryScheduleRegistry());
		ScheduleView created = service.createSchedule("daily-customers", "customer-load", "0 0 * * *", "UTC", true, "daily run");

		ScheduleView paused = service.pause(created.scheduleId()).orElseThrow();
		assertTrue(paused.paused());

		ScheduleView resumed = service.resume(created.scheduleId()).orElseThrow();
		assertFalse(resumed.paused());
		assertTrue(resumed.enabled());

		ScheduleView disabled = service.disable(created.scheduleId()).orElseThrow();
		assertFalse(disabled.enabled());
	}

	@Test
	void updatesScheduleMetadata() {
		ScheduleService service = new ScheduleService(new InMemoryScheduleRegistry());
		ScheduleView created = service.createSchedule("daily-customers", "customer-load", "0 0 * * *", "UTC", true, "daily run");

		ScheduleView updated = service.updateSchedule(
				created.scheduleId(),
				"customer-load",
				"0 15 * * *",
				"UTC",
				true,
				"updated"
		).orElseThrow();

		assertEquals("0 15 * * *", updated.expression());
		assertEquals("updated", updated.description());
	}
}


