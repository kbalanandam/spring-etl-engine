package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconciles log-derived artifact detail with persisted artifact projections.
 */
final class RunDetailArtifactReconciler {

    private final RunSummaryRegistry runSummaryRegistry;

    RunDetailArtifactReconciler(RunSummaryRegistry runSummaryRegistry) {
        this.runSummaryRegistry = runSummaryRegistry;
    }

    List<ArtifactRecordView> reconcile(long jobExecutionId,
                                       List<ArtifactRecordView> detailArtifacts,
                                       List<StepRecordView> mergedSteps) {
        List<ArtifactRecordView> detailItems = detailArtifacts == null ? List.of() : detailArtifacts;
        List<RunArtifactRecordView> persistedItems = runSummaryRegistry.listArtifactRecordsByJobExecutionId(jobExecutionId, 200);
        if (persistedItems == null || persistedItems.isEmpty()) {
            return detailItems;
        }

        Map<String, ArtifactRecordView> mergedByKey = new LinkedHashMap<>();
        Set<String> detailKeys = new HashSet<>();
        for (ArtifactRecordView artifact : detailItems) {
            String key = artifactKey(artifact.role(), artifact.pathOrUri());
            mergedByKey.put(key, artifact);
            detailKeys.add(key);
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
            if (!detailKeys.contains(key)) {
                mergedByKey.put(key, choosePreferredPersistedArtifact(existing, mapped));
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

    private ArtifactRecordView choosePreferredPersistedArtifact(ArtifactRecordView first,
                                                                ArtifactRecordView second) {
        int firstScore = artifactCompletenessScore(first);
        int secondScore = artifactCompletenessScore(second);
        ArtifactRecordView preferred = firstScore >= secondScore ? first : second;
        ArtifactRecordView fallback = preferred == first ? second : first;

        LocalDateTime preferredCreated = preferred.createdAt();
        LocalDateTime fallbackCreated = fallback.createdAt();
        if (fallbackCreated != null && (preferredCreated == null || fallbackCreated.isAfter(preferredCreated))) {
            preferred = fallback;
            fallback = preferred == first ? second : first;
        }

        return new ArtifactRecordView(
                preferText(preferred.artifactId(), fallback.artifactId()),
                preferText(preferred.role(), fallback.role()),
                preferText(preferred.label(), fallback.label()),
                preferText(preferred.pathOrUri(), fallback.pathOrUri()),
                preferDateTime(preferred.createdAt(), fallback.createdAt()),
                preferLong(preferred.recordCount(), fallback.recordCount()),
                preferText(preferred.stepName(), fallback.stepName())
        );
    }

    private int artifactCompletenessScore(ArtifactRecordView artifact) {
        int score = 0;
        if (!firstNonBlank(artifact.artifactId()).isBlank()) {
            score++;
        }
        if (!firstNonBlank(artifact.stepName()).isBlank()) {
            score++;
        }
        if (artifact.recordCount() != null) {
            score++;
        }
        if (artifact.createdAt() != null) {
            score++;
        }
        return score;
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

