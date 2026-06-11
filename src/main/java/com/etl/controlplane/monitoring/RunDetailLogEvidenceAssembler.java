package com.etl.controlplane.monitoring;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles run-detail evidence from structured scenario logs for one run instance.
 */
final class RunDetailLogEvidenceAssembler {

    private final StructuredLogEventParser parser;

    RunDetailLogEvidenceAssembler(StructuredLogEventParser parser) {
        this.parser = parser;
    }

    RunDetailLogEvidence assemble(Path logPath, long jobExecutionId) {
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

        try {
            List<StructuredScenarioLogScanner.ParsedScenarioLogLine> scanned = StructuredScenarioLogScanner.read(logPath, parser);
            for (StructuredScenarioLogScanner.ParsedScenarioLogLine scannedLine : scanned) {
                StructuredLogEvent event = scannedLine.event();
                if (event == null) {
                    continue;
                }
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
            // Best-effort read model: retain summary even when detailed evidence cannot be read.
        }

        return new RunDetailLogEvidence(
                stepsByKey.values().stream().map(StepAccumulator::toView).toList(),
                deduplicateArtifacts(artifacts),
                failureSummary,
                sawRunSummary,
                sawStepEvent,
                sawJobFailure,
                runSummaryLine,
                firstStepEventLine,
                jobFailureLine
        );
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

    record RunDetailLogEvidence(
            List<StepRecordView> steps,
            List<ArtifactRecordView> artifacts,
            FailureSummaryView failureSummary,
            boolean sawRunSummary,
            boolean sawStepEvent,
            boolean sawJobFailure,
            Integer runSummaryLine,
            Integer firstStepEventLine,
            Integer jobFailureLine
    ) {
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

