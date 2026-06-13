package com.etl.step;

import com.etl.config.job.JobConfig;

/**
 * Registers a custom step handler for one logical custom step type.
 *
 * <p>Type binding is declared by {@link CustomStepBinding} on the implementation class.
 * Providers should be lightweight and create per-step handlers from the supplied config.</p>
 */
public interface CustomStepProvider {

    /**
     * Builds the handler executed by Spring Batch for one custom step declaration.
     */
    CustomStepHandler createHandler(JobConfig.CustomStepConfig config);

    /**
     * Stable provider identity used in conflict/error reporting.
     */
    default String providerId() {
        return getClass().getName();
    }

    /**
     * Backward-compatible override metadata for pre-annotation wiring paths.
     */
    default boolean isOverride() {
        return false;
    }
}
