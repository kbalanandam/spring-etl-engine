package com.etl.config;

import com.etl.common.util.ResolvedModelMetadata;
import com.etl.runtime.job.JobModelResolutionMode;
import com.etl.runtime.job.JobStepModelDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepExecutionListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchConfigRuntimeContextSupportTest {

    private final BatchConfigRuntimeContextSupport support = new BatchConfigRuntimeContextSupport();

    @Test
    void toResolvedModelMetadataProjectsDescriptorFields() {
        JobStepModelDescriptor descriptor = new JobStepModelDescriptor(
                "com.etl.generated.source.Customer",
                "com.etl.generated.target.CustomerProcessing",
                "com.etl.generated.target.CustomerWrite",
                true,
                "records",
                JobModelResolutionMode.SCENARIO_GENERATED,
                null
        );

        ResolvedModelMetadata metadata = support.toResolvedModelMetadata(descriptor);

        assertEquals("com.etl.generated.source.Customer", metadata.getSourceClassName());
        assertEquals("com.etl.generated.target.CustomerProcessing", metadata.getTargetProcessingClassName());
        assertEquals("com.etl.generated.target.CustomerWrite", metadata.getTargetWriteClassName());
        assertTrue(metadata.isWrapperRequired());
        assertEquals("records", metadata.getWrapperFieldName());
    }

    @Test
    @SuppressWarnings("ConstantValue")
    void jobHierarchyContextListenerReturnsNullWhenDescriptorOrStepIsMissing() {
        assertNull(support.jobHierarchyContextListener(null, null));
        assertNull(support.jobHierarchyContextListener(null, org.mockito.Mockito.mock(com.etl.runtime.job.JobStepDescriptor.class)));
        assertNull(support.jobHierarchyContextListener(org.mockito.Mockito.mock(com.etl.runtime.job.JobRuntimeDescriptor.class), null));
    }

    @Test
    void jobHierarchyContextListenerReturnsListenerWhenInputsExist() {
        StepExecutionListener listener = support.jobHierarchyContextListener(
                org.mockito.Mockito.mock(com.etl.runtime.job.JobRuntimeDescriptor.class),
                org.mockito.Mockito.mock(com.etl.runtime.job.JobStepDescriptor.class)
        );

        assertNotNull(listener);
    }
}



