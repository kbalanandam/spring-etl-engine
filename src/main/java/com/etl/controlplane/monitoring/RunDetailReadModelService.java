package com.etl.controlplane.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds a richer run drill-down view from structured scenario log evidence.
 */
@Service
public class RunDetailReadModelService {

	private final RunSummaryReadModelService runSummaryReadModelService;
	private final RunSummaryRegistry runSummaryRegistry;
	private final StructuredLogEventParser parser;

	@Autowired
	public RunDetailReadModelService(RunSummaryReadModelService runSummaryReadModelService,
	                                 RunSummaryRegistry runSummaryRegistry) {
		this(runSummaryReadModelService, runSummaryRegistry, new StructuredLogEventParser());
	}

	RunDetailReadModelService(RunSummaryReadModelService runSummaryReadModelService,
	                         StructuredLogEventParser parser) {
		this(runSummaryReadModelService, new InMemoryRunSummaryRegistry(), parser);
	}

	RunDetailReadModelService(RunSummaryReadModelService runSummaryReadModelService,
	                         RunSummaryRegistry runSummaryRegistry,
	                         StructuredLogEventParser parser) {
		this.runSummaryReadModelService = runSummaryReadModelService;
		this.runSummaryRegistry = runSummaryRegistry;
		this.parser = parser;
	}

	public Optional<RunDetailView> findRunDetailByJobExecutionId(long jobExecutionId) {
		return runSummaryReadModelService.findRunByJobExecutionId(jobExecutionId)
				.map(summary -> buildDetail(summary, jobExecutionId));
	}

	private RunDetailView buildDetail(RunSummaryView runSummary, long jobExecutionId) {
		Path logPath = Path.of(runSummary.logPath());
		if (!Files.exists(logPath)) {
			List<StepRecordView> mergedSteps = reconcileWithPersistedStepRecords(jobExecutionId, List.of());
			List<ArtifactRecordView> mergedArtifacts = reconcileWithPersistedArtifactRecords(jobExecutionId, List.of(), mergedSteps);
			return new RunDetailView(
					runSummary,
					mergedSteps,
					mergedArtifacts,
					null,
					defaultEvidenceLinks(runSummary.logPath())
			);
		}

		Map<String, StepAccumulator> stepsByKey = new LinkedHashMap<>();
		List<ArtifactRecordView> artifacts = new ArrayList<>();
		FailureSummaryView failureSummary = null;
		boolean sawRunSummary = false;
		boolean sawStepEvent = false;
		boolean sawJobFailure = false;
		Integer runSummaryLine = null;
		Integer firstStepEventLine = null;
		Integer jobFailureLine = null;
		int[] nextSequence = {1};

		try (Stream<String> lines = Files.lines(logPath)) {
			List<String> allLines = lines.toList();
			for (int i = 0; i < allLines.size(); i++) {
				String line = allLines.get(i);
				Optional<StructuredLogEvent> maybeEvent = parser.parse(line, logPath, i + 1);
				if (maybeEvent.isEmpty()) {
					continue;
				}
				StructuredLogEvent event = maybeEvent.orElseThrow();
				if (event.jobExecutionId() == null || event.jobExecutionId() != jobExecutionId) {
					continue;
				}
				if ("STEP_EVENT".equals(event.recordType())) {
					sawStepEvent = true;
					if (firstStepEventLine == null) {
						firstStepEventLine = event.lineNumber();
					}
					StepAccumulator accumulator = stepAccumulator(stepsByKey, event, nextSequence[0]);
					if (accumulator.sequence == nextSequence[0]) {
						nextSequence[0]++;
					}
					mergeStepEvent(accumulator, event);
					artifacts.addAll(extractArtifacts(accumulator, event));
				} else if ("RUN_SUMMARY".equals(event.recordType())) {
					sawRunSummary = true;
					if (runSummaryLine == null) {
						runSummaryLine = event.lineNumber();
					}
				} else if (failureSummary == null && "JOB_FAILURE".equals(event.recordType()) && "job_failure".equalsIgnoreCase(event.event())) {
					sawJobFailure = true;
					if (jobFailureLine == null) {
						jobFailureLine = event.lineNumber();
					}
					failureSummary = new FailureSummaryView(
							field(event.fields(), "failureCategory"),
							field(event.fields(), "exceptionType"),
							field(event.fields(), "rootCause"),
							field(event.fields(), "message")
					);
				}
			}
		} catch (IOException ignored) {
			// Best-effort read model: retain the summary even if detailed evidence cannot be read.
		}

		List<StepRecordView> mergedSteps = reconcileWithPersistedStepRecords(
				jobExecutionId,
				stepsByKey.values().stream().map(StepAccumulator::toView).toList()
		);
		List<ArtifactRecordView> mergedArtifacts = reconcileWithPersistedArtifactRecords(
				jobExecutionId,
				deduplicateArtifacts(artifacts),
				mergedSteps
		);

		return new RunDetailView(
				runSummary,
				mergedSteps,
				mergedArtifacts,
				failureSummary,
				buildEvidenceLinks(runSummary.logPath(), sawRunSummary, sawStepEvent, sawJobFailure, runSummaryLine, firstStepEventLine, jobFailureLine)
		);
	}

