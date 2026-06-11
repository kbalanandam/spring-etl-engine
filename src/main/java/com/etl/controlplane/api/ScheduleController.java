package com.etl.controlplane.api;

import com.etl.controlplane.schedules.ScheduleService;
import com.etl.controlplane.schedules.ScheduleView;
import com.etl.controlplane.triggers.TriggerEventRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

	private final ScheduleService scheduleService;
	private final TriggerEventRegistry triggerEventRegistry;
	private final ScheduleResponseMapper scheduleResponseMapper;
	private final ScheduleApiLimitPolicy scheduleApiLimitPolicy;

	public ScheduleController(ScheduleService scheduleService,
	                         TriggerEventRegistry triggerEventRegistry,
	                   ScheduleResponseMapper scheduleResponseMapper,
	                   ScheduleApiLimitPolicy scheduleApiLimitPolicy) {
		this.scheduleService = scheduleService;
		this.triggerEventRegistry = triggerEventRegistry;
		this.scheduleResponseMapper = scheduleResponseMapper;
		this.scheduleApiLimitPolicy = scheduleApiLimitPolicy;
	}

	@GetMapping
	public ScheduleListResponse listSchedules(@RequestParam(name = "limit", required = false) Integer limit) {
		int effectiveLimit = scheduleApiLimitPolicy.scheduleLimit(limit);
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
		int effectiveLimit = scheduleApiLimitPolicy.triggerEventLimit(limit);
		return scheduleService.findByScheduleId(scheduleId)
				.map(schedule -> {
					var events = triggerEventRegistry.listByScheduleId(schedule.scheduleId(), effectiveLimit);
					return ResponseEntity.ok(new TriggerEventListResponse(events, 0, effectiveLimit, events.size()));
				})
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<ScheduleViewResponse> createSchedule(@RequestBody CreateScheduleRequest request) {
		ScheduleView created = scheduleService.createSchedule(
				request.scheduleKey(),
				request.selectedJobKey(),
				request.expression(),
				request.timezone(),
				scheduleApiLimitPolicy.resolveEnabledDefault(request.enabled()),
				request.description()
		);
		return ResponseEntity.status(201).body(scheduleResponseMapper.toViewResponse(created));
	}

	@PutMapping("/{scheduleId}")
	public ResponseEntity<?> updateSchedule(@PathVariable String scheduleId,
	                                                          @RequestBody UpdateScheduleRequest request) {
		return scheduleService.updateSchedule(
					scheduleId,
					request.selectedJobKey(),
					request.expression(),
					request.timezone(),
					scheduleApiLimitPolicy.resolveEnabledDefault(request.enabled()),
					request.description()
			)
				.map(schedule -> ResponseEntity.ok(scheduleResponseMapper.toViewResponse(schedule)))
				.orElseGet(() -> ResponseEntity.notFound().build());
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


	private ResponseEntity<ScheduleStateChangeResponse> applyStateChange(Optional<ScheduleView> maybeSchedule) {
		return maybeSchedule
				.map(scheduleResponseMapper::toStateChangeResponse)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}


}



