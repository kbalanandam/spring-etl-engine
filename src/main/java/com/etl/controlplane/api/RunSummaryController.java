package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.monitoring.RunSummaryView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs")
public class RunSummaryController {

	private static final int DEFAULT_LIMIT = 25;
	private static final int MAX_LIMIT = 200;

	private final RunSummaryReadModelService runSummaryReadModelService;

	public RunSummaryController(RunSummaryReadModelService runSummaryReadModelService) {
		this.runSummaryReadModelService = runSummaryReadModelService;
	}

	@GetMapping
	public RunSummaryListResponse latestRuns(@RequestParam(name = "limit", required = false) Integer limit) {
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
		return new RunSummaryListResponse(runSummaryReadModelService.latestRuns(effectiveLimit));
	}

	@GetMapping("/{jobExecutionId}")
	public ResponseEntity<RunSummaryView> runByJobExecutionId(@PathVariable long jobExecutionId) {
		return runSummaryReadModelService.findRunByJobExecutionId(jobExecutionId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}


