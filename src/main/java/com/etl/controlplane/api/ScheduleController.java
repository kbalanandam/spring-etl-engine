package com.etl.controlplane.api;

import com.etl.controlplane.schedules.ScheduleService;
import com.etl.controlplane.schedules.ScheduleView;
import com.etl.controlplane.triggers.TriggerEventRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

	private static final int DEFAULT_LIMIT = 25;
	private static final int MAX_LIMIT = 200;
	private static final int DEFAULT_TRIGGER_EVENT_LIMIT = 20;
	private static final int MAX_TRIGGER_EVENT_LIMIT = 200;

	private final ScheduleService scheduleService;
	private final TriggerEventRegistry triggerEventRegistry;

	public ScheduleController(ScheduleService scheduleService,
	                         TriggerEventRegistry triggerEventRegistry) {
		this.scheduleService = scheduleService;
		this.triggerEventRegistry = triggerEventRegistry;
	}

	@GetMapping
	public ScheduleListResponse listSchedules(@RequestParam(name = "limit", required = false) Integer limit) {
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
		var schedules = scheduleService.list(effectiveLimit).stream().map(this::toResponse).toList();
		return new ScheduleListResponse(schedules, 0, effectiveLimit, schedules.size());
	}

	@GetMapping("/{scheduleId}")
	public ResponseEntity<ScheduleViewResponse> getSchedule(@PathVariable String scheduleId) {
		return scheduleService.findByScheduleId(scheduleId)
				.map(schedule -> ResponseEntity.ok(toResponse(schedule)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{scheduleId}/trigger-events")
	public ResponseEntity<TriggerEventListResponse> scheduleTriggerEvents(@PathVariable String scheduleId,
	                                                                     @RequestParam(name = "limit", required = false) Integer limit) {
		int effectiveLimit = limit == null
				? DEFAULT_TRIGGER_EVENT_LIMIT
				: Math.max(1, Math.min(limit, MAX_TRIGGER_EVENT_LIMIT));
		return scheduleService.findByScheduleId(scheduleId)
				.map(schedule -> {
					var events = triggerEventRegistry.listByScheduleId(schedule.scheduleId(), effectiveLimit);
					return ResponseEntity.ok(new TriggerEventListResponse(events, 0, effectiveLimit, events.size()));
				})
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<ScheduleViewResponse> createSchedule(@RequestBody CreateScheduleRequest request) {
		try {
			ScheduleView created = scheduleService.createSchedule(
					request.scheduleKey(),
					request.selectedJobKey(),
					request.expression(),
					request.timezone(),
					request.enabled() == null || request.enabled(),
					request.description()
			);
			return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
		} catch (IllegalStateException conflict) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		} catch (IllegalArgumentException invalid) {
			return ResponseEntity.badRequest().build();
		}
	}

	@PutMapping("/{scheduleId}")
	public ResponseEntity<ScheduleViewResponse> updateSchedule(@PathVariable String scheduleId,
	                                                          @RequestBody UpdateScheduleRequest request) {
		try {
			return scheduleService.updateSchedule(
					scheduleId,
					request.selectedJobKey(),
					request.expression(),
					request.timezone(),
					request.enabled() == null || request.enabled(),
					request.description()
			)
					.map(schedule -> ResponseEntity.ok(toResponse(schedule)))
					.orElseGet(() -> ResponseEntity.notFound().build());
		} catch (IllegalArgumentException invalid) {
			return ResponseEntity.badRequest().build();
		}
	}

	@PostMapping("/{scheduleId}:enable")
	public ResponseEntity<ScheduleStateChangeResponse> enable(@PathVariable String scheduleId) {
		return scheduleService.enable(scheduleId)
				.map(this::toStateResponse)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{scheduleId}:disable")
	public ResponseEntity<ScheduleStateChangeResponse> disable(@PathVariable String scheduleId) {
		return scheduleService.disable(scheduleId)
				.map(this::toStateResponse)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{scheduleId}:pause")
	public ResponseEntity<ScheduleStateChangeResponse> pause(@PathVariable String scheduleId) {
		return scheduleService.pause(scheduleId)
				.map(this::toStateResponse)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{scheduleId}:resume")
	public ResponseEntity<ScheduleStateChangeResponse> resume(@PathVariable String scheduleId) {
		return scheduleService.resume(scheduleId)
				.map(this::toStateResponse)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	private ScheduleViewResponse toResponse(ScheduleView schedule) {
		return new ScheduleViewResponse(
				schedule.scheduleId(),
				schedule.scheduleKey(),
				schedule.selectedJobKey(),
				schedule.expression(),
				schedule.timezone(),
				schedule.enabled(),
				schedule.paused(),
				schedule.description(),
				schedule.updatedAt()
		);
	}

	private ScheduleStateChangeResponse toStateResponse(ScheduleView schedule) {
		return new ScheduleStateChangeResponse(
				schedule.scheduleId(),
				schedule.enabled(),
				schedule.paused(),
				schedule.updatedAt()
		);
	}
}



