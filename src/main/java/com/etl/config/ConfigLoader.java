package com.etl.config;

import com.etl.config.exception.ConfigException;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.RelationalSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.nio.file.Files.readString;

@Configuration
public class ConfigLoader {

	private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

	@Value("${etl.config.source:src/main/resources/source-config.yaml}")
	private String sourceConfigPath;

	@Value("${etl.config.target:src/main/resources/target-config.yaml}")
	private String targetConfigPath;

	@Value("${etl.config.processor:src/main/resources/processor-config.yaml}")
	private String processorConfigPath;

	@Value("${etl.config.job:}")
	private String jobConfigPath;

	@Value("${etl.config.allow-demo-fallback:false}")
	private boolean allowDemoFallback;

	private volatile ResolvedRuntimeConfig cachedRuntimeConfig;

    /**
     * ConfigLoader is a Spring configuration class that loads YAML configuration
     * files for source, target, and processor configurations. It uses Jackson's
     * ObjectMapper to read the YAML files and convert them into Java objects.
     */

    @Bean
    SourceWrapper sourceWrapper() {
		try {
			ResolvedRuntimeConfig runtimeConfig = resolveRuntimeConfig();
			if (runtimeConfig.requireExternalConfigs()) {
				return loadRequiredExternalYamlConfig(runtimeConfig.sourceConfigPath(), SourceWrapper.class);
			}
			return loadYamlConfig(runtimeConfig.sourceConfigPath(), "source-config.yaml", SourceWrapper.class);
		} catch (ConfigException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigException("Failed to load source config YAML", e);
		}
	}

	@Bean
	TargetWrapper targetWrapper() {
		try {
			ResolvedRuntimeConfig runtimeConfig = resolveRuntimeConfig();
			if (runtimeConfig.requireExternalConfigs()) {
				return loadRequiredExternalYamlConfig(runtimeConfig.targetConfigPath(), TargetWrapper.class);
			}
			return loadYamlConfig(runtimeConfig.targetConfigPath(), "target-config.yaml", TargetWrapper.class);
		} catch (ConfigException e) {
			throw e;
		} catch (Exception e) {

			throw new ConfigException("Failed to load target config YAML", e);
		}
	}

	@Bean
	public ProcessorConfig processorConfig() {
		try {
			ObjectMapper mapper = buildYamlMapper();
			ResolvedRuntimeConfig runtimeConfig = resolveRuntimeConfig();
			ProcessorConfig config = runtimeConfig.requireExternalConfigs()
					? loadRequiredExternalYamlConfig(runtimeConfig.processorConfigPath(), ProcessorConfig.class, mapper)
					: loadYamlConfig(runtimeConfig.processorConfigPath(), "processor-config.yaml", ProcessorConfig.class, mapper);

			// --- Validation step ---

			if (config.getMappings() == null || config.getMappings().isEmpty()) {
				throw new IllegalStateException("No entity mappings found in processor YAML");
			}

			for (ProcessorConfig.EntityMapping em : config.getMappings()) {
				for (int i = 0; i < config.getMappings().size(); i++) {
					ProcessorConfig.EntityMapping m = config.getMappings().get(i);
                    logger.debug("Mapping {}: source={}, target={}", i, m.getSource(), m.getTarget());
				}
                logger.debug("Validating EntityMapping size: {}", config.getMappings().size());
				if (em.getSource() == null || em.getSource().isEmpty()) {
					throw new IllegalStateException("EntityMapping missing 'source' property: " + em);
				}
				if (em.getTarget() == null || em.getTarget().isEmpty()) {
					throw new IllegalStateException("EntityMapping missing 'target' property: " + em);
				}
				if (em.getFields() == null || em.getFields().isEmpty()) {
					throw new IllegalStateException("EntityMapping for source=" + em.getSource() + " and target=" + em.getTarget() + " has no field mappings");
				}

				// Optional: validate individual field mappings
				for (ProcessorConfig.FieldMapping fm : em.getFields()) {
					if (fm.getFrom() == null || fm.getFrom().isEmpty()) {
						throw new IllegalStateException("FieldMapping missing 'from' in entity " + em.getSource());
					}
					if (fm.getTo() == null || fm.getTo().isEmpty()) {
						throw new IllegalStateException("FieldMapping missing 'to' in entity " + em.getSource());
					}
				}
			}
			logger.info("Processor configuration loaded and validated successfully from YAML");
			return config;
		} catch (ConfigException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigException("Failed to load or validate processor config YAML", e);
		}
	}

