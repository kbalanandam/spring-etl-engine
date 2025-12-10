package com.etl.model.generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.etl.config.source.SourceConfig;
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
			if (generator == null || generator.getType() == null || generator.getType().isEmpty()) {
				logger.warn("Skipping invalid generator found by Spring: {}", generator);
				continue;
			}
			this.generators.put(generator.getType().toLowerCase(), generator);
			logger.info("Spring registered generator: {}", generator.getType());
		}
	}
	/**
	 * This method is automatically invoked by Spring when the application is fully
	 * started and ready to handle requests (specifically, when an ApplicationReadyEvent is published).
	 * It orchestrates the generation of source and target model classes based on the configurations.
	 * It ensures that the model generation process runs only once during the application lifecycle.
	 */

	@EventListener(ApplicationReadyEvent.class)
	public void generateModels() {
		if (alreadyGenerated) {
			logger.info("Model generation already completed. Skipping.");
			return;
		}
		logger.info("Starting source model generation...");
		for (SourceConfig source : sourceWrapper.getSources()) {
			logger.info("Processing source: {}", source.getSourceName());
			try {

				ModelFormat format = ModelFormat.fromString(source.getType());
				ModelGenerator<?> generator = generators.get(format.getFormat().toLowerCase());

				if (generator != null) {
					generator.generateModel(source);
				} else {
					logger.error(
							"❌ No model generator found for validated source type: '{}'. Skipping generation for {}.",
							format.getFormat(), source.getSourceName());
					System.exit(1);
				}
			} catch (IllegalArgumentException e) {

				logger.error("❌ Unsupported model format for source '{}': '{}'. Skipping generation for {}.",
						source.getSourceName(), source.getType(), source.getSourceName());
				System.exit(1);
			} catch (Exception e) {
				logger.error("❌ Failed to generate source model class for {}: {}", source.getSourceName(),
						e.getMessage(), e);
				System.exit(1);
			}
		}
		logger.info("✅ Source model generation completed.");

		logger.info("Starting target model generation...");
		for (TargetConfig target : targetWrapper.getTargets()) {
			logger.info("Processing target: {}", target.getTargetName());
			try {

				ModelFormat format = ModelFormat.fromString(target.getType());
				ModelGenerator<?> generator = generators.get(format.getFormat().toLowerCase());

				if (generator != null) {
					generator.generateModel(target);
				} else {
					logger.error(
							"❌ No model generator found for validated target type: '{}'. Skipping generation for {}.",
							format.getFormat(), target.getTargetName());
					System.exit(1);
				}
			} catch (IllegalArgumentException e) {
				logger.error("❌ Unsupported model format for target '{}': '{}'. Skipping generation for {}.",
						target.getTargetName(), target.getType(), target.getTargetName());
				System.exit(1);
			} catch (Exception e) {
				logger.error("❌ Failed to generate target model class for {}: {}", target.getTargetName(), e.getMessage(), e);
				System.exit(1);
			}
		}
		logger.info("✅ Target model generation completed.");
		alreadyGenerated = true;
	}
}
