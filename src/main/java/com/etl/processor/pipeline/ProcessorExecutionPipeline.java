package com.etl.processor.pipeline;

import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import org.springframework.batch.item.ItemProcessor;

/**
 * Orchestrates processor construction for one selected source-target step.
 *
 * <p>Transition status: BRIDGE.</p>
 */
public interface ProcessorExecutionPipeline {

    ItemProcessor<Object, Object> createProcessor(
            ProcessorConfig processorConfig,
            SourceConfig sourceConfig,
            TargetConfig targetConfig,
            ResolvedModelMetadata metadata
    ) throws ClassNotFoundException;
}

