package com.etl.step;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Executes one custom step handler implementation.
 */
public interface CustomStepHandler {

    RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception;
}

