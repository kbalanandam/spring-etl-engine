package com.etl.processor.impl;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.processor.ProcessorConfig.EntityMapping;
import com.etl.config.target.TargetConfig;
import com.etl.mapping.DynamicMapping;
import com.etl.processor.DynamicProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * <p>
 * {@code DefaultDynamicProcessor} provides a dynamic and configuration-driven
 * implementation of {@link ItemProcessor} for ETL transformations.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *     <li>Selects the correct source&ndash;target mapping based on YAML configuration</li>
 *     <li>Applies field-level transformations dynamically using map-based input/output</li>
 *     <li>Ensures different entity pairs (e.g., Customers &rarr; Customers, Department &rarr; Department)
 *         use different mapping rules</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 *     <li>No reflection per record &mdash; simple HashMap-to-HashMap transformation</li>
 *     <li>Supports multiple entity mapping groups from YAML</li>
 *     <li>Validates that a mapping exists for each ETL step</li>
 *     <li>Throws descriptive errors when mapping is missing</li>
 * </ul>
 *
 * <h2>Example YAML Supported</h2>
 *
 * <pre>
 * processor:
 *   type: default
 *   mappings:
 *     - source: Customers
 *       target: Customers
 *       fields:
 *         - from: id
 *           to: id
 *         - from: name
 *           to: name
 *         - from: email
 *           to: email
 *
 *     - source: Department
 *       target: Department
 *       fields:
 *         - from: id
 *           to: id
 *         - from: name
 *           to: name
 * </pre>
 *
 * <p>
 * Each ETL step automatically resolves the correct mapping based on the source and target
 * configurations supplied to the step.
 * </p>
 *
 * @author
 *   ETL Framework Auto-Mapper
 */
@Component("default")
public class DefaultDynamicProcessor implements DynamicProcessor<Object, Object> {

	private static final Logger logger = LoggerFactory.getLogger(DefaultDynamicProcessor.class);

	/**
	 * Returns the processor type identifier used in YAML:
	 *
	 * <pre>
	 * processor:
	 *   type: default
	 * </pre>
	 *
	 * @return fixed identifier {@code "default"}
	 */
	@Override
	public String getType() {
		return "default";
	}

	/**
	 * Builds a dynamic {@link ItemProcessor} that transforms input records using
	 * the field-level mapping defined in YAML for a given source–target pair.
	 *
	 * <p>The method:</p>
	 * <ul>
	 *     <li>Identifies the correct {@link EntityMapping} based on sourceConfig and targetConfig</li>
	 *     <li>Constructs a lightweight processor that copies values from input map to output map</li>
	 *     <li>Ensures that only the correct field list is applied for the given ETL step</li>
	 * </ul>
	 *
	 * @param processorConfig full processor configuration containing mapping groups
	 * @param sourceConfig    source model configuration used in current ETL step
	 * @param targetConfig    target model configuration used in current ETL step
	 *
	 * @return a dynamic mapping {@code ItemProcessor} instance
	 *
	 * @throws IllegalStateException
	 *         if no mapping block exists for the given source–target pair
	 */
	@Override
	public ItemProcessor<Object, Object> getProcessor(
			ProcessorConfig processorConfig,
			SourceConfig sourceConfig,
			TargetConfig targetConfig) throws ClassNotFoundException {

		var mapping = processorConfig.getMappings()
				.stream()
				.filter(m -> m.getSource().equalsIgnoreCase(sourceConfig.getSourceName())
						&& m.getTarget().equalsIgnoreCase(targetConfig.getTargetName()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"Mapping not found for " + sourceConfig.getSourceName()
								+ " → " + targetConfig.getTargetName()
				));

		@SuppressWarnings("unchecked")
		Class<Object> targetClass = (Class<Object>) Class.forName(targetConfig.getPackageName() + "." + targetConfig.getTargetName());
		logger.info("Using mapping for {} → {} with {} fields",
				sourceConfig.getSourceName(),
				targetConfig.getTargetName(),
				mapping.getFields().size()
		);
		return new DynamicMapping<>(mapping, targetClass);
	}
}
