package com.etl.controlplane.schedules;

import com.etl.controlplane.jobs.JobBundleReadModelService;
import com.etl.controlplane.jobs.JobBundleSummaryView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

	@Test
	void rejectsUnknownSelectedJobInStrictMode() {
		JobBundleReadModelService readModelService = mock(JobBundleReadModelService.class);
		when(readModelService.findBundle("missing-job")).thenReturn(Optional.empty());
		ScheduleService service = new ScheduleService(
				new InMemoryScheduleRegistry(),
				new ScheduleDefinitionValidator(readModelService)
		);

		ScheduleValidationException ex = assertThrows(ScheduleValidationException.class,
				() -> service.createSchedule("daily-customers", "missing-job", "0 0 * * *", "UTC", true, "daily run"));
		assertEquals("unknown_selected_job", ex.reasonToken());
	}

	@Test
	void rejectsNonReadySelectedJobInStrictMode() {
		JobBundleReadModelService readModelService = mock(JobBundleReadModelService.class);
		when(readModelService.findBundle("inactive-job")).thenReturn(Optional.of(
				new JobBundleSummaryView("inactive-job", "Inactive Job", "x/job-config.yaml", "INACTIVE", List.of("inactive"))
		));
		ScheduleService service = new ScheduleService(
				new InMemoryScheduleRegistry(),
				new ScheduleDefinitionValidator(readModelService)
		);

		ScheduleValidationException ex = assertThrows(ScheduleValidationException.class,
				() -> service.createSchedule("daily-customers", "inactive-job", "0 0 * * *", "UTC", true, "daily run"));
		assertEquals("selected_job_not_ready", ex.reasonToken());
	}

	@Test
	void rejectsInvalidExpressionAndTimezone() {
		ScheduleService service = new ScheduleService(new InMemoryScheduleRegistry());

		ScheduleValidationException expressionEx = assertThrows(ScheduleValidationException.class,
				() -> service.createSchedule("daily-customers", "customer-load", "bad-expression", "UTC", true, "daily run"));
		assertEquals("invalid_expression", expressionEx.reasonToken());

		ScheduleValidationException timezoneEx = assertThrows(ScheduleValidationException.class,
				() -> service.createSchedule("daily-customers-2", "customer-load", "0 0 * * *", "Bad/Timezone", true, "daily run"));
		assertEquals("invalid_timezone", timezoneEx.reasonToken());
	}
}


