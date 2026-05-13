package com.etl.runtime.job;

import com.etl.config.ColumnConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.target.CsvTargetConfig;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobHierarchyLoggingSupportTest {

    @Test
    void populateStepExecutionContextProjectsHierarchyMetadataIntoStepContext() {
        JobRuntimeDescriptor descriptor = ordersDescriptor();
        JobStepDescriptor publishOrders = descriptor.stepsByName().get("publish-orders");
        ExecutionContext executionContext = new ExecutionContext();

        JobHierarchyLoggingSupport.populateStepExecutionContext(executionContext, descriptor, publishOrders);

        assertEquals("publish-orders-subflow", executionContext.getString(JobHierarchyLoggingSupport.STEP_SUB_FLOW_NAME_KEY));
        assertEquals(1, executionContext.getInt(JobHierarchyLoggingSupport.STEP_SUB_FLOW_ORDER_KEY));
        assertEquals("normalize-orders-subflow", executionContext.getString(JobHierarchyLoggingSupport.STEP_DEPENDS_ON_SUB_FLOWS_KEY));
        assertEquals("OrdersValidated", executionContext.getString(JobHierarchyLoggingSupport.STEP_CONSUMES_HANDOFFS_KEY));
        assertEquals("OrdersFinal", executionContext.getString(JobHierarchyLoggingSupport.STEP_PRODUCES_HANDOFFS_KEY));
        assertEquals("normalize-orders", executionContext.getString(JobHierarchyLoggingSupport.STEP_UPSTREAM_STEPS_KEY));
        assertEquals("DATA_HANDOFF", executionContext.getString(JobHierarchyLoggingSupport.STEP_LINK_TYPES_KEY));
        assertTrue(executionContext.getString(JobHierarchyLoggingSupport.STEP_LINK_CONTROL_SUMMARY_KEY)
                .contains("requiredUpstreamStatuses=[COMPLETED]"));
        assertTrue(executionContext.getString(JobHierarchyLoggingSupport.STEP_SUMMARY_KEY)
                .contains("publish-orders"));
    }

    @Test
    void evaluateSubFlowEvidenceMarksDownstreamReadyWhenDependenciesAreSatisfied() {
        JobRuntimeDescriptor descriptor = ordersDescriptor();
        StepExecution completedStep = stepExecution("normalize-orders", BatchStatus.COMPLETED);

        List<JobHierarchyLoggingSupport.SubFlowStatusEvidence> evidence = JobHierarchyLoggingSupport.evaluateSubFlowEvidence(
                descriptor,
                List.of(completedStep),
                BatchStatus.STARTED);

        assertEquals(2, evidence.size());
        assertEquals(JobSubFlowExecutionStatus.COMPLETED, evidence.get(0).observedStatus());
        assertEquals(JobSubFlowExecutionStatus.READY, evidence.get(1).observedStatus());
        assertEquals("", evidence.get(1).blockedReason());
    }

    @Test
    void evaluateSubFlowEvidenceIncludesBlockedReasonWithHandoffAliasWhenUpstreamFails() {
        JobRuntimeDescriptor descriptor = ordersDescriptor();
        StepExecution failedStep = stepExecution("normalize-orders", BatchStatus.FAILED);

        List<JobHierarchyLoggingSupport.SubFlowStatusEvidence> evidence = JobHierarchyLoggingSupport.evaluateSubFlowEvidence(
                descriptor,
                List.of(failedStep),
                BatchStatus.FAILED);

        assertEquals(JobSubFlowExecutionStatus.FAILED, evidence.get(0).observedStatus());
        assertEquals(JobSubFlowExecutionStatus.BLOCKED, evidence.get(1).observedStatus());
        assertTrue(evidence.get(1).blockedReason().contains("upstreamSubFlow=normalize-orders-subflow"));
        assertTrue(evidence.get(1).blockedReason().contains("upstreamLink=normalize-orders->publish-orders"));
        assertTrue(evidence.get(1).blockedReason().contains("handoffAliases=OrdersValidated"));
    }

    private JobRuntimeDescriptor ordersDescriptor() {
        JobStepDescriptor normalizeOrders = stepDescriptor(
                "normalize-orders",
                0,
                "OrdersIn",
                "OrdersValidated",
                JobStepInputDescriptor.fromConfiguredSource("OrdersIn"),
                new JobStepOutputDescriptor(JobStepOutputType.INTERMEDIATE_DATASET, "OrdersValidated", "OrdersValidated", false, null, null)
        );
        JobStepDescriptor publishOrders = stepDescriptor(
                "publish-orders",
                1,
                "OrdersValidated",
                "OrdersFinal",
                new JobStepInputDescriptor(JobStepInputType.UPSTREAM_STEP_OUTPUT, null, "normalize-orders", "OrdersValidated", null, null),
                JobStepOutputDescriptor.configuredTarget("OrdersFinal", true)
        );

        JobSubFlowDescriptor normalizeSubFlow = new JobSubFlowDescriptor(
                "normalize-orders-subflow",
                0,
                List.of("normalize-orders"),
                JobSubFlowExecutionStatus.READY,
                JobSubFlowControlDescriptor.defaultSequentialControl(false),
                List.of(),
                List.of(),
                List.of("OrdersValidated"),
                null
        );
        JobSubFlowDescriptor publishSubFlow = new JobSubFlowDescriptor(
                "publish-orders-subflow",
                1,
                List.of("publish-orders"),
                JobSubFlowExecutionStatus.NOT_STARTED,
                JobSubFlowControlDescriptor.defaultSequentialControl(true),
                List.of("normalize-orders-subflow"),
                List.of("OrdersValidated"),
                List.of("OrdersFinal"),
                null
        );
        JobStepLinkDescriptor publishLink = new JobStepLinkDescriptor(
                "normalize-orders",
                "publish-orders",
                JobStepLinkType.DATA_HANDOFF,
                "OrdersValidated",
                "OrdersValidated",
                JobStepLinkControlDescriptor.defaultSequentialControl(true),
                null
        );

        return new JobRuntimeDescriptor(
                "orders-flow",
                null,
                "orders-flow",
                "default-subflow",
                false,
                JobRecoveryPolicy.RERUN_FROM_START,
                List.of(normalizeSubFlow, publishSubFlow),
                null,
                null,
                "C:/scenarios/orders/job-config.yaml",
                JobRunMode.EXPLICIT_JOB,
                new JobConfigPaths("source-config.yaml", "target-config.yaml", "processor-config.yaml"),
                List.of(normalizeOrders, publishOrders),
                null,
                List.of(publishLink),
                new JobValidationSummary(true, true, true, true, List.of(), List.of(), null)
        );
    }

    private JobStepDescriptor stepDescriptor(String stepName,
                                             int stepOrder,
                                             String sourceName,
                                             String targetName,
                                             JobStepInputDescriptor input,
                                             JobStepOutputDescriptor output) {
        return new JobStepDescriptor(
                stepName,
                stepOrder,
                sourceName,
                targetName,
                "default",
                null,
                stepName + " summary",
                input,
                output,
                csvSource(sourceName, "input/" + sourceName + ".csv"),
                csvTarget(targetName, "target/" + targetName + ".csv"),
                mapping(sourceName, targetName),
                new JobStepModelDescriptor(
                        "com.etl.model.source." + stepName,
                        "com.etl.model.target.processing." + stepName,
                        "com.etl.model.target.write." + stepName,
                        false,
                        "records",
                        JobModelResolutionMode.CONFIG_DRIVEN,
                        null
                ),
                new JobStepExecutionHints(JobStepExecutionMode.UNRESOLVED, false, null, false, false, false, false, null),
                new JobStepValidationSummary(true, true, true, true, List.of(), List.of(), null)
        );
    }

    private StepExecution stepExecution(String stepName, BatchStatus status) {
        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getStepName()).thenReturn(stepName);
        when(stepExecution.getStatus()).thenReturn(status);
        return stepExecution;
    }

    private CsvSourceConfig csvSource(String sourceName, String filePath) {
        return new CsvSourceConfig(sourceName, "com.etl.model.source", List.of(column("id")), filePath, ",");
    }

    private CsvTargetConfig csvTarget(String targetName, String filePath) {
        return new CsvTargetConfig(targetName, "com.etl.model.target", List.of(column("id")), filePath, ",");
    }

    private ColumnConfig column(String name) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType("String");
        return column;
    }

    private ProcessorConfig.EntityMapping mapping(String sourceName, String targetName) {
        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource(sourceName);
        mapping.setTarget(targetName);
        ProcessorConfig.FieldMapping field = new ProcessorConfig.FieldMapping();
        field.setFrom("id");
        field.setTo("id");
        mapping.setFields(List.of(field));
        return mapping;
    }
}

