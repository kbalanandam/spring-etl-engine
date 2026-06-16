package com.etl.step;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Executes one custom step handler implementation.
 *
 * <p>Returning {@link RepeatStatus#FINISHED} completes the custom step. Throwing an exception
 * marks the step as failed and delegates failure handling to the job runtime policy.</p>
 */
public interface CustomStepHandler {

    /**
     * Executes custom step logic for the current Spring Batch step context.
     */
    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception;
}
