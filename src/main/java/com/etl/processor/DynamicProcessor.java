package com.etl.processor;

import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.source.SourceConfig;
import org.springframework.batch.item.ItemProcessor;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.target.TargetConfig;

public interface DynamicProcessor<I, O> {
    String getType();


    default ItemProcessor<I, O> getProcessor(ProcessorConfig processorConfig,
                                             SourceConfig sourceConfig,
                                             TargetConfig targetConfig) throws Exception {
        return getProcessor(processorConfig, sourceConfig, targetConfig, null);
    }

    ItemProcessor<I, O> getProcessor(ProcessorConfig processorConfig,
                                     SourceConfig sourceConfig,
                                     TargetConfig targetConfig,
                                     ResolvedModelMetadata metadata) throws Exception;
}