	@Bean
	RunConfigurationMetadata runConfigurationMetadata() {
		try {
			ResolvedRuntimeConfig runtimeConfig = resolveRuntimeConfig();
			return new RunConfigurationMetadata(
					runtimeConfig.scenarioName(),
					runtimeConfig.jobConfigPath(),
					runtimeConfig.demoFallbackMode(),
					runtimeConfig.steps()
			);
		} catch (ConfigException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigException("Failed to resolve runtime configuration metadata", e);
		}
	}

	private ObjectMapper buildYamlMapper() {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.findAndRegisterModules();
		return mapper;
	}

	private <T> T loadYamlConfig(String configuredPath, String fallbackClasspathResource, Class<T> targetType) throws IOException {
		return loadYamlConfig(configuredPath, fallbackClasspathResource, targetType, buildYamlMapper());
	}

	private <T> T loadRequiredExternalYamlConfig(String configuredPath, Class<T> targetType) throws IOException {
		return loadRequiredExternalYamlConfig(configuredPath, targetType, buildYamlMapper());
	}

	private <T> T loadYamlConfig(String configuredPath, String fallbackClasspathResource, Class<T> targetType, ObjectMapper mapper) throws IOException {
		File externalFile = new File(configuredPath);
		if (externalFile.exists() && externalFile.isFile()) {
			logger.info("Loading {} from external YAML file: {}", targetType.getSimpleName(), configuredPath);
			if (logger.isDebugEnabled()) {
				logger.debug("YAML content from {}:\n{}", configuredPath, readString(externalFile.toPath()));
			}
			return mapper.readValue(externalFile, targetType);
		}

		logger.warn("Configured YAML file not found at {}. Falling back to classpath resource: {}", configuredPath, fallbackClasspathResource);
		ClassPathResource classPathResource = new ClassPathResource(fallbackClasspathResource);
		if (!classPathResource.exists()) {
			throw new IOException("Fallback classpath resource not found: " + fallbackClasspathResource);
		}

		try (InputStream inputStream = classPathResource.getInputStream()) {
			return mapper.readValue(inputStream, targetType);
		}
	}

	private <T> T loadRequiredExternalYamlConfig(String configuredPath, Class<T> targetType, ObjectMapper mapper) throws IOException {
		File externalFile = new File(configuredPath);
		if (!externalFile.exists() || !externalFile.isFile()) {
			throw new IOException("Required YAML file not found: " + configuredPath);
		}

		logger.info("Loading {} from job-config referenced YAML file: {}", targetType.getSimpleName(), configuredPath);
		if (logger.isDebugEnabled()) {
			logger.debug("YAML content from {}:\n{}", configuredPath, readString(externalFile.toPath()));
		}
		return mapper.readValue(externalFile, targetType);
	}

	private ResolvedRuntimeConfig resolveRuntimeConfig() throws IOException {
		ResolvedRuntimeConfig existing = cachedRuntimeConfig;
		if (existing != null) {
			return existing;
		}

		synchronized (this) {
			if (cachedRuntimeConfig == null) {
				cachedRuntimeConfig = buildRuntimeConfig();
			}
			return cachedRuntimeConfig;
		}
	}

