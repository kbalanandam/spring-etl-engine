package com.etl.step;

import com.etl.config.job.JobConfig;
import org.springframework.batch.core.StepContribution;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves custom provider result codes to one runtime action.
 */
public final class CustomStepOutcomeMapper {

    public enum OutcomeAction {
        CONTINUE,
        STOP,
        FAIL
    }

    public OutcomeAction resolveAction(JobConfig.CustomStepConfig customConfig, StepContribution contribution) {
        if (customConfig == null || customConfig.getOnResult() == null || customConfig.getOnResult().isEmpty()) {
            return null;
        }

        String resultCode = contribution.getExitStatus() == null ? null : contribution.getExitStatus().getExitCode();
        if (resultCode == null || resultCode.isBlank()) {
            return null;
        }

        String configuredAction = resolveConfiguredAction(customConfig.getOnResult(), resultCode);
        if (configuredAction == null) {
            return null;
        }
        return OutcomeAction.valueOf(configuredAction.toUpperCase(Locale.ROOT));
    }

    private String resolveConfiguredAction(Map<String, String> onResult, String resultCode) {
        String direct = onResult.get(resultCode);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : onResult.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(resultCode)) {
                return entry.getValue();
            }
        }
        return null;
    }
}

