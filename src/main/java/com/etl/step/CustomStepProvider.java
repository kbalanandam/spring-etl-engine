package com.etl.step;

import com.etl.config.job.JobConfig;

/**
 * Registers a custom step handler for one logical custom step type.
 */
public interface CustomStepProvider {

    String customType();

    CustomStepHandler createHandler(JobConfig.CustomStepConfig config);

    default String providerId() {
        return getClass().getName();
    }

    default boolean isOverride() {
        return false;
    }
}