	private ResolvedRuntimeConfig buildRuntimeConfig() throws IOException {
		if (jobConfigPath == null || jobConfigPath.isBlank()) {
			if (!allowDemoFallback) {
				logger.error("Missing required runtime property 'etl.config.job'. Demo fallback is disabled, so startup cannot continue.");
				throw new ConfigException(
						"Missing required runtime property 'etl.config.job'. " +
						"Set it to a job-config.yaml path, or enable demo fallback with 'etl.config.allow-demo-fallback=true' for local/demo runs."
				);
			}

			logger.warn("No 'etl.config.job' was provided. Demo fallback is enabled, so the runtime will use direct config paths and may fall back to bundled classpath YAML resources. This mode is intended for local/demo use only.");
			SourceWrapper demoSourceWrapper = loadYamlConfig(sourceConfigPath, "source-config.yaml", SourceWrapper.class);
			TargetWrapper demoTargetWrapper = loadYamlConfig(targetConfigPath, "target-config.yaml", TargetWrapper.class);
			ProcessorConfig demoProcessorConfig = loadYamlConfig(processorConfigPath, "processor-config.yaml", ProcessorConfig.class, buildYamlMapper());
			return new ResolvedRuntimeConfig(
					sourceConfigPath,
					targetConfigPath,
					processorConfigPath,
					false,
					"demo-fallback",
					"",
					true,
					synthesizeDemoSteps(demoSourceWrapper, demoTargetWrapper, demoProcessorConfig)
			);
		}

		ObjectMapper mapper = buildYamlMapper();
		File jobConfigFile = new File(jobConfigPath);
		if (!jobConfigFile.exists() || !jobConfigFile.isFile()) {
			logger.error("Configured job config YAML not found at {}. Explicit job selection never falls back automatically.", jobConfigPath);
			throw new ConfigException("Configured job config YAML not found at " + jobConfigPath);
		}

		logger.info("Loading JobConfig from external YAML file: {}", jobConfigPath);
		JobConfig jobConfig = mapper.readValue(jobConfigFile, JobConfig.class);
		Path jobConfigDirectory = jobConfigFile.getAbsoluteFile().getParentFile().toPath();
		String scenarioName = deriveScenarioName(jobConfig, jobConfigDirectory);
		String resolvedSourceConfigPath = resolveReferencedPath(jobConfigDirectory, jobConfig.getSourceConfigPath(), "sourceConfigPath");
		String resolvedTargetConfigPath = resolveReferencedPath(jobConfigDirectory, jobConfig.getTargetConfigPath(), "targetConfigPath");
		String resolvedProcessorConfigPath = resolveReferencedPath(jobConfigDirectory, jobConfig.getProcessorConfigPath(), "processorConfigPath");
		SourceWrapper explicitSourceWrapper = loadRequiredExternalYamlConfig(resolvedSourceConfigPath, SourceWrapper.class);
		TargetWrapper explicitTargetWrapper = loadRequiredExternalYamlConfig(resolvedTargetConfigPath, TargetWrapper.class);
		ProcessorConfig explicitProcessorConfig = loadRequiredExternalYamlConfig(resolvedProcessorConfigPath, ProcessorConfig.class, mapper);
		validateSelectedSourceConfigs(explicitSourceWrapper, scenarioName, resolvedSourceConfigPath);
		validateSelectedTargetConfigs(explicitTargetWrapper, scenarioName, resolvedTargetConfigPath);
		List<JobConfig.JobStepConfig> resolvedSteps = resolveExplicitSteps(jobConfig, explicitSourceWrapper, explicitTargetWrapper, explicitProcessorConfig);

		return new ResolvedRuntimeConfig(
				resolvedSourceConfigPath,
				resolvedTargetConfigPath,
				resolvedProcessorConfigPath,
				true,
				scenarioName,
				jobConfigFile.getAbsolutePath(),
				false,
				resolvedSteps
		);
	}

