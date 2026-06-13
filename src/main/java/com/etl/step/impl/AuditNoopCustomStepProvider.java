package com.etl.step.impl;

import com.etl.config.job.JobConfig;
import com.etl.step.CustomStepBinding;
import com.etl.step.CustomStepHandler;
import com.etl.step.CustomStepProvider;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Built-in no-op provider used to verify custom-step orchestration in preserved bundles.
 *
 * <p>This provider intentionally performs no side effects and always finishes successfully,
 * making it safe as a baseline custom-step wiring example.</p>
 */
@Component
@CustomStepBinding(type = "auditNoop")
public class AuditNoopCustomStepProvider implements CustomStepProvider {


    /**
     * Returns a deterministic no-op handler used for orchestration and observability checks.
     */
    @Override
    public CustomStepHandler createHandler(JobConfig.CustomStepConfig config) {
        return (contribution, chunkContext) -> RepeatStatus.FINISHED;
    }
}
