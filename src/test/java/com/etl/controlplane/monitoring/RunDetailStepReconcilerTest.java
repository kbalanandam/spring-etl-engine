package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunDetailStepReconcilerTest {

    @Test
    void returnsDetailStepsWhenPersistedStepRecordsAreAbsent() {
        StubRunSummaryRegistry registry = new StubRunSummaryRegistry();
        RunDetailStepReconciler reconciler = new RunDetailStepReconciler(registry);

        List<StepRecordView> detailSteps = List.of(
                new StepRecordView("normalize-orders", 1, "FAILED", 801L,
                        10L, 8L, 0L, 0L, 0L, 2L,
                        LocalDateTime.parse("2026-05-27T10:00:01"),
                        LocalDateTime.parse("2026-05-27T10:00:05"),
                        "normalize-orders-subflow", "Normalize orders")
        );

        List<StepRecordView> result = reconciler.reconcile(404L, detailSteps);

        assertEquals(1, result.size());
        assertEquals("normalize-orders", result.get(0).stepName());
        assertEquals(2L, result.get(0).rejectedCount());
    }

    @Test
    void mergesPersistedStepFieldsButRetainsDetailRejectedCountWhenPersistedValueIsNull() {
        StubRunSummaryRegistry registry = new StubRunSummaryRegistry();
        registry.stepRecords = List.of(
                new RunStepRecordView("sr-404-1", "rr-404", "normalize-orders", "COMPLETED",
                        LocalDateTime.parse("2026-05-27T10:00:01"),
                        LocalDateTime.parse("2026-05-27T10:00:05"),
                        4L, 12L, 12L, 0L, 0L, 0L, null)
        );
        RunDetailStepReconciler reconciler = new RunDetailStepReconciler(registry);

        List<StepRecordView> detailSteps = List.of(
                new StepRecordView("normalize-orders", 1, "FAILED", 801L,
                        10L, 8L, 0L, 0L, 0L, 2L,
                        LocalDateTime.parse("2026-05-27T10:00:01"),
                        LocalDateTime.parse("2026-05-27T10:00:05"),
                        "normalize-orders-subflow", "Normalize orders")
        );

        List<StepRecordView> result = reconciler.reconcile(404L, detailSteps);

        assertEquals(1, result.size());
        assertEquals("COMPLETED", result.get(0).status());
        assertEquals(12L, result.get(0).readCount());
        assertEquals(2L, result.get(0).rejectedCount());
    }

    @Test
    void returnsPersistedStepsWhenDetailStepsAreEmpty() {
        StubRunSummaryRegistry registry = new StubRunSummaryRegistry();
        registry.stepRecords = List.of(
                new RunStepRecordView("sr-505-1", "rr-505", "customers-step", "COMPLETED",
                        LocalDateTime.parse("2026-05-27T10:00:01"),
                        LocalDateTime.parse("2026-05-27T10:00:02"),
                        1L, 3L, 3L, 0L, 0L, 0L, 0L),
                new RunStepRecordView("sr-505-2", "rr-505", "departments-step", "COMPLETED",
                        LocalDateTime.parse("2026-05-27T10:00:03"),
                        LocalDateTime.parse("2026-05-27T10:00:04"),
                        1L, 3L, 3L, 0L, 0L, 0L, 0L)
        );
        RunDetailStepReconciler reconciler = new RunDetailStepReconciler(registry);

        List<StepRecordView> result = reconciler.reconcile(505L, List.of());

        assertEquals(2, result.size());
        assertEquals("customers-step", result.get(0).stepName());
        assertEquals(1, result.get(0).sequence());
        assertEquals("departments-step", result.get(1).stepName());
        assertEquals(2, result.get(1).sequence());
    }

    private static final class StubRunSummaryRegistry implements RunSummaryRegistry {
        private List<RunStepRecordView> stepRecords = List.of();

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
            return List.of();
        }

        @Override
        public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
            return List.of();
        }
    }
}