	private List<JobConfig.JobStepConfig> resolveExplicitSteps(JobConfig jobConfig,
	                                                         SourceWrapper sourceWrapper,
	                                                         TargetWrapper targetWrapper,
	                                                         ProcessorConfig processorConfig) {
		List<JobConfig.JobStepConfig> configuredSteps = jobConfig.getSteps();
		if (configuredSteps == null || configuredSteps.isEmpty()) {
			throw new ConfigException("JobConfig must define a non-empty 'steps' list for explicit source-target orchestration.");
		}

		Set<String> stepNames = new HashSet<>();
		Set<String> sourceNames = sourceNames(sourceWrapper);
		Set<String> targetNames = targetNames(targetWrapper);
		List<JobConfig.JobStepConfig> resolvedSteps = new ArrayList<>();

		for (int i = 0; i < configuredSteps.size(); i++) {
			JobConfig.JobStepConfig configuredStep = configuredSteps.get(i);
			if (configuredStep == null) {
				throw new ConfigException("JobConfig contains a null step definition at index " + i);
			}

			String stepName = requireNonBlank(configuredStep.getName(), "JobConfig.steps[" + i + "].name");
			String sourceName = requireNonBlank(configuredStep.getSource(), "JobConfig.steps[" + i + "].source");
			String targetName = requireNonBlank(configuredStep.getTarget(), "JobConfig.steps[" + i + "].target");

			if (!stepNames.add(stepName)) {
				throw new ConfigException("JobConfig contains duplicate step name '" + stepName + "'. Step names must be unique.");
			}
			if (!sourceNames.contains(sourceName)) {
				throw new ConfigException("JobConfig step '" + stepName + "' references unknown source '" + sourceName + "'.");
			}
			if (!targetNames.contains(targetName)) {
				throw new ConfigException("JobConfig step '" + stepName + "' references unknown target '" + targetName + "'.");
			}
			ensureProcessorMappingExists(processorConfig, stepName, sourceName, targetName);

			JobConfig.JobStepConfig resolvedStep = new JobConfig.JobStepConfig();
			resolvedStep.setName(stepName);
			resolvedStep.setSource(sourceName);
			resolvedStep.setTarget(targetName);
			resolvedSteps.add(resolvedStep);
		}

		return List.copyOf(resolvedSteps);
	}

	private List<JobConfig.JobStepConfig> synthesizeDemoSteps(SourceWrapper sourceWrapper,
	                                                        TargetWrapper targetWrapper,
	                                                        ProcessorConfig processorConfig) {
		List<? extends com.etl.config.source.SourceConfig> sources = sourceWrapper.getSources();
		List<com.etl.config.target.TargetConfig> targets = targetWrapper.getTargets();
		if (sources == null || sources.isEmpty()) {
			throw new ConfigException("Demo fallback source configuration contains no sources.");
		}
		if (targets == null || targets.isEmpty()) {
			throw new ConfigException("Demo fallback target configuration contains no targets.");
		}
		if (sources.size() != targets.size()) {
			throw new ConfigException("Demo fallback requires the same number of sources and targets because it synthesizes step definitions by index.");
		}

		List<JobConfig.JobStepConfig> synthesizedSteps = new ArrayList<>();
		for (int i = 0; i < sources.size(); i++) {
			String sourceName = requireNonBlank(sources.get(i).getSourceName(), "sources[" + i + "].sourceName");
			String targetName = requireNonBlank(targets.get(i).getTargetName(), "targets[" + i + "].targetName");
			String stepName = (i + 1) + "-" + sourceName + "-to-" + targetName;
			ensureProcessorMappingExists(processorConfig, stepName, sourceName, targetName);

			JobConfig.JobStepConfig synthesizedStep = new JobConfig.JobStepConfig();
			synthesizedStep.setName(stepName);
			synthesizedStep.setSource(sourceName);
			synthesizedStep.setTarget(targetName);
			synthesizedSteps.add(synthesizedStep);
		}

		return List.copyOf(synthesizedSteps);
	}

