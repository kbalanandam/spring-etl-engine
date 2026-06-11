package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconciles log-derived step detail with persisted step projections.
 */
final class RunDetailStepReconciler {

    private final RunSummaryRegistry runSummaryRegistry;

    RunDetailStepReconciler(RunSummaryRegistry runSummaryRegistry) {
        this.runSummaryRegistry = runSummaryRegistry;
    }

    List<StepRecordView> reconcile(long jobExecutionId, List<StepRecordView> detailSteps) {
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
            persistedByName.merge(stepKey, persisted, this::choosePreferredPersistedRecord);
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
        for (RunStepRecordView persisted : persistedByName.values()) {
            merged.add(mapPersistedStepRecord(persisted, nextSequence++));
        }
        return merged;
    }

    private RunStepRecordView choosePreferredPersistedRecord(RunStepRecordView first, RunStepRecordView second) {
        int firstScore = persistedCompletenessScore(first);
        int secondScore = persistedCompletenessScore(second);
        if (secondScore > firstScore) {
            return second;
        }
        if (firstScore > secondScore) {
            return first;
        }

        LocalDateTime firstFinished = first.finishedAt();
        LocalDateTime secondFinished = second.finishedAt();
        if (secondFinished != null && (firstFinished == null || secondFinished.isAfter(firstFinished))) {
            return second;
        }
        if (firstFinished != null && (secondFinished == null || firstFinished.isAfter(secondFinished))) {
            return first;
        }

        String firstRecordId = firstNonBlank(first.stepRecordId());
        String secondRecordId = firstNonBlank(second.stepRecordId());
        if (!secondRecordId.isBlank() && (firstRecordId.isBlank() || secondRecordId.compareTo(firstRecordId) < 0)) {
            return second;
        }
        return first;
    }

    private int persistedCompletenessScore(RunStepRecordView record) {
        int score = 0;
        if (!firstNonBlank(record.stepStatus()).isBlank()) {
            score++;
        }
        if (record.readCount() != null) {
            score++;
        }
        if (record.writeCount() != null) {
            score++;
        }
        if (record.filterCount() != null) {
            score++;
        }
        if (record.skipCount() != null) {
            score++;
        }
        if (record.rollbackCount() != null) {
            score++;
        }
        if (record.rejectedCount() != null) {
            score++;
        }
        if (record.startedAt() != null) {
            score++;
        }
        if (record.finishedAt() != null) {
            score++;
        }
        return score;
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

    private String normalizeStepName(String value) {
        return firstNonBlank(value).toLowerCase();
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
}