	private List<StepRecordView> reconcileWithPersistedStepRecords(long jobExecutionId, List<StepRecordView> detailSteps) {
		List<StepRecordView> detailItems = detailSteps == null ? List.of() : detailSteps;
		List<RunStepRecordView> persistedItems = runSummaryRegistry.listStepRecordsByJobExecutionId(jobExecutionId, 200);
		if (persistedItems == null || persistedItems.isEmpty()) {
			return detailItems;
		}

		Map<String, RunStepRecordView> persistedByName = new LinkedHashMap<>();
		for (RunStepRecordView persisted : persistedItems) {
			String stepKey = normalizeStepName(persisted.stepName());
			if (stepKey.isBlank()) {
				continue;
			}
			persistedByName.putIfAbsent(stepKey, persisted);
		}

		List<StepRecordView> merged = new ArrayList<>();
		for (StepRecordView detail : detailItems) {
			RunStepRecordView persisted = persistedByName.remove(normalizeStepName(detail.stepName()));
			merged.add(persisted == null ? detail : mergeStepRecord(detail, persisted));
		}

		if (!detailItems.isEmpty()) {
			return merged;
		}

		int nextSequence = 1;
		for (RunStepRecordView persisted : persistedItems) {
			merged.add(mapPersistedStepRecord(persisted, nextSequence++));
		}
		return merged;
	}

	private List<ArtifactRecordView> reconcileWithPersistedArtifactRecords(long jobExecutionId,
	                                                                     List<ArtifactRecordView> detailArtifacts,
	                                                                     List<StepRecordView> mergedSteps) {
		List<ArtifactRecordView> detailItems = detailArtifacts == null ? List.of() : detailArtifacts;
		List<RunArtifactRecordView> persistedItems = runSummaryRegistry.listArtifactRecordsByJobExecutionId(jobExecutionId, 200);
		if (persistedItems == null || persistedItems.isEmpty()) {
			return detailItems;
		}

		Map<String, ArtifactRecordView> mergedByKey = new LinkedHashMap<>();
		for (ArtifactRecordView artifact : detailItems) {
			mergedByKey.put(artifactKey(artifact.role(), artifact.pathOrUri()), artifact);
		}

		Set<String> knownStepKeys = new HashSet<>();
		for (StepRecordView step : mergedSteps) {
			String stepKey = normalizeStepName(step.stepName());
			if (!stepKey.isBlank()) {
				knownStepKeys.add(stepKey);
			}
		}

		Set<String> detailRejectStepKeys = new HashSet<>();
		for (ArtifactRecordView artifact : detailItems) {
			if (!"reject-output".equalsIgnoreCase(firstNonBlank(artifact.role()))) {
				continue;
			}
			String stepKey = normalizeStepName(artifact.stepName());
			if (!stepKey.isBlank()) {
				detailRejectStepKeys.add(stepKey);
			}
		}

		Map<String, String> stepNamesByRecordId = persistedStepNameByRecordId(jobExecutionId);
		for (RunArtifactRecordView persisted : persistedItems) {
			if ("RUN_LOG".equals(normalizeRoleToken(persisted.artifactRole()))) {
				continue;
			}
			ArtifactRecordView mapped = mapPersistedArtifact(persisted, stepNamesByRecordId, mergedSteps);
			String mappedStepKey = normalizeStepName(mapped.stepName());
			if (!mappedStepKey.isBlank() && !knownStepKeys.isEmpty() && !knownStepKeys.contains(mappedStepKey)) {
				continue;
			}
			if ("reject-output".equalsIgnoreCase(mapped.role()) && !detailItems.isEmpty()) {
				if (mappedStepKey.isBlank() || detailRejectStepKeys.contains(mappedStepKey)) {
					continue;
				}
			}
			String key = artifactKey(mapped.role(), mapped.pathOrUri());
			ArtifactRecordView existing = mergedByKey.get(key);
			if (existing == null) {
				mergedByKey.put(key, mapped);
				continue;
			}
			mergedByKey.put(key, new ArtifactRecordView(
					existing.artifactId(),
					existing.role(),
					existing.label(),
					existing.pathOrUri(),
					preferDateTime(existing.createdAt(), mapped.createdAt()),
					preferLong(existing.recordCount(), mapped.recordCount()),
					preferText(existing.stepName(), mapped.stepName())
			));
		}

		return List.copyOf(mergedByKey.values());
	}

