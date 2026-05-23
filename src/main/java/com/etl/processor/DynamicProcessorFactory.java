package com.etl.processor;

import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.source.SourceConfig;
import com.etl.exception.EtlException;
import com.etl.exception.FactoryException;
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

	private final DynamicProcessor<Object, Object> defaultProcessor;

	/**
	 * DynamicProcessorFactory is responsible for creating the shared active processor
	 * used by the selected runtime contract.
	 *
	 * @param defaultProcessor the shared default processor implementation used by the
	 *                         active runtime contract
	 */

	public DynamicProcessorFactory(DynamicProcessor<Object, Object> defaultProcessor) {
		this.defaultProcessor = defaultProcessor;
	}

	/**
	 * Creates the active processor for one selected source/target pairing.
	 *
	 * <p>This factory now routes all selected runtime processor creation through the shared
	 * default processor contract. Non-default processor types are rejected before delegation.</p>
	 */
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
		if (defaultProcessor == null) {
			throw new FactoryException("The active runtime expects a registered 'default' processor implementation, but none was found.");
		}

		try {
			@SuppressWarnings("unchecked")
			ItemProcessor<I, O> processor = (ItemProcessor<I, O>) defaultProcessor.getProcessor(processorConfig, sourceConfig, targetConfig, metadata);
			return processor;
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
