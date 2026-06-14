package com.etl.config;

import com.etl.config.batch.bridge.BatchStepModePlanner;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.runtime.job.JobRecoveryPolicy;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchStepModePlannerTest {

    @Test
    void planUsesTaskletWhenRecordCountIsWithinThreshold() throws Exception {
        BatchStepModePlanner planner = new BatchStepModePlanner(mock(Logger.class), runMetadata());

        BatchStepModePlanner.StepModePlan plan = planner.plan(
                "customers-step",
                source("Customers", 3),
                target("CustomersOut"),
                mapping("Customers", "CustomersOut"),
                null,
                null,
                null,
                10
        );

        assertFalse(plan.useChunk());
        assertEquals(3, plan.recordCount());
        assertNull(plan.duplicateRule());
    }

    @Test
    void planOverridesTaskletToChunkWhenSkipPolicyIsEnabled() throws Exception {
        BatchStepModePlanner planner = new BatchStepModePlanner(mock(Logger.class), runMetadata());

        BatchStepModePlanner.StepModePlan plan = planner.plan(
                "customers-step",
                source("Customers", 2),
                target("CustomersOut"),
                mapping("Customers", "CustomersOut"),
                skipPolicy(3, List.of("runtime"), List.of()),
                null,
                null,
                10
        );

        assertTrue(plan.useChunk());
    }

    @Test
    void planOverridesTaskletToChunkWhenRetryPolicyIsEnabled() throws Exception {
        BatchStepModePlanner planner = new BatchStepModePlanner(mock(Logger.class), runMetadata());

        BatchStepModePlanner.StepModePlan plan = planner.plan(
                "customers-step",
                source("Customers", 2),
                target("CustomersOut"),
                mapping("Customers", "CustomersOut"),
                null,
                retryPolicy(3, 25L, List.of("runtime"), List.of()),
                null,
                10
        );

        assertTrue(plan.useChunk());
    }

    @Test
    void planDefaultsToChunkWhenRecordCountIsUnknown() throws Exception {
        BatchStepModePlanner planner = new BatchStepModePlanner(mock(Logger.class), runMetadata());
        SourceConfig source = mock(SourceConfig.class);
        when(source.getSourceName()).thenReturn("Customers");
        when(source.getRecordCount()).thenReturn(-1);

        BatchStepModePlanner.StepModePlan plan = planner.plan(
                "customers-step",
                source,
                target("CustomersOut"),
                mapping("Customers", "CustomersOut"),
                null,
                null,
                null,
                10
        );

        assertTrue(plan.useChunk());
        assertEquals(11, plan.recordCount());
    }

    @Test
    void planHonorsConfiguredEmbeddedDbStorageModeForOrderedDuplicates() throws Exception {
        BatchStepModePlanner planner = new BatchStepModePlanner(mock(Logger.class), runMetadata());

        BatchStepModePlanner.StepModePlan plan = planner.plan(
                "customers-step",
                source("Customers", 3),
                target("CustomersOut"),
                mappingWithOrderedDuplicateRule("Customers", "CustomersOut", "embeddedDb"),
                null,
                null,
                null,
                10
        );

        assertNotNull(plan.duplicateRule());
        assertTrue(plan.useEmbeddedDbDuplicateResolver());
        assertEquals("embeddedDb", plan.orderedDuplicateResolverMode());
        assertEquals("configured_storage_mode_embeddedDb", plan.orderedDuplicateResolverReason());
    }

    @Test
    void planOverridesChunkToTaskletWhenOrderedDuplicateWinnerSelectionIsPresent() throws Exception {
        BatchStepModePlanner planner = new BatchStepModePlanner(mock(Logger.class), runMetadata());

        BatchStepModePlanner.StepModePlan plan = planner.plan(
                "customers-step",
                source("Customers", 100),
                target("CustomersOut"),
                mappingWithOrderedDuplicateRule("Customers", "CustomersOut", null),
                null,
                null,
                null,
                10
        );

        assertFalse(plan.useChunk());
        assertNotNull(plan.duplicateRule());
    }

    @Test
    @SuppressWarnings("RedundantThrows")
    void planFailsFastWhenOrderedDuplicateWinnerSelectionIsCombinedWithSkipPolicy() throws Exception {
        BatchStepModePlanner planner = new BatchStepModePlanner(mock(Logger.class), runMetadata());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> planner.plan(
                        "customers-step",
                        source("Customers", 100),
                        target("CustomersOut"),
                        mappingWithOrderedDuplicateRule("Customers", "CustomersOut", null),
                        skipPolicy(3, List.of("runtime"), List.of()),
                        null,
                        null,
                        10
                )
        );

        assertTrue(exception.getMessage().contains("both ordered duplicate winner selection and skipPolicy"));
    }

    private RunConfigurationMetadata runMetadata() {
        return new RunConfigurationMetadata(
                "customers",
                "job-config.yaml",
                false,
                "customers-main-flow",
                "default-subflow",
                JobRecoveryPolicy.RERUN_FROM_START,
                List.of()
        );
    }

    @SuppressWarnings("SameParameterValue")
    private SourceConfig source(String sourceName, int recordCount) throws Exception {
        SourceConfig source = mock(SourceConfig.class);
        when(source.getSourceName()).thenReturn(sourceName);
        when(source.getRecordCount()).thenReturn(recordCount);
        return source;
    }

    @SuppressWarnings("SameParameterValue")
    private TargetConfig target(String targetName) {
        TargetConfig target = mock(TargetConfig.class);
        when(target.getTargetName()).thenReturn(targetName);
        return target;
    }

    private ProcessorConfig.EntityMapping mapping(String source, String target) {
        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource(source);
        mapping.setTarget(target);
        return mapping;
    }

    @SuppressWarnings("SameParameterValue")
    private ProcessorConfig.EntityMapping mappingWithOrderedDuplicateRule(String source, String target, String storageMode) {
        ProcessorConfig.EntityMapping mapping = mapping(source, target);

        ProcessorConfig.FieldRule duplicateRule = new ProcessorConfig.FieldRule();
        duplicateRule.setType("duplicate");
        duplicateRule.setOrderBy(List.of(orderBy("eventTime", "DESC")));
        duplicateRule.setStorageMode(storageMode);

        ProcessorConfig.FieldMapping idField = new ProcessorConfig.FieldMapping();
        idField.setFrom("id");
        idField.setTo("id");
        idField.setRules(List.of(duplicateRule));

        ProcessorConfig.FieldMapping eventTimeField = new ProcessorConfig.FieldMapping();
        eventTimeField.setFrom("eventTime");
        eventTimeField.setTo("eventTime");

        mapping.setFields(List.of(idField, eventTimeField));
        return mapping;
    }

    @SuppressWarnings("SameParameterValue")
    private ProcessorConfig.OrderByField orderBy(String field, String direction) {
        ProcessorConfig.OrderByField orderByField = new ProcessorConfig.OrderByField();
        orderByField.setField(field);
        orderByField.setDirection(direction);
        return orderByField;
    }

    @SuppressWarnings("SameParameterValue")
    private JobConfig.SkipPolicyConfig skipPolicy(int skipLimit,
                                                  List<String> categories,
                                                  List<String> exceptions) {
        JobConfig.SkipPolicyConfig skipPolicy = new JobConfig.SkipPolicyConfig();
        skipPolicy.setEnabled(true);
        skipPolicy.setSkipLimit(skipLimit);
        skipPolicy.setSkippableCategories(categories);
        skipPolicy.setSkippableExceptions(exceptions);
        return skipPolicy;
    }

    @SuppressWarnings("SameParameterValue")
    private JobConfig.RetryPolicyConfig retryPolicy(int maxAttempts,
                                                    long backoffMs,
                                                    List<String> categories,
                                                    List<String> exceptions) {
        JobConfig.RetryPolicyConfig retryPolicy = new JobConfig.RetryPolicyConfig();
        retryPolicy.setEnabled(true);
        retryPolicy.setMaxAttempts(maxAttempts);
        retryPolicy.setBackoffMs(backoffMs);
        retryPolicy.setRetryableCategories(categories);
        retryPolicy.setRetryableExceptions(exceptions);
        return retryPolicy;
    }
}




