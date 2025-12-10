package com.etl.processor;

import org.springframework.batch.item.ItemProcessor;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;

public interface DynamicProcessor<I, O> {
    String getType();

    /** NEW → tells if this processor supports this source–target pair */
    default boolean supports(String sourceName, String targetName) {
        return true; // backward compatible
    }
    default ItemProcessor<I, O> getProcessorWithMapping(ProcessorConfig fullConfig, ProcessorConfig.EntityMapping selectedMapping) throws Exception {
        // default fallback → existing processors still work
        return getProcessor(fullConfig, null, null);
    }

    ItemProcessor<I, O> getProcessor(ProcessorConfig processorConfig, SourceConfig sourceConfig, TargetConfig targetConfig) throws Exception;
}
