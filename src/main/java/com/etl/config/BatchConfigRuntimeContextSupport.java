package com.etl.config;

import com.etl.common.util.ResolvedModelMetadata;
import com.etl.runtime.job.JobHierarchyLoggingSupport;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.runtime.job.JobStepDescriptor;
import com.etl.runtime.job.JobStepModelDescriptor;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.lang.NonNull;

/**
 * Bridge seam for runtime-context projection helpers extracted from BatchConfig.
 *
 * <p>This helper keeps metadata/listener behavior stable while reducing orchestration weight.</p>
 */
final class BatchConfigRuntimeContextSupport {

    ResolvedModelMetadata toResolvedModelMetadata(JobStepModelDescriptor modelDescriptor) {
        return new ResolvedModelMetadata(
                modelDescriptor.sourceClassName(),
                modelDescriptor.targetProcessingClassName(),
                modelDescriptor.targetWriteClassName(),
                modelDescriptor.wrapperRequired(),
                modelDescriptor.wrapperFieldName()
        );
    }

    StepExecutionListener jobHierarchyContextListener(JobRuntimeDescriptor jobRuntimeDescriptor, JobStepDescriptor jobStep) {
        if (jobRuntimeDescriptor == null || jobStep == null) {
            return null;
        }
        return new StepExecutionListener() {
            @Override
            public void beforeStep(@NonNull StepExecution stepExecution) {
                JobHierarchyLoggingSupport.populateStepExecutionContext(stepExecution.getExecutionContext(), jobRuntimeDescriptor, jobStep);
            }

            @Override
            public org.springframework.batch.core.ExitStatus afterStep(@NonNull StepExecution stepExecution) {
                return stepExecution.getExitStatus();
            }
        };
    }
}

