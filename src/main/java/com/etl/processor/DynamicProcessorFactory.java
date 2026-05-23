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

	/**
	 * Creates the active processor for one selected source/target pairing.
	 *
	 * <p>This factory is the runtime dispatch seam for processor type selection. It does not
	 * interpret mapping rules itself; instead it ensures that the requested processor type is
	 * registered and then delegates source/target-specific processor construction to the chosen
	 * implementation.</p>
	 */
	@SuppressWarnings("unchecked")
	public <I, O> ItemProcessor<I, O> getProcessor(
			ProcessorConfig processorConfig,
			SourceConfig sourceConfig,
			TargetConfig targetConfig,
			ResolvedModelMetadata metadata) throws Exception {
		if (processorConfig == null) {
			throw new FactoryException("Processor configuration must not be null when creating a processor.");
		}

		String type = processorConfig.getType() == null ? null : processorConfig.getType().trim();
		if (type == null || type.isBlank()) {
			throw new FactoryException("ProcessorConfig.type must be 'default'. The active runtime no longer supports blank processor types.");
		}
		if (!"default".equalsIgnoreCase(type)) {
			throw new FactoryException("ProcessorConfig.type='" + type + "' is no longer supported. The active runtime only accepts 'type: default'.");
		}

        var dp = (DynamicProcessor<I, O>) processorMap.get(type);

		if (dp == null) {
			throw new NoProcessorFoundException("The active runtime expects a registered 'default' processor implementation, but none was found.");
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
