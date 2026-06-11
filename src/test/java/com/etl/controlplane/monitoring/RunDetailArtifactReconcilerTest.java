package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunDetailArtifactReconcilerTest {

    @Test
    void filtersRunLogArtifactsAndUnknownStepArtifacts() {
        StubRunSummaryRegistry registry = new StubRunSummaryRegistry();
        registry.stepRecords = List.of(
                new RunStepRecordView("sr-505-1", "rr-505", "customers-step", "COMPLETED",
                        LocalDateTime.parse("2026-05-27T10:00:01"),
                        LocalDateTime.parse("2026-05-27T10:00:02"),
                        1L, 3L, 3L, 0L, 0L, 0L, 0L),
                new RunStepRecordView("sr-505-2", "rr-505", "departments-step", "COMPLETED",
                        LocalDateTime.parse("2026-05-27T10:00:03"),
                        LocalDateTime.parse("2026-05-27T10:00:04"),
                        1L, 3L, 3L, 0L, 0L, 0L, 0L),
                new RunStepRecordView("sr-505-3", "rr-505", "nested-tag-validation-csv-step", "COMPLETED",
                        LocalDateTime.parse("2026-05-27T10:00:01"),
                        LocalDateTime.parse("2026-05-27T10:00:04"),
                        3L, 36616L, 36583L, 33L, 0L, 0L, 0L)
        );
        registry.artifactRecords = List.of(
                new RunArtifactRecordView("ar-log-505", "rr-505", null, "RUN_LOG",
                        "logs\\2026-05-27\\customer-load.log", LocalDateTime.parse("2026-05-27T10:00:05")),
                new RunArtifactRecordView("ar-foreign-505", "rr-505", "sr-505-3", "STEP_REJECT_OUTPUT",
                        "output/rejects/foreign.csv", LocalDateTime.parse("2026-05-27T10:00:04"))
        );
        RunDetailArtifactReconciler reconciler = new RunDetailArtifactReconciler(registry);

        List<StepRecordView> mergedSteps = List.of(
                new StepRecordView("customers-step", 1, "COMPLETED", 901L,
                        3L, 3L, 0L, 0L, 0L, 0L, null, null, null, null),
                new StepRecordView("departments-step", 2, "COMPLETED", 902L,
                        3L, 3L, 0L, 0L, 0L, 0L, null, null, null, null)
        );

        List<ArtifactRecordView> result = reconciler.reconcile(505L, List.of(), mergedSteps);

        assertEquals(0, result.size());
    }

    @Test
    void doesNotAddSecondRejectArtifactWhenDetailAlreadyContainsStepRejectEvidence() {
        StubRunSummaryRegistry registry = new StubRunSummaryRegistry();
        registry.stepRecords = List.of(
                new RunStepRecordView("sr-606-1", "rr-606", "nested-tag-validation-csv-step", "COMPLETED",
                        LocalDateTime.parse("2026-05-27T10:00:01"),
                        LocalDateTime.parse("2026-05-27T10:00:05"),
                        4L, 36616L, 36583L, 33L, 0L, 0L, 0L)
        );
        registry.artifactRecords = List.of(
                new RunArtifactRecordView("ar-reject-606", "rr-606", "sr-606-1", "STEP_REJECT_OUTPUT",
                        "", LocalDateTime.parse("2026-05-27T10:00:05")),
                new RunArtifactRecordView("ar-log-606", "rr-606", null, "RUN_LOG",
                        "logs\\2026-05-27\\customer-load.log", LocalDateTime.parse("2026-05-27T10:00:10"))
        );
        RunDetailArtifactReconciler reconciler = new RunDetailArtifactReconciler(registry);

        List<StepRecordView> mergedSteps = List.of(
                new StepRecordView("nested-tag-validation-csv-step", 1, "COMPLETED", 1001L,
                        36616L, 36583L, 33L, 0L, 0L, 33L, null, null, null, null)
        );
        List<ArtifactRecordView> detailArtifacts = List.of(
                new ArtifactRecordView("reject-1001", "reject-output", "Rejected records for nested-tag-validation-csv-step",
                        "output/rejects/tags.csv", LocalDateTime.parse("2026-05-27T10:00:05"), 33L,
                        "nested-tag-validation-csv-step")
        );

        List<ArtifactRecordView> result = reconciler.reconcile(606L, detailArtifacts, mergedSteps);

        assertEquals(1, result.size());
        assertEquals("reject-output", result.get(0).role());
        assertEquals(33L, result.get(0).recordCount());
        assertEquals("output/rejects/tags.csv", result.get(0).pathOrUri());
    }

    private static final class StubRunSummaryRegistry implements RunSummaryRegistry {
        private List<RunStepRecordView> stepRecords = List.of();
        private List<RunArtifactRecordView> artifactRecords = List.of();

        @Override
        public void upsert(RunSummaryView runSummary) {
        }

        @Override
        public List<RunSummaryView> latestRuns(int limit) {
            return List.of();
        }

        @Override
        public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
            return Optional.empty();
        }

        @Override
        public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
            return Optional.empty();
        }

        @Override
        public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
            return stepRecords;
        }

        @Override
        public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
            return artifactRecords;
        }

        @Override
        public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
            return List.of();
        }
    }
}

