package com.etl.step;

import com.etl.config.job.JobConfig;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Built-in no-op provider used to verify custom-step orchestration in preserved bundles.
 */
@Component
public class AuditNoopCustomStepProvider implements CustomStepProvider {

    @Override
    public String customType() {
        return "auditNoop";
    }

    @Override
    public CustomStepHandler createHandler(JobConfig.CustomStepConfig config) {
        return (contribution, chunkContext) -> RepeatStatus.FINISHED;
    }
}

