package com.etl.controlplane.api;

import com.etl.controlplane.jobs.JobBundleReadModelService;
import com.etl.controlplane.jobs.JobBundleSummaryView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
		return new JobBundleListResponse(jobBundleReadModelService.listBundles());
	}

	@GetMapping("/{jobKey}")
	public ResponseEntity<JobBundleSummaryView> jobDetail(@PathVariable String jobKey) {
		return jobBundleReadModelService.findBundle(jobKey)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}

