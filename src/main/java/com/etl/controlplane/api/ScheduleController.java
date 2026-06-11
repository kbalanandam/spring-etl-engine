package com.etl.controlplane.api;

import com.etl.controlplane.schedules.ScheduleService;
import com.etl.controlplane.schedules.ScheduleValidationException;
import com.etl.controlplane.schedules.ScheduleView;
import com.etl.controlplane.triggers.TriggerEventRegistry;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Clock;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

	private static final int DEFAULT_LIMIT = 25;
	private static final int MAX_LIMIT = 200;
	private static final int DEFAULT_TRIGGER_EVENT_LIMIT = 20;
	private static final int MAX_TRIGGER_EVENT_LIMIT = 200;
	private static final Clock RESPONSE_CLOCK = Clock.systemUTC();

	private final ScheduleService scheduleService;
	private final TriggerEventRegistry triggerEventRegistry;
	private final ScheduleResponseMapper scheduleResponseMapper;

	@Autowired
	public ScheduleController(ScheduleService scheduleService,
	                         TriggerEventRegistry triggerEventRegistry) {
		this(scheduleService, triggerEventRegistry, new ScheduleResponseMapper(RESPONSE_CLOCK));
	}

	ScheduleController(ScheduleService scheduleService,
	                   TriggerEventRegistry triggerEventRegistry,
	                   ScheduleResponseMapper scheduleResponseMapper) {
		this.scheduleService = scheduleService;
		this.triggerEventRegistry = triggerEventRegistry;
		this.scheduleResponseMapper = scheduleResponseMapper;
	}

	@GetMapping
	public ScheduleListResponse listSchedules(@RequestParam(name = "limit", required = false) Integer limit) {
		int effectiveLimit = clampLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
		var schedules = scheduleService.list(effectiveLimit).stream().map(scheduleResponseMapper::toViewResponse).toList();
		return new ScheduleListResponse(schedules, 0, effectiveLimit, schedules.size());
	}

	@GetMapping("/{scheduleId}")
	public ResponseEntity<ScheduleViewResponse> getSchedule(@PathVariable String scheduleId) {
		return scheduleService.findByScheduleId(scheduleId)
				.map(schedule -> ResponseEntity.ok(scheduleResponseMapper.toViewResponse(schedule)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{scheduleId}/trigger-events")
	public ResponseEntity<TriggerEventListResponse> scheduleTriggerEvents(@PathVariable String scheduleId,
	                                                                     @RequestParam(name = "limit", required = false) Integer limit) {
		int effectiveLimit = clampLimit(limit, DEFAULT_TRIGGER_EVENT_LIMIT, MAX_TRIGGER_EVENT_LIMIT);
		return scheduleService.findByScheduleId(scheduleId)
				.map(schedule -> {
					var events = triggerEventRegistry.listByScheduleId(schedule.scheduleId(), effectiveLimit);
					return ResponseEntity.ok(new TriggerEventListResponse(events, 0, effectiveLimit, events.size()));
				})
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<?> createSchedule(@RequestBody CreateScheduleRequest request) {
		try {
			ScheduleView created = scheduleService.createSchedule(
					request.scheduleKey(),
					request.selectedJobKey(),
					request.expression(),
					request.timezone(),
					resolveEnabledDefault(request.enabled()),
					request.description()
			);
			return ResponseEntity.status(HttpStatus.CREATED).body(scheduleResponseMapper.toViewResponse(created));
		} catch (IllegalStateException conflict) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		} catch (ScheduleValidationException invalid) {
			return toBadRequest(invalid.reasonToken(), invalid.getMessage());
		} catch (IllegalArgumentException invalid) {
			return toBadRequest("invalid_schedule", invalid.getMessage());
		}
	}

	@PutMapping("/{scheduleId}")
	public ResponseEntity<?> updateSchedule(@PathVariable String scheduleId,
	                                                          @RequestBody UpdateScheduleRequest request) {
		try {
			return scheduleService.updateSchedule(
					scheduleId,
					request.selectedJobKey(),
					request.expression(),
					request.timezone(),
					resolveEnabledDefault(request.enabled()),
					request.description()
			)
					.map(schedule -> ResponseEntity.ok(scheduleResponseMapper.toViewResponse(schedule)))
					.orElseGet(() -> ResponseEntity.notFound().build());
		} catch (ScheduleValidationException invalid) {
			return toBadRequest(invalid.reasonToken(), invalid.getMessage());
		} catch (IllegalArgumentException invalid) {
			return toBadRequest("invalid_schedule", invalid.getMessage());
		}
	}

	@PostMapping("/{scheduleId}:enable")
	public ResponseEntity<ScheduleStateChangeResponse> enable(@PathVariable String scheduleId) {
		return applyStateChange(scheduleService.enable(scheduleId));
	}

	@PostMapping("/{scheduleId}:disable")
	public ResponseEntity<ScheduleStateChangeResponse> disable(@PathVariable String scheduleId) {
		return applyStateChange(scheduleService.disable(scheduleId));
	}

	@PostMapping("/{scheduleId}:pause")
	public ResponseEntity<ScheduleStateChangeResponse> pause(@PathVariable String scheduleId) {
		return applyStateChange(scheduleService.pause(scheduleId));
	}

	@PostMapping("/{scheduleId}:resume")
	public ResponseEntity<ScheduleStateChangeResponse> resume(@PathVariable String scheduleId) {
		return applyStateChange(scheduleService.resume(scheduleId));
	}

	private boolean resolveEnabledDefault(Boolean enabled) {
		return enabled == null || enabled;
	}

	private int clampLimit(Integer requestedLimit, int defaultLimit, int maxLimit) {
		if (requestedLimit == null) {
			return defaultLimit;
		}
		return Math.max(1, Math.min(requestedLimit, maxLimit));
	}

	private ResponseEntity<ScheduleStateChangeResponse> applyStateChange(Optional<ScheduleView> maybeSchedule) {
		return maybeSchedule
				.map(scheduleResponseMapper::toStateChangeResponse)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	private ResponseEntity<ScheduleValidationErrorResponse> toBadRequest(String reason, String message) {
		return ResponseEntity.badRequest().body(new ScheduleValidationErrorResponse(reason, message));
	}

}