	private void validateSelectedSourceConfigs(SourceWrapper sourceWrapper, String scenarioName, String sourceConfigPath) {
		if (sourceWrapper == null || sourceWrapper.getSources() == null) {
			return;
		}

		for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
			if (sourceConfig instanceof RelationalSourceConfig relationalSourceConfig) {
				try {
					relationalSourceConfig.validate();
				} catch (IllegalArgumentException e) {
					logger.error("Invalid relational source configuration for scenario '{}' in {} (source='{}'): {}",
							scenarioName,
							sourceConfigPath,
							defaultName(sourceConfig.getSourceName()),
							e.getMessage());
					throw new ConfigException("Invalid relational source configuration for scenario '" + scenarioName +
							"' in " + sourceConfigPath + " (source='" + defaultName(sourceConfig.getSourceName()) + "'): " + e.getMessage(), e);
				}
			}
		}
	}

	private void validateSelectedTargetConfigs(TargetWrapper targetWrapper, String scenarioName, String targetConfigPath) {
		if (targetWrapper == null || targetWrapper.getTargets() == null) {
			return;
		}

		for (TargetConfig targetConfig : targetWrapper.getTargets()) {
			if (targetConfig instanceof RelationalTargetConfig relationalTargetConfig) {
				try {
					relationalTargetConfig.validate();
				} catch (IllegalArgumentException e) {
					logger.error("Invalid relational target configuration for scenario '{}' in {} (target='{}'): {}",
							scenarioName,
							targetConfigPath,
							defaultName(targetConfig.getTargetName()),
							e.getMessage());
					throw new ConfigException("Invalid relational target configuration for scenario '" + scenarioName +
							"' in " + targetConfigPath + " (target='" + defaultName(targetConfig.getTargetName()) + "'): " + e.getMessage(), e);
				}
			}
		}
	}

	private void ensureProcessorMappingExists(ProcessorConfig processorConfig,
	                                       String stepName,
	                                       String sourceName,
	                                       String targetName) {
		if (processorConfig.getMappings() == null || processorConfig.getMappings().isEmpty()) {
			throw new ConfigException("ProcessorConfig contains no mappings, so step '" + stepName + "' cannot be resolved.");
		}

		boolean exists = processorConfig.getMappings().stream()
				.anyMatch(mapping -> sourceName.equals(mapping.getSource()) && targetName.equals(mapping.getTarget()));
		if (!exists) {
			throw new ConfigException("JobConfig step '" + stepName + "' requires a processor mapping for source '" + sourceName + "' and target '" + targetName + "'.");
		}
	}

	private Set<String> sourceNames(SourceWrapper sourceWrapper) {
		Set<String> names = new HashSet<>();
		if (sourceWrapper.getSources() != null) {
			for (int i = 0; i < sourceWrapper.getSources().size(); i++) {
				String sourceName = requireNonBlank(sourceWrapper.getSources().get(i).getSourceName(), "sources[" + i + "].sourceName");
				names.add(sourceName);
			}
		}
		return names;
	}

	private Set<String> targetNames(TargetWrapper targetWrapper) {
		Set<String> names = new HashSet<>();
		if (targetWrapper.getTargets() != null) {
			for (int i = 0; i < targetWrapper.getTargets().size(); i++) {
				String targetName = requireNonBlank(targetWrapper.getTargets().get(i).getTargetName(), "targets[" + i + "].targetName");
				names.add(targetName);
			}
		}
		return names;
	}

	private String requireNonBlank(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new ConfigException("Missing required property '" + propertyName + "'.");
		}
		return value.trim();
	}

	private String deriveScenarioName(JobConfig jobConfig, Path jobConfigDirectory) {
		String configuredName = jobConfig.getName();
		if (configuredName != null && !configuredName.isBlank()) {
			return configuredName.trim();
		}

		Path directoryName = jobConfigDirectory.getFileName();
		if (directoryName != null) {
			String folderName = directoryName.toString().trim();
			if (!folderName.isBlank()) {
				return folderName;
			}
		}

		return "selected-job";
	}

	private String resolveReferencedPath(Path jobConfigDirectory, String configuredPath, String propertyName) {
		if (configuredPath == null || configuredPath.isBlank()) {
			throw new ConfigException("JobConfig missing required property '" + propertyName + "'");
		}

		Path path = Path.of(configuredPath);
		return path.isAbsolute()
				? path.normalize().toString()
				: jobConfigDirectory.resolve(path).normalize().toString();
	}

	private String defaultName(String configuredName) {
		return configuredName == null || configuredName.isBlank() ? "unnamed" : configuredName.trim();
	}

	private record ResolvedRuntimeConfig(
			String sourceConfigPath,
			String targetConfigPath,
			String processorConfigPath,
			boolean requireExternalConfigs,
			String scenarioName,
			String jobConfigPath,
			boolean demoFallbackMode,
			List<JobConfig.JobStepConfig> steps
	) {
	}


}
