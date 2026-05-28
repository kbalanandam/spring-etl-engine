package com.etl.controlplane.schedules;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryScheduleRegistryTest {

	@Test
	void upsertsAndListsByUpdatedAtDesc() {
		InMemoryScheduleRegistry registry = new InMemoryScheduleRegistry();
		registry.upsert(schedule("sch-1", "daily-a", LocalDateTime.parse("2026-05-28T08:00:00")));
		registry.upsert(schedule("sch-2", "daily-b", LocalDateTime.parse("2026-05-28T09:00:00")));

		List<ScheduleView> schedules = registry.list(10);
		assertEquals(2, schedules.size());
		assertEquals("sch-2", schedules.get(0).scheduleId());
	}

	@Test
	void findsByIdAndKey() {
		InMemoryScheduleRegistry registry = new InMemoryScheduleRegistry();
		registry.upsert(schedule("sch-1", "Daily-A", LocalDateTime.parse("2026-05-28T08:00:00")));

		assertTrue(registry.findByScheduleId("sch-1").isPresent());
		assertTrue(registry.findByScheduleKey("daily-a").isPresent());
	}

	private ScheduleView schedule(String id, String key, LocalDateTime updatedAt) {
		return new ScheduleView(
				id,
				key.toLowerCase(),
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"desc",
				updatedAt.minusHours(1),
				updatedAt,
				null
		);
	}
}