	private StepRecordView mergeStepRecord(StepRecordView detail, RunStepRecordView persisted) {
		return new StepRecordView(
				preferText(persisted.stepName(), detail.stepName()),
				detail.sequence(),
				preferText(persisted.stepStatus(), detail.status()),
				detail.stepExecutionId(),
				preferLong(persisted.readCount(), detail.readCount()),
				preferLong(persisted.writeCount(), detail.writeCount()),
				preferLong(persisted.filterCount(), detail.filterCount()),
				preferLong(persisted.skipCount(), detail.skipCount()),
				preferLong(persisted.rollbackCount(), detail.rollbackCount()),
				preferLong(persisted.rejectedCount(), detail.rejectedCount()),
				preferDateTime(persisted.startedAt(), detail.startedAt()),
				preferDateTime(persisted.finishedAt(), detail.finishedAt()),
				detail.subFlow(),
				detail.stepSummary()
		);
	}

	private StepRecordView mapPersistedStepRecord(RunStepRecordView persisted, int sequence) {
		return new StepRecordView(
				persisted.stepName(),
				sequence,
				blankToUnknown(persisted.stepStatus()),
				null,
				persisted.readCount(),
				persisted.writeCount(),
				persisted.filterCount(),
				persisted.skipCount(),
				persisted.rollbackCount(),
				persisted.rejectedCount(),
				persisted.startedAt(),
				persisted.finishedAt(),
				null,
				null
		);
	}

	private Map<String, String> persistedStepNameByRecordId(long jobExecutionId) {
		Map<String, String> map = new LinkedHashMap<>();
		List<RunStepRecordView> persistedSteps = runSummaryRegistry.listStepRecordsByJobExecutionId(jobExecutionId, 200);
		for (RunStepRecordView step : persistedSteps) {
			String recordId = step.stepRecordId();
			if (recordId == null || recordId.isBlank()) {
				continue;
			}
			map.put(recordId, step.stepName());
		}
		return map;
	}

	private ArtifactRecordView mapPersistedArtifact(RunArtifactRecordView persisted,
	                                              Map<String, String> stepNamesByRecordId,
	                                              List<StepRecordView> mergedSteps) {
		String roleToken = normalizeRoleToken(persisted.artifactRole());
		String stepName = resolveArtifactStepName(persisted.stepRecordId(), stepNamesByRecordId);
		String role;
		String label;
		if ("STEP_REJECT_OUTPUT".equals(roleToken)) {
			role = "reject-output";
			label = "Rejected records for " + (stepName.isBlank() ? "step" : stepName);
		} else if ("STEP_ARCHIVED_SOURCE".equals(roleToken)) {
			role = "archived-source";
			label = "Archived source for " + (stepName.isBlank() ? "step" : stepName);
		} else if ("RUN_LOG".equals(roleToken)) {
			role = "run-log";
			label = "Scenario log";
		} else {
			role = roleToken.toLowerCase().replace('_', '-');
			label = "Artifact " + role;
		}

		Long recordCount = null;
		if ("reject-output".equals(role) && !stepName.isBlank()) {
			recordCount = mergedSteps.stream()
					.filter(step -> stepName.equals(step.stepName()))
					.map(StepRecordView::rejectedCount)
					.filter(value -> value != null)
					.findFirst()
					.orElse(null);
		}

		String artifactId = persisted.artifactRecordId();
		if (artifactId == null || artifactId.isBlank()) {
			artifactId = role + "-" + System.identityHashCode(persisted);
		}

		return new ArtifactRecordView(
				artifactId,
				role,
				label,
				persisted.artifactPath(),
				persisted.createdAt(),
				recordCount,
				stepName.isBlank() ? null : stepName
		);
	}

