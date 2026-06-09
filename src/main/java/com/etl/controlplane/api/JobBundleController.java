package com.etl.controlplane.api;

import com.etl.controlplane.jobs.JobBundleReadModelService;
import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.triggers.TriggerEventRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobBundleController {
	private static final int DEFAULT_TRIGGER_EVENT_LIMIT = 20;
	private static final int MAX_TRIGGER_EVENT_LIMIT = 200;
	private static final int DEFAULT_RECENT_RUN_LIMIT = 10;
	private static final int RECENT_TRIGGER_SCAN_LIMIT = 5;
	private static final Duration MANUAL_TRIGGER_DUPLICATE_SUPPRESSION_WINDOW = Duration.ofSeconds(5);

	private final JobBundleReadModelService jobBundleReadModelService;
	private final RunSummaryReadModelService runSummaryReadModelService;
	private final TriggerEventRegistry triggerEventRegistry;

	public JobBundleController(JobBundleReadModelService jobBundleReadModelService,
	                           RunSummaryReadModelService runSummaryReadModelService,
	                           TriggerEventRegistry triggerEventRegistry) {
		this.jobBundleReadModelService = jobBundleReadModelService;
		this.runSummaryReadModelService = runSummaryReadModelService;
		this.triggerEventRegistry = triggerEventRegistry;
	}

	@GetMapping
	public JobBundleListResponse listJobs() {
		var bundles = jobBundleReadModelService.listBundles();
		return new JobBundleListResponse(bundles, 0, bundles.size(), bundles.size());
	}

	@GetMapping("/{jobKey}")
	public ResponseEntity<JobBundleDetailResponse> jobDetail(@PathVariable String jobKey) {
		return jobBundleReadModelService.findBundle(jobKey)
				.map(job -> ResponseEntity.ok(new JobBundleDetailResponse(
						job,
						runSummaryReadModelService.latestRunsForJob(job.jobKey(), job.displayName(), DEFAULT_RECENT_RUN_LIMIT),
						triggerEventRegistry.listByJobKey(job.jobKey(), DEFAULT_TRIGGER_EVENT_LIMIT)
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
	                                                                @RequestParam(name = "limit", required = false) Integer limit) {
		if (jobBundleReadModelService.findBundle(jobKey).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		int effectiveLimit = limit == null
				? DEFAULT_TRIGGER_EVENT_LIMIT
				: Math.max(1, Math.min(limit, MAX_TRIGGER_EVENT_LIMIT));
		var events = triggerEventRegistry.listByJobKey(jobKey, effectiveLimit);
		return ResponseEntity.ok(new TriggerEventListResponse(events, 0, effectiveLimit, events.size()));
	}

	@PostMapping("/{jobKey}:trigger-now")
	public ResponseEntity<TriggerNowDecisionResponse> triggerNow(@PathVariable String jobKey,
	                                                             @RequestBody(required = false) TriggerNowRequest request) {
		if (jobBundleReadModelService.findBundle(jobKey).isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new TriggerNowDecisionResponse(
					jobKey,
					"NOT_FOUND",
					"Unknown jobKey. Register the bundle before trigger-now.",
					null
			));
		}

		String reason = request == null || request.reason() == null || request.reason().isBlank()
				? "manual_operator_request"
				: request.reason().trim();
		String requestedBy = request == null || request.requestedBy() == null || request.requestedBy().isBlank()
				? "operator"
				: request.requestedBy().trim();

		var recentDuplicate = triggerEventRegistry.listByJobKey(jobKey, RECENT_TRIGGER_SCAN_LIMIT).stream()
				.filter(event -> "ACCEPTED".equalsIgnoreCase(event.decisionStatus()))
				.filter(event -> reason.equals(event.reason()))
				.filter(event -> requestedBy.equals(event.requestedBy()))
				.filter(event -> Duration.between(event.requestedAt(), Instant.now()).compareTo(MANUAL_TRIGGER_DUPLICATE_SUPPRESSION_WINDOW) < 0)
				.findFirst();
		if (recentDuplicate.isPresent()) {
			var duplicate = recentDuplicate.get();
			String message = "Duplicate trigger request suppressed because a recent accepted manual trigger already exists for this job and operator.";
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TriggerNowDecisionResponse(
					jobKey,
					"DUPLICATE_SUPPRESSED",
					message,
					duplicate.triggerEventId()
			));
		}

		String message = "Trigger request accepted as placeholder for reason='" + reason + "' requestedBy='" + requestedBy + "'.";
		var triggerEvent = triggerEventRegistry.recordAccepted(jobKey, reason, requestedBy, message);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TriggerNowDecisionResponse(
				jobKey,
				"ACCEPTED",
				message,
				triggerEvent.triggerEventId()
		));
	}
}





