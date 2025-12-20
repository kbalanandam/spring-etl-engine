package com.etl.model.generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.etl.config.source.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.enums.ModelFormat;

/**
 * ModelGeneratorFactory is responsible for generating model classes based on
 * the source and target configurations defined in the YAML file. It uses a map
 * to store different ModelGenerator implementations keyed by their type. This
 * factory is a Spring component and will be initialized and managed by the
 * Spring container. It's active only when the "dev" profile is enabled.
 */
@Profile("dev")
@Component
public class ModelGeneratorFactory {

	private static final Logger logger = LoggerFactory.getLogger(ModelGeneratorFactory.class);
	private final SourceWrapper sourceWrapper;
	private final TargetWrapper targetWrapper;
	private final Map<String, ModelGenerator<?>> generators;
	private boolean alreadyGenerated = false;

	/**
	 * Constructs a new ModelGeneratorFactory.
	 * Spring will automatically inject the SourceWrapper, TargetWrapper, and a list of
	 * all available ModelGenerator beans into this constructor.
	 *
	 * @param sourceWrapper The wrapper containing source configurations, typically loaded from a YAML file.
	 * @param targetWrapper The wrapper containing target configurations, typically loaded from a YAML file.
	 * @param modelGenerators A list of all ModelGenerator beans discovered and managed by Spring.
	 */

	public ModelGeneratorFactory(SourceWrapper sourceWrapper, TargetWrapper targetWrapper,
								 List<ModelGenerator<?>> modelGenerators) {
		this.sourceWrapper = sourceWrapper;
		this.targetWrapper = targetWrapper;
		this.generators = new HashMap<>();

		for (ModelGenerator<?> generator : modelGenerators) {
			if (generator != null && generator.getType() != null) {
				this.generators.put(generator.getType().toLowerCase(), generator);
				logger.info("Spring registered generator: {}", generator.getType());
			}
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void generateModels() {
		if (alreadyGenerated) {
			logger.info("Model generation already completed. Skipping..");
			return;
		}

		// Logic is now clean and readable
		processModels(sourceWrapper.getSources(), "source");
		processModels(targetWrapper.getTargets(), "target");

		alreadyGenerated = true;
	}

	/**
	 * Helper method to handle model generation logic for any list of configurations.
	 * This reduces cognitive complexity by eliminating code duplication.
	 */
	private void processModels(List<?> configs, String label) {
		logger.info("Starting {} model generation...", label);
		for (Object config : configs) {
			generateSingleModel(config, label);
		}
		logger.info("{} model generation completed.", label);
	}

	private void generateSingleModel(Object config, String label) {
		String name = getModelName(config);
		String type = getModelType(config);

		logger.info("Processing {}: {}", label, name);
		try {
			ModelFormat format = ModelFormat.fromString(type);
			ModelGenerator<?> generator = generators.get(format.getFormat().toLowerCase());

			if (generator == null) {
				handleFailure("No model generator found for type: " + type, name);
				return;
			}
			generator.generateModel(config);

		} catch (IllegalArgumentException e) {
			handleFailure("Unsupported model format: " + type, name);
		} catch (Exception e) {
			handleFailure("Critical failure: " + e.getMessage(), name);
		}
	}

	private void handleFailure(String message, String name) {
		logger.error("{} for {}. Exiting.", message, name);
		System.exit(1);
	}
	// Helper methods to treat Source and Target configs generically
	private String getModelName(Object config) {
		return (config instanceof SourceConfig) ?
				((SourceConfig) config).getSourceName() : ((TargetConfig) config).getTargetName();
	}

	private String getModelType(Object config) {
		return (config instanceof SourceConfig) ?
				((SourceConfig) config).getType() : ((TargetConfig) config).getType();
	}
}