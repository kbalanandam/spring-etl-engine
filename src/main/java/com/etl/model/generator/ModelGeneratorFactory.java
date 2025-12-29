package com.etl.model.generator;

import com.etl.config.ModelConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.enums.ModelFormat;
import com.etl.model.exception.ModelGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
	private final Map<String, GenerationStatus> generationStatus = new ConcurrentHashMap<>();
	private boolean alreadyGenerated = false;

	/**
	 * Enum to track the status of each model generation.
	 */
	private enum GenerationStatus {
		PENDING, SUCCESS, FAILED
	}

	/**
	 * Constructs a new ModelGeneratorFactory.
	 * Spring will automatically inject the SourceWrapper, TargetWrapper, and a list of
	 * all available ModelGenerator beans into this constructor.
	 *
	 * @param sourceWrapper     The wrapper containing source configurations, typically loaded from a YAML file.
	 * @param targetWrapper     The wrapper containing target configurations, typically loaded from a YAML file.
	 * @param modelGenerators   A list of all ModelGenerator beans discovered and managed by Spring.
	 */
	public ModelGeneratorFactory(SourceWrapper sourceWrapper,
								 TargetWrapper targetWrapper,
								 List<ModelGenerator<?>> modelGenerators) {
		this.sourceWrapper = sourceWrapper;
		this.targetWrapper = targetWrapper;
		this.generators = new HashMap<>();

		// Safely register generators with trimmed keys
		Optional.ofNullable(modelGenerators).ifPresent(generatorsList ->
				generatorsList.forEach(generator ->
						Optional.ofNullable(generator)
								.map(ModelGenerator::getType)
								.filter(type -> !type.getFormat().isBlank())
								.map(ModelFormat::getFormat)
								.map(String::trim)  // Trim the type key
								.ifPresent(type -> {
									this.generators.put(type.toLowerCase(), generator);
									logger.info("Spring registered generator: '{}'", type);
								})
				)
		);

		logger.info("ModelGeneratorFactory initialized with {} generators: {}",
				this.generators.size(), this.generators.keySet());

		// Log generator details for debugging
		if (logger.isDebugEnabled()) {
			logGeneratorDetails();
		}
	}

	/**
	 * Logs detailed information about registered generators for debugging.
	 */
	private void logGeneratorDetails() {
		logger.debug("=== Generator Registration Details ===");
		generators.forEach((key, generator) -> {
			logger.debug("Key: '{}' -> Class: {}",
					key, generator.getClass().getSimpleName());
			// Log the actual type string from the generator
			ModelFormat originalType = generator.getType();
			logger.debug("  Original type: '{}' (length: {})",
					originalType, originalType.getFormat().length());
			logger.debug("  Trimmed type: '{}'", originalType.getFormat().trim());
		});
		logger.debug("=== End Generator Details ===");
	}

	/**
	 * Event listener that triggers model generation when the application is ready.
	 * Prevents duplicate generation attempts.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void generateModels() {
		if (alreadyGenerated) {
			logger.info("Model generation already completed. Skipping..");
			return;
		}

		logger.info("Starting model generation process...");

		try {
			// Validate configurations before processing
			validateConfigurations();

			// Process source and target models
			int totalSuccessCount = 0;
			int totalProcessedCount = 0;

			totalSuccessCount += processModels(sourceWrapper.getSources(), "source");
			totalSuccessCount += processModels(targetWrapper.getTargets(), "target");

			totalProcessedCount = getTotalConfigCount();

			logger.info("Model generation completed. Total: {} processed, {} successful, {} failed",
					totalProcessedCount, totalSuccessCount, totalProcessedCount - totalSuccessCount);

			// Log detailed status
			logGenerationStatus();

			alreadyGenerated = true;

		} catch (ModelGenerationException e) {
			logger.error("Model generation failed critically: {}", e.getMessage());
			throw e; // Let Spring handle the exception
		} catch (Exception e) {
			logger.error("Unexpected error during model generation", e);
			throw new ModelGenerationException("Unexpected error during model generation: " + e.getMessage());
		}
	}

	/**
	 * Logs the detailed generation status for all models.
	 */
	private void logGenerationStatus() {
		if (logger.isInfoEnabled()) {
			long pending = generationStatus.values().stream()
					.filter(status -> status == GenerationStatus.PENDING)
					.count();
			long success = generationStatus.values().stream()
					.filter(status -> status == GenerationStatus.SUCCESS)
					.count();
			long failed = generationStatus.values().stream()
					.filter(status -> status == GenerationStatus.FAILED)
					.count();

			logger.info("Generation Status - Pending: {}, Success: {}, Failed: {}",
					pending, success, failed);
		}
	}

	/**
	 * Helper method to handle model generation logic for any list of configurations.
	 * This reduces cognitive complexity by eliminating code duplication.
	 *
	 * @param configs The list of configurations to process
	 * @param label   Descriptive label for logging (e.g., "source" or "target")
	 * @return Number of successfully generated models
	 */
	private int processModels(List<?> configs, String label) {
		if (configs == null || configs.isEmpty()) {
			logger.info("No {} configurations found.", label);
			return 0;
		}

		logger.info("Starting {} model generation for {} configs...", label, configs.size());

		int successCount = 0;
		for (Object config : configs) {
			if (generateSingleModel(config, label)) {
				successCount++;
			}
		}

		logger.info("{} model generation completed. Success: {}/{}",
				label, successCount, configs.size());
		return successCount;
	}

	/**
	 * Generates a single model from a configuration object.
	 *
	 * @param config The configuration object
	 * @param label  Descriptive label for logging
	 * @return true if generation was successful, false otherwise
	 */
	private boolean generateSingleModel(Object config, String label) {
		// Validate input
		if (!(config instanceof ModelConfig)) {
			String errorMsg = String.format("Invalid config type for %s. Expected ModelConfig, got %s",
					label, config != null ? config.getClass().getName() : "null");
			handleFailure(errorMsg, "unknown-config", false);
			return false;
		}

		ModelConfig modelConfig = (ModelConfig) config;
		String name = modelConfig.getModelName();

		if (name == null || name.isBlank()) {
			handleFailure("Model name is null or empty", "unnamed-model", false);
			return false;
		}

		// Track generation status
		generationStatus.put(name, GenerationStatus.PENDING);
		logger.info("Processing {}: {}", label, name);

		try {
			// Extract format based on model type
			String format = extractFormat(modelConfig, name);
			if (format == null) {
				return false; // Error already logged in extractFormat
			}

			// Get model format enum
			ModelFormat modelFormat = ModelFormat.fromString(format);
			if (modelFormat == null) {
				handleFailure("Unsupported model format: " + format, name, false);
				return false;
			}

			// Find appropriate generator with trimmed key
			String formatKey = modelFormat.getFormat().trim().toLowerCase();
			ModelGenerator<?> generator = generators.get(formatKey);

			if (generator == null) {
				// Provide more helpful error message
				String available = String.join(", ", generators.keySet());
				handleFailure(String.format("No model generator found for type: '%s'. Available: [%s]",
						formatKey, available), name, false);
				return false;
			}

			// Generate the model
			generator.generateModel(config);

			// Update status on success
			generationStatus.put(name, GenerationStatus.SUCCESS);
			logger.debug("Successfully generated model: {}", name);
			return true;

		} catch (IllegalArgumentException e) {
			handleFailure("Invalid configuration: " + e.getMessage(), name, false);
			return false;
		} catch (Exception e) {
			handleFailure("Generation failed: " + e.getMessage(), name, false);
			return false;
		}
	}

	/**
	 * Extracts the format string from a ModelConfig based on its type.
	 * Uses pattern matching for type safety.
	 *
	 * @param modelConfig The model configuration
	 * @param name        The model name for error reporting
	 * @return The format string, or null if extraction failed
	 */
	private String extractFormat(ModelConfig modelConfig, String name) {
		return switch (modelConfig.getModelType()) {
			case TARGET -> {
				if (modelConfig instanceof TargetConfig targetConfig) {
					String format = targetConfig.getFormat();
					if (format == null || format.isBlank()) {
						handleFailure("Format is null or empty for TARGET config", name, false);
						yield null;
					}
					yield format.trim();
				} else {
					handleFailure("Config is marked as TARGET but not a TargetConfig instance", name, false);
					yield null;
				}
			}
			case SOURCE -> {
				if (modelConfig instanceof SourceConfig sourceConfig) {
					String format = sourceConfig.getFormat();
					if (format == null || format.isBlank()) {
						handleFailure("Format is null or empty for SOURCE config", name, false);
						yield null;
					}
					yield format.trim();
				} else {
					handleFailure("Config is marked as SOURCE but not a SourceConfig instance", name, false);
					yield null;
				}
			}
			default -> {
				handleFailure("Unknown ModelType: " + modelConfig.getModelType(), name, false);
				yield null;
			}
		};
	}

	/**
	 * Handles generation failures with configurable error handling.
	 *
	 * @param message    The error message
	 * @param modelName  The name of the model that failed
	 * @param critical   Whether this is a critical failure that should stop the entire process
	 */
	private void handleFailure(String message, String modelName, boolean critical) {
		logger.error("{} for model: {}", message, modelName);
		generationStatus.put(modelName, GenerationStatus.FAILED);

		if (critical) {
			throw new ModelGenerationException(message + " for model: " + modelName);
		}
	}

	/**
	 * Validates all configurations before processing.
	 *
	 * @throws ModelGenerationException if validation fails
	 */
	private void validateConfigurations() {
		logger.info("Validating configurations...");

		// Check for duplicate model names
		validateNoDuplicateNames(sourceWrapper.getSources(), "source");
		validateNoDuplicateNames(targetWrapper.getTargets(), "target");

		// Check that all required generators are available
		validateRequiredGenerators();

		logger.info("Configuration validation passed");
	}

	/**
	 * Validates that there are no duplicate model names within a configuration list.
	 */
	private void validateNoDuplicateNames(List<?> configs, String label) {
		if (configs == null || configs.isEmpty()) {
			logger.debug("No {} configurations to check for duplicates", label);
			return;
		}

		Map<String, Integer> nameCount = new HashMap<>();
		for (Object config : configs) {
			if (config instanceof ModelConfig modelConfig) {
				String name = modelConfig.getModelName();
				if (name != null) {
					nameCount.put(name, nameCount.getOrDefault(name, 0) + 1);
				}
			}
		}

		// Log duplicates as warnings
		nameCount.entrySet().stream()
				.filter(entry -> entry.getValue() > 1)
				.forEach(entry ->
						logger.warn("Duplicate {} model name found: '{}' (appears {} times)",
								label, entry.getKey(), entry.getValue())
				);
	}

	/**
	 * Validates that all required generators are available.
	 */
	private void validateRequiredGenerators() {
		// Collect all required format types from configurations
		List<String> requiredFormats = collectAllRequiredFormats();

		if (requiredFormats.isEmpty()) {
			logger.warn("No required formats found in configurations");
			return;
		}

		logger.debug("Required formats: {}", requiredFormats);
		logger.debug("Available generators: {}", generators.keySet());

		// Check each required format has a generator (with trimming)
		List<String> missingGenerators = requiredFormats.stream()
				.filter(format -> !generators.containsKey(format.toLowerCase()))
				.toList();

		if (!missingGenerators.isEmpty()) {
			String errorMessage = String.format(
					"Missing generators for formats: %s. Available generators: %s",
					missingGenerators, generators.keySet()
			);
			logger.error(errorMessage);
			throw new ModelGenerationException(errorMessage);
		}

		logger.info("All required generators are available");
	}

	/**
	 * Collects all unique format types from both source and target configurations.
	 *
	 * @return List of required format types (trimmed and lowercased)
	 */
	private List<String> collectAllRequiredFormats() {
		List<String> requiredFormats = java.util.stream.Stream.concat(
						extractFormatsFromConfigs(sourceWrapper.getSources()),
						extractFormatsFromConfigs(targetWrapper.getTargets())
				)
				.filter(format -> format != null && !format.isBlank())
				.map(String::trim)  // Trim whitespace
				.map(String::toLowerCase)
				.distinct()
				.toList();

		logger.debug("Collected required formats: {}", requiredFormats);
		return requiredFormats;
	}

	/**
	 * Extracts format strings from a list of configurations.
	 */
	private java.util.stream.Stream<String> extractFormatsFromConfigs(List<?> configs) {
		if (configs == null) {
			return java.util.stream.Stream.empty();
		}

		return configs.stream()
				.filter(config -> config instanceof ModelConfig)
				.map(config -> (ModelConfig) config)
				.map(this::extractFormatSafely);
	}

	/**
	 * Safely extracts format from a ModelConfig without throwing exceptions.
	 */
	private String extractFormatSafely(ModelConfig modelConfig) {
		try {
			return extractFormat(modelConfig, modelConfig.getModelName());
		} catch (Exception e) {
			logger.warn("Failed to extract format for model: {}", modelConfig.getModelName(), e);
			return null;
		}
	}

	/**
	 * Gets the total count of all configurations.
	 */
	private int getTotalConfigCount() {
		int sourceCount = sourceWrapper.getSources() != null ? sourceWrapper.getSources().size() : 0;
		int targetCount = targetWrapper.getTargets() != null ? targetWrapper.getTargets().size() : 0;
		return sourceCount + targetCount;
	}

	/**
	 * Gets the current generation status for all models.
	 * Useful for monitoring and debugging.
	 *
	 * @return A copy of the generation status map
	 */
	public Map<String, GenerationStatus> getGenerationStatus() {
		return new HashMap<>(generationStatus);
	}

	/**
	 * Gets a summary of generation statistics.
	 *
	 * @return Map with success, failed, and pending counts
	 */
	public Map<String, Long> getGenerationStatistics() {
		Map<String, Long> stats = new HashMap<>();
		stats.put("total", (long) generationStatus.size());
		stats.put("success", generationStatus.values().stream()
				.filter(status -> status == GenerationStatus.SUCCESS)
				.count());
		stats.put("failed", generationStatus.values().stream()
				.filter(status -> status == GenerationStatus.FAILED)
				.count());
		stats.put("pending", generationStatus.values().stream()
				.filter(status -> status == GenerationStatus.PENDING)
				.count());
		return stats;
	}

	/**
	 * Resets the factory state for testing purposes.
	 * Package-private for unit testing.
	 */
	void reset() {
		alreadyGenerated = false;
		generationStatus.clear();
		logger.info("ModelGeneratorFactory has been reset");
	}
}