package com.etl.controlplane.api;

import com.etl.controlplane.jobs.JobBundleReadModelService;
import com.etl.controlplane.jobs.JobBundleSummaryView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobBundleController {

	private final JobBundleReadModelService jobBundleReadModelService;

	public JobBundleController(JobBundleReadModelService jobBundleReadModelService) {
		this.jobBundleReadModelService = jobBundleReadModelService;
	}

	@GetMapping
	public JobBundleListResponse listJobs() {
		var bundles = jobBundleReadModelService.listBundles();
		return new JobBundleListResponse(bundles, 0, bundles.size(), bundles.size());
	}

	@GetMapping("/{jobKey}")
	public ResponseEntity<JobBundleSummaryView> jobDetail(@PathVariable String jobKey) {
		return jobBundleReadModelService.findBundle(jobKey)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
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

		String triggerEventId = "te-placeholder-" + jobKey;
		String message = "Trigger request accepted as placeholder for reason='" + reason + "' requestedBy='" + requestedBy + "'.";
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TriggerNowDecisionResponse(
				jobKey,
				"ACCEPTED",
				message,
				triggerEventId
		));
	}
}



