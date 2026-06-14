package com.etl.step;

import com.etl.config.job.JobConfig;
import org.springframework.batch.core.JobExecution;

/**
 * Optional callback invoked after a failed job for configured custom steps.
 */
@FunctionalInterface
public interface CustomStepFailureFinalizer {

    CustomStepFailureFinalizer NO_OP = (jobExecution, stepName, customConfig) -> {
        // Intentionally no-op for providers that do not need failure finalization.
    };

    void onFailure(JobExecution jobExecution, String stepName, JobConfig.CustomStepConfig customConfig) throws Exception;
}

