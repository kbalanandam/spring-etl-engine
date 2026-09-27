package com.etl.controlplane.triggers;

import com.etl.controlplane.persistence.jpa.JpaControlPlanePkAllocator;
import com.etl.controlplane.persistence.jpa.entity.RunRecord;
import com.etl.controlplane.persistence.jpa.entity.Schedule;
import com.etl.controlplane.persistence.jpa.entity.TriggerEvent;
import com.etl.controlplane.persistence.jpa.entity.TriggerSource;
import com.etl.controlplane.persistence.jpa.repository.RunRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.ScheduleRepository;
import com.etl.controlplane.persistence.jpa.repository.TriggerEventRepository;
import com.etl.controlplane.persistence.jpa.repository.TriggerSourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaTriggerEventRegistryTest {

	@Test
	void recordsAcceptedManualEventUsingJpaRepositories() {
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		TriggerSourceRepository triggerSourceRepository = mock(TriggerSourceRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);
		JpaTriggerEventRegistry registry = new JpaTriggerEventRegistry(
				triggerEventRepository,
				triggerSourceRepository,
				runRecordRepository,
				scheduleRepository,
				pkAllocator,
				10,
				"controlplane-test"
		);

		TriggerSource manualSource = new TriggerSource();
		manualSource.setTriggerSourcePk(1L);
		manualSource.setSourceCode("MANUAL");
		when(triggerSourceRepository.findBySourceCodeIgnoreCase("MANUAL")).thenReturn(Optional.of(manualSource));
		when(pkAllocator.nextPk("controlplane_trigger_event_pk")).thenReturn(15L);
		when(triggerEventRepository.save(any(TriggerEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(triggerEventRepository.countByJobKey("customer-load")).thenReturn(1L);

		TriggerEventView recorded = registry.recordAccepted("customer-load", "manual_operator_request", "operator-ui", "queued");

		assertEquals("ACCEPTED", recorded.decisionStatus());
		assertEquals("MANUAL", recorded.triggerOrigin());
		assertTrue(recorded.triggerEventId().startsWith("te-"));

		ArgumentCaptor<TriggerEvent> eventCaptor = ArgumentCaptor.forClass(TriggerEvent.class);
		verify(triggerEventRepository).save(eventCaptor.capture());
		TriggerEvent saved = eventCaptor.getValue();
		assertEquals(15L, saved.getTriggerEventPk());
		assertEquals("customer-load", saved.getJobKey());
		assertEquals("MANUAL", saved.getTriggerOrigin());
		assertEquals("controlplane-test", saved.getCreatedBy());
	}

	@Test
	void paginatesScheduleEventsWithOffsetAndLimit() {
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		TriggerSourceRepository triggerSourceRepository = mock(TriggerSourceRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);
		JpaTriggerEventRegistry registry = new JpaTriggerEventRegistry(
				triggerEventRepository,
				triggerSourceRepository,
				runRecordRepository,
				scheduleRepository,
				pkAllocator,
				10,
				"controlplane-test"
		);

		Schedule schedule = new Schedule();
		schedule.setSchedulePk(7L);
		schedule.setScheduleId("nightly");
		when(scheduleRepository.findByScheduleIdIgnoreCase("nightly")).thenReturn(Optional.of(schedule));

		TriggerEvent newest = event("te-3", "SCHEDULE", LocalDateTime.of(2026, 8, 19, 6, 0));
		TriggerEvent middle = event("te-2", "SCHEDULE", LocalDateTime.of(2026, 8, 19, 5, 0));
		TriggerEvent oldest = event("te-1", "SCHEDULE", LocalDateTime.of(2026, 8, 19, 4, 0));
		when(triggerEventRepository.findBySchedulePkOrderByTriggerEventPkDesc(7L))
				.thenReturn(List.of(newest, middle, oldest));

		List<TriggerEventView> page = registry.listByScheduleId("nightly", 1, 1);

		assertEquals(1, page.size());
		assertEquals("te-2", page.get(0).triggerEventId());
		assertEquals(LocalDateTime.of(2026, 8, 19, 5, 0).toInstant(ZoneOffset.UTC), page.get(0).requestedAt());
	}

	@Test
	void returnsZeroScheduleCountWhenScheduleDoesNotExist() {
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		TriggerSourceRepository triggerSourceRepository = mock(TriggerSourceRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);
		JpaTriggerEventRegistry registry = new JpaTriggerEventRegistry(
				triggerEventRepository,
				triggerSourceRepository,
				runRecordRepository,
				scheduleRepository,
				pkAllocator,
				10,
				"controlplane-test"
		);

		when(scheduleRepository.findByScheduleIdIgnoreCase(anyString())).thenReturn(Optional.empty());

		assertEquals(0L, registry.countByScheduleId("missing"));
		verify(triggerEventRepository, never()).countBySchedulePk(any());
	}

	@Test
	void resolvesLaunchedRunIdFromRunRecordWhenTriggerRowHasNotBeenBackfilledYet() {
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		TriggerSourceRepository triggerSourceRepository = mock(TriggerSourceRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);
		JpaTriggerEventRegistry registry = new JpaTriggerEventRegistry(
				triggerEventRepository,
				triggerSourceRepository,
				runRecordRepository,
				scheduleRepository,
				pkAllocator,
				10,
				"controlplane-test"
		);

		TriggerEvent event = event("te-21", "MANUAL", LocalDateTime.of(2026, 8, 19, 6, 0));
		event.setTriggerEventPk(21L);
		when(triggerEventRepository.findByJobKeyOrderByTriggerEventPkDesc("customer-load"))
				.thenReturn(List.of(event));

		RunRecord linkedRun = new RunRecord();
		linkedRun.setRunRecordPk(41L);
		linkedRun.setJobExecutionId(9876L);
		when(runRecordRepository.findFirstByTriggerEventIdIgnoreCaseOrderByStartedAtDescRunRecordPkDesc("te-21"))
				.thenReturn(Optional.of(linkedRun));

		List<TriggerEventView> events = registry.listByJobKey("customer-load", 1);

		assertEquals(1, events.size());
		assertEquals("9876", events.get(0).launchedRunId());
	}

	private TriggerEvent event(String triggerEventId, String triggerOrigin, LocalDateTime requestedAt) {
		TriggerEvent event = new TriggerEvent();
		event.setTriggerEventId(triggerEventId);
		event.setJobKey("customer-load");
		event.setDecisionStatus("ACCEPTED");
		event.setReason("reason");
		event.setRequestedBy("user");
		event.setRequestedAt(requestedAt);
		event.setMessage("message");
		event.setTriggerOrigin(triggerOrigin);
		return event;
	}
}



