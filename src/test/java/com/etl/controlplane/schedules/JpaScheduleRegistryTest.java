package com.etl.controlplane.schedules;

import com.etl.controlplane.persistence.jpa.JpaControlPlanePkAllocator;
import com.etl.controlplane.persistence.jpa.entity.Schedule;
import com.etl.controlplane.persistence.jpa.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaScheduleRegistryTest {

	@Test
	void insertsNewScheduleWithAllocatedPrimaryKey() {
		ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);
		JpaScheduleRegistry registry = new JpaScheduleRegistry(scheduleRepository, pkAllocator, "controlplane-test");

		when(scheduleRepository.findByScheduleIdIgnoreCase("sch-1")).thenReturn(Optional.empty());
		when(pkAllocator.nextPk("controlplane_schedule_pk")).thenReturn(31L);
		when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ScheduleView saved = registry.upsert(schedule("sch-1", "daily-a", LocalDateTime.of(2026, 8, 19, 6, 30)));

		assertEquals("sch-1", saved.scheduleId());
		assertEquals("daily-a", saved.scheduleKey());
		assertTrue(saved.enabled());

		ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
		verify(scheduleRepository).save(captor.capture());
		Schedule entity = captor.getValue();
		assertEquals(31L, entity.getSchedulePk());
		assertEquals("controlplane-test", entity.getCreatedBy());
	}

	@Test
	void advancesLastAcceptedDueAtOnlyWhenNewDueTimeIsLater() {
		ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);
		JpaScheduleRegistry registry = new JpaScheduleRegistry(scheduleRepository, pkAllocator, "controlplane-test");

		Schedule schedule = new Schedule();
		schedule.setSchedulePk(7L);
		schedule.setScheduleId("sch-2");
		schedule.setLastAcceptedDueAt(LocalDateTime.of(2026, 8, 19, 8, 0));
		when(scheduleRepository.findByScheduleIdIgnoreCase("sch-2")).thenReturn(Optional.of(schedule));
		when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

		boolean notAdvanced = registry.tryAdvanceLastAcceptedDueAt("sch-2", Instant.parse("2026-08-19T07:59:59Z"));
		boolean advanced = registry.tryAdvanceLastAcceptedDueAt("sch-2", Instant.parse("2026-08-19T08:30:00Z"));

		assertFalse(notAdvanced);
		assertTrue(advanced);
		assertEquals(LocalDateTime.of(2026, 8, 19, 8, 30), schedule.getLastAcceptedDueAt());
	}

	@Test
	void returnsEmptyWhenFindByKeyGetsBlankValue() {
		ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);
		JpaScheduleRegistry registry = new JpaScheduleRegistry(scheduleRepository, pkAllocator, "controlplane-test");

		assertTrue(registry.findByScheduleKey(" ").isEmpty());
		verify(scheduleRepository, never()).findByScheduleKeyIgnoreCase(anyString());
	}

	private ScheduleView schedule(String scheduleId, String scheduleKey, LocalDateTime updatedAt) {
		return new ScheduleView(
				scheduleId,
				scheduleKey,
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"test",
				LocalDateTime.of(2026, 8, 19, 6, 0),
				updatedAt,
				"watcher",
				LocalDateTime.of(2026, 8, 19, 6, 0).toInstant(ZoneOffset.UTC)
		);
	}
}