	private String resolveArtifactStepName(String stepRecordId, Map<String, String> stepNamesByRecordId) {
		if (stepRecordId == null || stepRecordId.isBlank()) {
			return "";
		}
		return firstNonBlank(stepNamesByRecordId.get(stepRecordId));
	}

	private String normalizeRoleToken(String role) {
		String normalized = firstNonBlank(role);
		return normalized.isBlank() ? "UNKNOWN" : normalized.toUpperCase();
	}

	private String normalizeStepName(String value) {
		return firstNonBlank(value).toLowerCase();
	}

	private String artifactKey(String role, String path) {
		return firstNonBlank(role) + "|" + firstNonBlank(path);
	}

	private String blankToUnknown(String value) {
		String normalized = firstNonBlank(value);
		return normalized.isBlank() ? "UNKNOWN" : normalized;
	}

	private Long preferLong(Long preferred, Long fallback) {
		return preferred != null ? preferred : fallback;
	}

	private LocalDateTime preferDateTime(LocalDateTime preferred, LocalDateTime fallback) {
		return preferred != null ? preferred : fallback;
	}

	private String preferText(String preferred, String fallback) {
		String normalizedPreferred = firstNonBlank(preferred);
		if (!normalizedPreferred.isBlank()) {
			return normalizedPreferred;
		}
		return firstNonBlank(fallback);
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return "";
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return "";
	}

	private StepAccumulator stepAccumulator(Map<String, StepAccumulator> stepsByKey,
	                                      StructuredLogEvent event,
	                                      int nextSequence) {
		String key = stepKey(event);
		return stepsByKey.computeIfAbsent(key, ignored -> new StepAccumulator(
				nextSequence,
				field(event.fields(), "stepName", event.mdcStepName())
		));
	}

	private void mergeStepEvent(StepAccumulator accumulator, StructuredLogEvent event) {
		Map<String, String> fields = event.fields();
		accumulator.stepName = field(fields, "stepName", accumulator.stepName);
		accumulator.stepExecutionId = toLong(fields.get("stepExecutionId"));
		accumulator.subFlow = field(fields, "subFlow", accumulator.subFlow);
		accumulator.stepSummary = field(fields, "stepSummary", accumulator.stepSummary);
		if ("step_started".equalsIgnoreCase(event.event())) {
			accumulator.startedAt = firstNonNull(accumulator.startedAt, event.loggedAt());
			accumulator.status = accumulator.status == null || accumulator.status.isBlank() ? "STARTED" : accumulator.status;
		} else if ("step_finished".equalsIgnoreCase(event.event())) {
			accumulator.finishedAt = firstNonNull(accumulator.finishedAt, event.loggedAt());
			accumulator.status = field(fields, "status", accumulator.status);
			accumulator.readCount = toLong(fields.get("readCount"));
			accumulator.writeCount = toLong(fields.get("writeCount"));
			accumulator.filterCount = toLong(fields.get("filterCount"));
			accumulator.skipCount = toLong(fields.get("skipCount"));
			accumulator.rollbackCount = toLong(fields.get("rollbackCount"));
			accumulator.rejectedCount = toLong(fields.get("rejectedCount"));
		}
	}

	private List<ArtifactRecordView> extractArtifacts(StepAccumulator accumulator, StructuredLogEvent event) {
		if (!"step_finished".equalsIgnoreCase(event.event())) {
			return List.of();
		}
		List<ArtifactRecordView> artifacts = new ArrayList<>();
		String rejectOutputPath = field(event.fields(), "rejectOutputPath");
		String archivedSourcePath = field(event.fields(), "archivedSourcePath");
		if (!rejectOutputPath.isBlank()) {
			artifacts.add(new ArtifactRecordView(
					"reject-" + artifactSuffix(accumulator),
					"reject-output",
					"Rejected records for " + accumulator.stepName,
					rejectOutputPath,
					event.loggedAt(),
					accumulator.rejectedCount,
					accumulator.stepName
			));
		}
		if (!archivedSourcePath.isBlank()) {
			artifacts.add(new ArtifactRecordView(
					"archive-" + artifactSuffix(accumulator),
					"archived-source",
					"Archived source for " + accumulator.stepName,
					archivedSourcePath,
					event.loggedAt(),
					null,
					accumulator.stepName
			));
		}
		return artifacts;
	}

