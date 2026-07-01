package com.etl.controlplane.api;

import com.etl.controlplane.jobs.SelectedJobLaunchService;
import com.etl.controlplane.schedules.ScheduleService;
import com.etl.controlplane.schedules.ScheduleView;
import com.etl.controlplane.triggers.TriggerEventRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {
	private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);
	private static final int RECENT_TRIGGER_SCAN_LIMIT = 5;
	private static final int DEFAULT_PAGE = 0;
	private static final Duration MANUAL_TRIGGER_DUPLICATE_SUPPRESSION_WINDOW = Duration.ofSeconds(5);

	private final ScheduleService scheduleService;
	private final TriggerEventRegistry triggerEventRegistry;
	private final SelectedJobLaunchService selectedJobLaunchService;
	private final ScheduleResponseMapper scheduleResponseMapper;
	private final ScheduleApiLimitPolicy scheduleApiLimitPolicy;
	private final Clock clock;

	@Autowired
	public ScheduleController(ScheduleService scheduleService,
	                         TriggerEventRegistry triggerEventRegistry,
	                         SelectedJobLaunchService selectedJobLaunchService,
	                   ScheduleResponseMapper scheduleResponseMapper,
	                   ScheduleApiLimitPolicy scheduleApiLimitPolicy) {
		this(scheduleService, triggerEventRegistry, selectedJobLaunchService, scheduleResponseMapper, scheduleApiLimitPolicy, Clock.systemUTC());
	}

	ScheduleController(ScheduleService scheduleService,
	                  TriggerEventRegistry triggerEventRegistry,
	                  SelectedJobLaunchService selectedJobLaunchService,
	                  ScheduleResponseMapper scheduleResponseMapper,
	                  ScheduleApiLimitPolicy scheduleApiLimitPolicy,
	                  Clock clock) {
		this.scheduleService = scheduleService;
		this.triggerEventRegistry = triggerEventRegistry;
		this.selectedJobLaunchService = selectedJobLaunchService;
		this.scheduleResponseMapper = scheduleResponseMapper;
		this.scheduleApiLimitPolicy = scheduleApiLimitPolicy;
		this.clock = clock == null ? Clock.systemUTC() : clock;
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
	                                                                     @RequestParam(name = "limit", required = false) Integer limit,
	                                                                     @RequestParam(name = "size", required = false) Integer size,
	                                                                     @RequestParam(name = "page", required = false) Integer page) {
		int effectiveSize = scheduleApiLimitPolicy.triggerEventLimit(size == null ? limit : size);
		int effectivePage = clampPage(page);
		int offset = safeOffset(effectivePage, effectiveSize);
		return scheduleService.findByScheduleId(scheduleId)
				.map(schedule -> {
					var events = triggerEventRegistry.listByScheduleId(schedule.scheduleId(), offset, effectiveSize);
					long totalItems = triggerEventRegistry.countByScheduleId(schedule.scheduleId());
					return ResponseEntity.ok(new TriggerEventListResponse(events, effectivePage, effectiveSize, totalItems));
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

	@PostMapping("/{scheduleId}:trigger-now")
	public ResponseEntity<TriggerNowDecisionResponse> triggerNow(@PathVariable String scheduleId,
	                                                             @RequestBody(required = false) TriggerNowRequest request) {
		String reason = request == null || request.reason() == null || request.reason().isBlank()
				? "manual_operator_request"
				: request.reason().trim();
		String requestedBy = request == null || request.requestedBy() == null || request.requestedBy().isBlank()
				? "operator"
				: request.requestedBy().trim();
		log.info("CONTROLPLANE_TRIGGER event=trigger_now_requested scope=SCHEDULE scheduleId={} reason={} requestedBy={}",
				scheduleId, reason, requestedBy);

		return scheduleService.findByScheduleId(scheduleId)
				.map(schedule -> {
					Instant now = Instant.now(clock);

					var recentDuplicate = triggerEventRegistry.listByScheduleId(schedule.scheduleId(), RECENT_TRIGGER_SCAN_LIMIT).stream()
							.filter(event -> "ACCEPTED".equalsIgnoreCase(event.decisionStatus()))
							.filter(event -> reason.equals(event.reason()))
							.filter(event -> requestedBy.equals(event.requestedBy()))
							.filter(event -> event.requestedAt() != null)
							.filter(event -> Duration.between(event.requestedAt(), now).compareTo(MANUAL_TRIGGER_DUPLICATE_SUPPRESSION_WINDOW) < 0)
							.findFirst();
					if (recentDuplicate.isPresent()) {
						var duplicate = recentDuplicate.get();
						String message = "Duplicate trigger request suppressed because a recent accepted schedule trigger already exists for this schedule and operator.";
						log.info("CONTROLPLANE_TRIGGER event=trigger_now_duplicate_suppressed scope=SCHEDULE scheduleId={} jobKey={} reason={} requestedBy={} triggerEventId={}",
								schedule.scheduleId(), schedule.selectedJobKey(), reason, requestedBy, duplicate.triggerEventId());
						return ResponseEntity.accepted().body(new TriggerNowDecisionResponse(
								schedule.selectedJobKey(),
								"DUPLICATE_SUPPRESSED",
								message,
								duplicate.triggerEventId()
						));
					}

					String message = "Schedule trigger request accepted for scheduleId='" + schedule.scheduleId() + "' reason='" + reason + "' requestedBy='" + requestedBy + "'.";
					var triggerEvent = triggerEventRegistry.recordAcceptedForSchedule(schedule.scheduleId(), schedule.selectedJobKey(), reason, requestedBy, message);
					SelectedJobLaunchService.LaunchResult launchResult = selectedJobLaunchService.launchSelectedJob(
							schedule.selectedJobKey(),
							"SCHEDULE",
							schedule.scheduleId());
					log.info("CONTROLPLANE_TRIGGER event=trigger_now_accepted scope=SCHEDULE scheduleId={} jobKey={} reason={} requestedBy={} triggerEventId={} launchStarted={} launchMessage={}",
							schedule.scheduleId(),
							schedule.selectedJobKey(),
							reason,
							requestedBy,
							triggerEvent.triggerEventId(),
							launchResult.started(),
							launchResult.message());
					String responseMessage = message + " " + launchResult.message();
					String decisionStatus = launchResult.started() ? "ACCEPTED" : "LAUNCH_SKIPPED";
					return ResponseEntity.accepted().body(new TriggerNowDecisionResponse(
							schedule.selectedJobKey(),
							decisionStatus,
							responseMessage,
							triggerEvent.triggerEventId()
					));
				})
				.orElseGet(() -> {
					log.warn("CONTROLPLANE_TRIGGER event=trigger_now_rejected scope=SCHEDULE decisionStatus=NOT_FOUND scheduleId={} reason={} requestedBy={} message=unknown_schedule_id",
							scheduleId, reason, requestedBy);
					return ResponseEntity.notFound().build();
				});
	}


	private ResponseEntity<ScheduleStateChangeResponse> applyStateChange(Optional<ScheduleView> maybeSchedule) {
		return maybeSchedule
				.map(scheduleResponseMapper::toStateChangeResponse)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	private int clampPage(Integer requestedPage) {
		if (requestedPage == null) {
			return DEFAULT_PAGE;
		}
		return Math.max(0, requestedPage);
	}

	private int safeOffset(int page, int size) {
		long offset = (long) page * size;
		if (offset > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int) offset;
	}


}
