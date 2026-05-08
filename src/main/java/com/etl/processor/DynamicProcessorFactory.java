package com.etl.processor;

import java.util.Map;

import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.source.SourceConfig;
import com.etl.exception.EtlException;
import com.etl.exception.FactoryException;
import com.etl.processor.exception.NoProcessorFoundException;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.target.TargetConfig;

/**
 * Creates processor implementations for the active processor type.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This remains the current runtime dispatch seam for processor selection in 1.4.x.
 * Keep it stable during migration, but avoid letting new architecture work depend on
 * this class as the final processor orchestration model without an explicit design decision.</p>
 */
@Component
public class DynamicProcessorFactory {

	private final Map<String, DynamicProcessor<?, ?>> processorMap;

	/**
	 * DynamicProcessorFactory is responsible for creating instances of
	 * DynamicProcessor based on the type specified in the ProcessorConfig. It uses
	 * a map to store different DynamicProcessor implementations keyed by their
	 * type.
	 *
	 * @param processorMap A map of processor types to their corresponding
	 *                     DynamicProcessor implementations.
	 */

	public DynamicProcessorFactory(Map<String, DynamicProcessor<?, ?>> processorMap) {
		this.processorMap = processorMap;
	}

	@SuppressWarnings("unchecked")
	public <I, O> ItemProcessor<I, O> getProcessor(
			ProcessorConfig processorConfig,
			SourceConfig sourceConfig,
			TargetConfig targetConfig,
			ResolvedModelMetadata metadata) throws Exception {
		if (processorConfig == null) {
			throw new FactoryException("Processor configuration must not be null when creating a processor.");
		}

		String type = processorConfig.getType();

        var dp = (DynamicProcessor<I, O>) processorMap.get(type);

		if (dp == null) {
			throw new NoProcessorFoundException("No processor found for type: " + type);
		}

		try {
			return dp.getProcessor(processorConfig, sourceConfig, targetConfig, metadata);
		} catch (EtlException e) {
			throw e;
		} catch (Exception e) {
			throw new FactoryException(
					"Failed to create processor for type '" + type + "'"
							+ " source='" + defaultName(sourceConfig == null ? null : sourceConfig.getSourceName()) + "'"
							+ " target='" + defaultName(targetConfig == null ? null : targetConfig.getTargetName()) + "'.",
					e
			);
		}
	}

	private String defaultName(String value) {
		return value == null || value.isBlank() ? "unnamed" : value.trim();
	}
}