	private List<ArtifactRecordView> deduplicateArtifacts(List<ArtifactRecordView> artifacts) {
		Map<String, ArtifactRecordView> unique = new LinkedHashMap<>();
		for (ArtifactRecordView artifact : artifacts) {
			unique.putIfAbsent(artifact.role() + "|" + artifact.pathOrUri(), artifact);
		}
		return List.copyOf(unique.values());
	}

	private List<EvidenceLinkView> defaultEvidenceLinks(String logPath) {
		List<EvidenceLinkView> links = new ArrayList<>();
		links.add(new EvidenceLinkView("Scenario log", logPath, "log-file"));
		links.add(new EvidenceLinkView("Scenario log unavailable", logPath, "log-file-missing"));
		return List.copyOf(links);
	}

	private List<EvidenceLinkView> buildEvidenceLinks(String logPath,
	                                                boolean sawRunSummary,
	                                                boolean sawStepEvent,
	                                                boolean sawJobFailure,
	                                                Integer runSummaryLine,
	                                                Integer stepEventLine,
	                                                Integer jobFailureLine) {
		List<EvidenceLinkView> links = new ArrayList<>();
		links.add(new EvidenceLinkView("Scenario log", logPath, "log-file"));
		if (sawRunSummary) {
			links.add(new EvidenceLinkView("Run summary", withLineAnchor(logPath, runSummaryLine), "run-summary"));
		}
		if (sawStepEvent) {
			links.add(new EvidenceLinkView("Step events", withLineAnchor(logPath, stepEventLine), "step-event"));
		}
		if (sawJobFailure) {
			links.add(new EvidenceLinkView("Job failure", withLineAnchor(logPath, jobFailureLine), "job-failure"));
		}
		return List.copyOf(links);
	}

	private String withLineAnchor(String logPath, Integer lineNumber) {
		if (lineNumber == null || lineNumber <= 0) {
			return logPath;
		}
		return logPath + "#L" + lineNumber;
	}

	private String stepKey(StructuredLogEvent event) {
		Long stepExecutionId = toLong(event.fields().get("stepExecutionId"));
		if (stepExecutionId != null) {
			return "stepExecutionId:" + stepExecutionId;
		}
		String stepName = field(event.fields(), "stepName", event.mdcStepName());
		return "stepName:" + stepName;
	}

	private String artifactSuffix(StepAccumulator accumulator) {
		return accumulator.stepExecutionId == null ? String.valueOf(accumulator.sequence) : String.valueOf(accumulator.stepExecutionId);
	}

	private String field(Map<String, String> fields, String key) {
		return field(fields, key, "");
	}

	private String field(Map<String, String> fields, String key, String defaultValue) {
		String value = fields.get(key);
		return value == null || value.isBlank() ? defaultValue : value.trim();
	}

	private Long toLong(String value) {
		if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
			return null;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private LocalDateTime firstNonNull(LocalDateTime currentValue, LocalDateTime candidate) {
		return currentValue != null ? currentValue : candidate;
	}

	private static final class StepAccumulator {
		private final int sequence;
		private String stepName;
		private String status;
		private Long stepExecutionId;
		private Long readCount;
		private Long writeCount;
		private Long filterCount;
		private Long skipCount;
		private Long rollbackCount;
		private Long rejectedCount;
		private LocalDateTime startedAt;
		private LocalDateTime finishedAt;
		private String subFlow;
		private String stepSummary;

		private StepAccumulator(int sequence, String stepName) {
			this.sequence = sequence;
			this.stepName = stepName;
		}

		private StepRecordView toView() {
			return new StepRecordView(
					stepName,
					sequence,
					status == null || status.isBlank() ? "UNKNOWN" : status,
					stepExecutionId,
					readCount,
					writeCount,
					filterCount,
					skipCount,
					rollbackCount,
					rejectedCount,
					startedAt,
					finishedAt,
					subFlow,
					stepSummary
			);
		}
	}
}


