package com.etl.step.impl;

import com.etl.config.job.JobConfig;
import com.etl.step.CustomStepBinding;
import com.etl.step.CustomStepHandler;
import com.etl.step.CustomStepProvider;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        String resultCode = resolveStringConfig(config, "resultCode");
        return (contribution, chunkContext) -> {
            if (!resultCode.isBlank()) {
                contribution.setExitStatus(new ExitStatus(resultCode));
            }
            return RepeatStatus.FINISHED;
        };
    }

    private String resolveStringConfig(JobConfig.CustomStepConfig config, String key) {
        if (config == null || config.getConfig() == null) {
            return "";
        }
        Map<String, Object> values = config.getConfig();
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
