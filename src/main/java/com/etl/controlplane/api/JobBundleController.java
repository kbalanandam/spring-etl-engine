package com.etl.controlplane.api;

import com.etl.controlplane.jobs.JobBundleReadModelService;
import com.etl.controlplane.jobs.SelectedJobLaunchService;
import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.triggers.TriggerEventRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobBundleController {
	private static final Logger log = LoggerFactory.getLogger(JobBundleController.class);
	private static final int DEFAULT_TRIGGER_EVENT_LIMIT = 20;
	private static final int MAX_TRIGGER_EVENT_LIMIT = 200;
	private static final int DEFAULT_RECENT_RUN_LIMIT = 10;
	private static final int MAX_RECENT_RUN_LIMIT = 200;
	private static final int DEFAULT_PAGE = 0;
	private static final int RECENT_TRIGGER_SCAN_LIMIT = 5;
	private static final Duration MANUAL_TRIGGER_DUPLICATE_SUPPRESSION_WINDOW = Duration.ofSeconds(5);

	private final JobBundleReadModelService jobBundleReadModelService;
	private final RunSummaryReadModelService runSummaryReadModelService;
	private final TriggerEventRegistry triggerEventRegistry;
	private final SelectedJobLaunchService selectedJobLaunchService;
	private final Clock clock;

	@Autowired
	public JobBundleController(JobBundleReadModelService jobBundleReadModelService,
	                           RunSummaryReadModelService runSummaryReadModelService,
	                           TriggerEventRegistry triggerEventRegistry,
	                           SelectedJobLaunchService selectedJobLaunchService) {
		this(jobBundleReadModelService, runSummaryReadModelService, triggerEventRegistry, selectedJobLaunchService, Clock.systemUTC());
	}

	JobBundleController(JobBundleReadModelService jobBundleReadModelService,
	                   RunSummaryReadModelService runSummaryReadModelService,
	                   TriggerEventRegistry triggerEventRegistry,
	                   SelectedJobLaunchService selectedJobLaunchService,
	                   Clock clock) {
		this.jobBundleReadModelService = jobBundleReadModelService;
		this.runSummaryReadModelService = runSummaryReadModelService;
		this.triggerEventRegistry = triggerEventRegistry;
		this.selectedJobLaunchService = selectedJobLaunchService;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	@GetMapping
	public JobBundleListResponse listJobs() {
		var bundles = jobBundleReadModelService.listBundles();
		return new JobBundleListResponse(bundles, 0, bundles.size(), bundles.size());
	}

	@GetMapping("/{jobKey}")
	public ResponseEntity<JobBundleDetailResponse> jobDetail(@PathVariable String jobKey,
	                                                        @RequestParam(name = "recentRunsLimit", required = false) Integer recentRunsLimit,
	                                                        @RequestParam(name = "recentTriggerEventsLimit", required = false) Integer recentTriggerEventsLimit,
	                                                        @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh) {
		int effectiveRecentRunLimit = clampLimit(recentRunsLimit, DEFAULT_RECENT_RUN_LIMIT, MAX_RECENT_RUN_LIMIT);
		int effectiveTriggerEventLimit = clampLimit(recentTriggerEventsLimit, DEFAULT_TRIGGER_EVENT_LIMIT, MAX_TRIGGER_EVENT_LIMIT);
		return jobBundleReadModelService.findBundle(jobKey)
				.map(job -> ResponseEntity.ok(new JobBundleDetailResponse(
						job,
						refresh
								? runSummaryReadModelService.latestRunsForJobFresh(job.jobKey(), job.displayName(), effectiveRecentRunLimit)
								: runSummaryReadModelService.latestRunsForJob(job.jobKey(), job.displayName(), effectiveRecentRunLimit),
						triggerEventRegistry.listByJobKey(job.jobKey(), effectiveTriggerEventLimit)
				)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{jobKey}/config")
	public ResponseEntity<JobBundleConfigResponse> jobConfig(@PathVariable String jobKey) {
		return jobBundleReadModelService.findBundleConfig(jobKey)
				.map(config -> ResponseEntity.ok(new JobBundleConfigResponse(
						config.jobKey(),
						config.displayName(),
						config.jobConfigPath(),
						config.rawYaml(),
						config.sourceConfigPath(),
						config.sourceRawYaml(),
						config.targetConfigPath(),
						config.targetRawYaml(),
						config.processorConfigPath(),
						config.processorRawYaml()
				)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{jobKey}/trigger-events")
	public ResponseEntity<TriggerEventListResponse> jobTriggerEvents(@PathVariable String jobKey,
	                                                                @RequestParam(name = "limit", required = false) Integer limit,
	                                                                @RequestParam(name = "size", required = false) Integer size,
	                                                                @RequestParam(name = "page", required = false) Integer page) {
		if (jobBundleReadModelService.findBundle(jobKey).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		int effectivePage = clampPage(page);
		int effectiveSize = clampLimit(size == null ? limit : size, DEFAULT_TRIGGER_EVENT_LIMIT, MAX_TRIGGER_EVENT_LIMIT);
		int offset = safeOffset(effectivePage, effectiveSize);
		var events = triggerEventRegistry.listByJobKey(jobKey, offset, effectiveSize);
		long totalItems = triggerEventRegistry.countByJobKey(jobKey);
		return ResponseEntity.ok(new TriggerEventListResponse(events, effectivePage, effectiveSize, totalItems));
	}

	@PostMapping("/{jobKey}:trigger-now")
	public ResponseEntity<TriggerNowDecisionResponse> triggerNow(@PathVariable String jobKey,
	                                                             @RequestBody(required = false) TriggerNowRequest request) {
		String reason = request == null || request.reason() == null || request.reason().isBlank()
				? "manual_operator_request"
				: request.reason().trim();
		String requestedBy = request == null || request.requestedBy() == null || request.requestedBy().isBlank()
				? "operator"
				: request.requestedBy().trim();
		log.info("CONTROLPLANE_TRIGGER event=trigger_now_requested scope=JOB jobKey={} reason={} requestedBy={}",
				jobKey, reason, requestedBy);

		if (jobBundleReadModelService.findBundle(jobKey).isEmpty()) {
			log.warn("CONTROLPLANE_TRIGGER event=trigger_now_rejected scope=JOB decisionStatus=NOT_FOUND jobKey={} reason={} requestedBy={} message=unknown_job_key",
					jobKey, reason, requestedBy);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new TriggerNowDecisionResponse(
					jobKey,
					"NOT_FOUND",
					"Unknown jobKey. Register the bundle before trigger-now.",
					null
			));
		}
		Instant now = Instant.now(clock);

		var recentDuplicate = triggerEventRegistry.listByJobKey(jobKey, RECENT_TRIGGER_SCAN_LIMIT).stream()
				.filter(event -> "ACCEPTED".equalsIgnoreCase(event.decisionStatus()))
				.filter(event -> reason.equals(event.reason()))
				.filter(event -> requestedBy.equals(event.requestedBy()))
				.filter(event -> event.requestedAt() != null)
				.filter(event -> Duration.between(event.requestedAt(), now).compareTo(MANUAL_TRIGGER_DUPLICATE_SUPPRESSION_WINDOW) < 0)
				.findFirst();
		if (recentDuplicate.isPresent()) {
			var duplicate = recentDuplicate.get();
			String message = "Duplicate trigger request suppressed because a recent accepted manual trigger already exists for this job and operator.";
			log.info("CONTROLPLANE_TRIGGER event=trigger_now_duplicate_suppressed scope=JOB jobKey={} reason={} requestedBy={} triggerEventId={}",
					jobKey, reason, requestedBy, duplicate.triggerEventId());
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TriggerNowDecisionResponse(
					jobKey,
					"DUPLICATE_SUPPRESSED",
					message,
					duplicate.triggerEventId()
			));
		}

		String message = "Trigger request accepted for reason='" + reason + "' requestedBy='" + requestedBy + "'.";
		var triggerEvent = triggerEventRegistry.recordAccepted(jobKey, reason, requestedBy, message);
		SelectedJobLaunchService.LaunchResult launchResult = selectedJobLaunchService.launchSelectedJob(
				jobKey,
				"MANUAL",
				null,
				triggerEvent.triggerEventId());
		log.info("CONTROLPLANE_TRIGGER event=trigger_now_accepted scope=JOB jobKey={} reason={} requestedBy={} triggerEventId={} launchStarted={} launchMessage={}",
				jobKey,
				reason,
				requestedBy,
				triggerEvent.triggerEventId(),
				launchResult.started(),
				launchResult.message());
		String responseMessage = message + " " + launchResult.message();
		String decisionStatus = launchResult.started() ? "ACCEPTED" : "LAUNCH_SKIPPED";
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TriggerNowDecisionResponse(
				jobKey,
				decisionStatus,
				responseMessage,
				triggerEvent.triggerEventId()
		));
	}

	private int clampLimit(Integer requestedLimit, int defaultLimit, int maxLimit) {
		if (requestedLimit == null) {
			return defaultLimit;
		}
		return Math.max(1, Math.min(requestedLimit, maxLimit));
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

