package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunDetailReadModelService;
import com.etl.controlplane.monitoring.RunDetailView;
import com.etl.controlplane.monitoring.RunSummaryRegistry;
import com.etl.controlplane.monitoring.RunScopedLogReadModelService;
import com.etl.controlplane.monitoring.RunScopedLogView;
import com.etl.controlplane.monitoring.RunSummaryReadModelService;
import com.etl.controlplane.monitoring.RunSummaryView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v1/runs")
public class RunSummaryController {

	private static final int DEFAULT_LIMIT = 25;
	private static final int MAX_LIMIT = 200;

	private final RunSummaryReadModelService runSummaryReadModelService;
	private final RunSummaryRegistry runSummaryRegistry;
	private final RunDetailReadModelService runDetailReadModelService;
	private final RunScopedLogReadModelService runScopedLogReadModelService;

	public RunSummaryController(RunSummaryReadModelService runSummaryReadModelService,
	                            RunSummaryRegistry runSummaryRegistry,
	                            RunDetailReadModelService runDetailReadModelService,
	                            RunScopedLogReadModelService runScopedLogReadModelService) {
		this.runSummaryReadModelService = runSummaryReadModelService;
		this.runSummaryRegistry = runSummaryRegistry;
		this.runDetailReadModelService = runDetailReadModelService;
		this.runScopedLogReadModelService = runScopedLogReadModelService;
	}

	@GetMapping
	public RunSummaryListResponse latestRuns(@RequestParam(name = "limit", required = false) Integer limit,
	                                        @RequestParam(name = "job", required = false) String job,
	                                        @RequestParam(name = "startDate", required = false) String startDate,
	                                        @RequestParam(name = "timezone", required = false) String timezone) {
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
		LocalDate effectiveStartDate = parseStartDate(startDate);
		ZoneId effectiveZoneId = parseTimezone(timezone);
		var runs = runSummaryReadModelService.latestRunsFiltered(effectiveLimit, job, effectiveStartDate, effectiveZoneId);
		return new RunSummaryListResponse(runs, 0, effectiveLimit, runs.size());
	}

	private LocalDate parseStartDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.trim());
		} catch (RuntimeException ex) {
			throw new ResponseStatusException(BAD_REQUEST, "Invalid startDate. Expected yyyy-MM-dd.");
		}
	}

	private ZoneId parseTimezone(String value) {
		if (value == null || value.isBlank()) {
			return ZoneId.systemDefault();
		}
		try {
			return ZoneId.of(value.trim());
		} catch (RuntimeException ex) {
			throw new ResponseStatusException(BAD_REQUEST, "Invalid timezone. Expected IANA zone id.");
		}
	}

	@GetMapping("/{jobExecutionId}")
	public ResponseEntity<RunSummaryView> runByJobExecutionId(@PathVariable long jobExecutionId) {
		return runSummaryReadModelService.findRunByJobExecutionId(jobExecutionId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{jobExecutionId}/step-records")
	public ResponseEntity<RunStepRecordListResponse> stepRecordsByJobExecutionId(@PathVariable long jobExecutionId,
	                                                                            @RequestParam(name = "limit", required = false) Integer limit) {
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
		if (runSummaryReadModelService.findRunByJobExecutionId(jobExecutionId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		var records = runSummaryRegistry.listStepRecordsByJobExecutionId(jobExecutionId, effectiveLimit);
		return ResponseEntity.ok(new RunStepRecordListResponse(records, 0, effectiveLimit, records.size()));
	}

	@GetMapping("/{jobExecutionId}/artifact-records")
	public ResponseEntity<RunArtifactRecordListResponse> artifactRecordsByJobExecutionId(@PathVariable long jobExecutionId,
	                                                                                    @RequestParam(name = "limit", required = false) Integer limit) {
		int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
		if (runSummaryReadModelService.findRunByJobExecutionId(jobExecutionId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		var records = runSummaryRegistry.listArtifactRecordsByJobExecutionId(jobExecutionId, effectiveLimit);
		return ResponseEntity.ok(new RunArtifactRecordListResponse(records, 0, effectiveLimit, records.size()));
	}

	@GetMapping("/{jobExecutionId}/detail")
	public ResponseEntity<RunDetailView> runDetailByJobExecutionId(@PathVariable long jobExecutionId) {
		return runDetailReadModelService.findRunDetailByJobExecutionId(jobExecutionId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{jobExecutionId}/log")
	public ResponseEntity<RunScopedLogView> runScopedLogByJobExecutionId(@PathVariable long jobExecutionId,
	                                                                    @RequestParam(name = "limit", required = false) Integer limit) {
		return runScopedLogReadModelService.findRunScopedLogByJobExecutionId(jobExecutionId, limit)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}




