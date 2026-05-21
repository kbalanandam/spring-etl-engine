package com.etl.processor.pipeline;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;

/**
 * Resolves exactly one processor entity mapping for the active source-target pair.
 */
public class MappingSelectionService {

	public ProcessorConfig.EntityMapping select(
			ProcessorConfig processorConfig,
			SourceConfig sourceConfig,
			TargetConfig targetConfig
	) {
		return processorConfig.getMappings()
				.stream()
				.filter(mapping -> mapping.getSource().equalsIgnoreCase(sourceConfig.getSourceName())
						&& mapping.getTarget().equalsIgnoreCase(targetConfig.getTargetName()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"Mapping not found for " + sourceConfig.getSourceName()
								+ " -> " + targetConfig.getTargetName()
				));
	}
}


