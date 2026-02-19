package com.etl.model.generator;

import com.etl.config.ModelConfig;
import com.etl.config.ModelPathConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.enums.ModelFormat;
import com.etl.model.exception.ModelGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.tools.StandardJavaFileManager;
import javax.tools.JavaFileObject;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
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
	private final ModelPathConfig modelPathConfig;

	/**
	 * Enum to track the status of each model generation.
	 */
	enum GenerationStatus {
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
	 * @param modelPathConfig   The configuration for model source and class paths.
	 */
	public ModelGeneratorFactory(SourceWrapper sourceWrapper,
								 TargetWrapper targetWrapper,
								 List<ModelGenerator<?>> modelGenerators,
								 ModelPathConfig modelPathConfig) {
		this.sourceWrapper = sourceWrapper;
		this.targetWrapper = targetWrapper;
		this.generators = new HashMap<>();
		this.modelPathConfig = modelPathConfig;

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

		this.generateModels();
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
			totalSuccessCount += processModels(sourceWrapper.getSources(), "source");
			totalSuccessCount += processModels(targetWrapper.getTargets(), "target");

			int totalProcessedCount = getTotalConfigCount();

			logger.info("Model generation completed. Total: {} processed, {} successful, {} failed",
				totalProcessedCount, totalSuccessCount, totalProcessedCount - totalSuccessCount);

			// Log detailed status
			logGenerationStatus();

			// Compile and load generated model classes
			compileAndLoadGeneratedModels();

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
	 * Compiles and loads all generated model classes from both source and target model packages.
	 * Looks in 'src/main/java/com/etl/model/source' and 'src/main/java/com/etl/model/target'.
	 */
	private void compileAndLoadGeneratedModels() {
		try {
			// Always compile to the main output directory
			String outputDir = "target/classes";
			String[] generatedSrcDirs = {
				modelPathConfig.getSourceDir(),
				modelPathConfig.getTargetDir()
			};
			List<File> javaFiles = new ArrayList<>();
			for (String dirPath : generatedSrcDirs) {
				File srcDir = new File(dirPath);
				if (srcDir.exists() && srcDir.isDirectory()) {
					collectJavaFiles(srcDir, javaFiles);
				} else {
					logger.warn("No generated source directory found at {}. Skipping.", dirPath);
				}
			}
			if (javaFiles.isEmpty()) {
				logger.warn("No generated Java files found in source/target model directories. Skipping compilation.");
				return;
			}

			// Compile Java files to target/classes
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			if (compiler == null) {
				throw new IllegalStateException("No Java compiler available. Are you running a JRE instead of a JDK?");
			}
			List<String> options = List.of("-d", outputDir);
			try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
				Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(javaFiles);
				boolean success = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
				if (!success) {
					throw new IllegalStateException("Compilation of generated model classes failed.");
				}
			}

			// Load all .class files from target/classes/com/etl/model/source and target
			List<File> classFiles = new ArrayList<>();
			collectClassFiles(new File(outputDir + "/com/etl/model/source"), classFiles);
			collectClassFiles(new File(outputDir + "/com/etl/model/target"), classFiles);
			if (classFiles.isEmpty()) {
				logger.warn("No compiled class files found in output directory. Skipping class loading.");
				return;
			}
			try (URLClassLoader classLoader = new URLClassLoader(new URL[]{new File(outputDir).getAbsoluteFile().toURI().toURL()}, Thread.currentThread().getContextClassLoader())) {
				for (File classFile : classFiles) {
					String absPath = classFile.getAbsolutePath().replace("\\", "/");
					int idx = absPath.indexOf("/com/etl/model/");
					if (idx == -1) continue;
					String relPath = absPath.substring(idx + 1); // skip leading /
					String className = relPath.replace("/", ".").replace(".class", "");
					try {
						Class<?> loadedClass = classLoader.loadClass(className);
						logger.info("Loaded generated model class: {}", loadedClass.getName());
					} catch (ClassNotFoundException e) {
						logger.error("Failed to load generated class: {}", className, e);
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error during compilation/loading of generated models", e);
			throw new ModelGenerationException("Failed to compile/load generated models: " + e.getMessage());
		}
	}

	private void collectJavaFiles(File dir, List<File> javaFiles) {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) {
				collectJavaFiles(file, javaFiles);
			} else if (file.getName().endsWith(".java")) {
				javaFiles.add(file);
			}
		}
	}

	private void collectClassFiles(File dir, List<File> classFiles) {
		if (dir == null || !dir.exists()) return;
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) {
				collectClassFiles(file, classFiles);
			} else if (file.getName().endsWith(".class")) {
				classFiles.add(file);
			}
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
		// Use pattern matching for instanceof
		if (!(config instanceof ModelConfig modelConfig)) {
			String errorMsg = String.format("Invalid config type for %s. Expected ModelConfig, got %s",
					label, config != null ? config.getClass().getName() : "null");
			handleFailure(errorMsg, "unknown-config");
			return false;
		}
		String name = modelConfig.getModelName();
		if (name == null || name.isBlank()) {
			handleFailure("Model name is null or empty", "unnamed-model");
			return false;
		}
		// Track generation status
		generationStatus.put(name, GenerationStatus.PENDING);
		logger.info("Processing {}: {}", label, name);

		try {
			String format = extractFormat(modelConfig, name);
			if (format == null) {
				return false;
			}
			ModelFormat modelFormat = ModelFormat.fromString(format);
            String formatKey = modelFormat.getFormat().trim().toLowerCase();
			ModelGenerator<?> generator = generators.get(formatKey);
			if (generator == null) {
				String available = String.join(", ", generators.keySet());
				handleFailure(String.format("No model generator found for type: '%s'. Available: [%s]",
						formatKey, available), name);
				return false;
			}
			generateModelWithGeneric(generator, config);
			generationStatus.put(name, GenerationStatus.SUCCESS);
			logger.debug("Successfully generated model: {}", name);
			return true;
		} catch (IllegalArgumentException e) {
			handleFailure("Invalid configuration: " + e.getMessage(), name);
			return false;
		} catch (Exception e) {
			handleFailure("Generation failed: " + e.getMessage(), name);
			return false;
		}
	}
	// Helper method for type-safe generic invocation
	@SuppressWarnings("unchecked")
	private <T extends ModelConfig> void generateModelWithGeneric(ModelGenerator<?> generator, Object config) throws Exception {
		((ModelGenerator<T>) generator).generateModel((T) config);
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
					String format = targetConfig.getFormat().getFormat();
					if (format.isBlank()) {
						handleFailure("Format is blank for TARGET config", name);
						yield null;
					}
					yield format.trim();
				} else {
					handleFailure("Config is marked as TARGET but not a TargetConfig instance", name);
					yield null;
				}
			}
			case SOURCE -> {
				if (modelConfig instanceof SourceConfig sourceConfig) {
					String format = sourceConfig.getFormat().getFormat();
					if (format.isBlank()) {
						handleFailure("Format is blank for SOURCE config", name);
						yield null;
					}
					yield format.trim();
				} else {
					handleFailure("Config is marked as SOURCE but not a SourceConfig instance", name);
					yield null;
				}
			}
			default -> {
				handleFailure("Unknown ModelType: " + modelConfig.getModelType(), name);
				yield null;
			}
		};
	}

	/**
	 * Handles generation failures with configurable error handling.
	 *
	 * @param message    The error message
	 * @param modelName  The name of the model that failed
	 */
	private void handleFailure(String message, String modelName) {
		logger.error("{} for model: {}", message, modelName);
		generationStatus.put(modelName, GenerationStatus.FAILED);
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
}

