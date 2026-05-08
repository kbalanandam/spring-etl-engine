package com.etl.config;

import com.etl.config.exception.ConfigException;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.FileSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.common.util.ConfigBundlePathAliasResolver;
import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.JobScopedPackageNameResolver;
import com.etl.config.source.validation.SourceValidationContext;
import com.etl.config.source.validation.SourceValidationService;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import com.etl.runtime.job.JobConfigPaths;
import com.etl.runtime.job.JobRunMode;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.runtime.job.JobRuntimeDescriptorAssembler;
import com.etl.processor.transform.TransformEvaluator;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Loads the active source, target, processor, and job-level runtime configuration.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This class is still central to the current 1.4.x runtime, but the next
 * architecture is expected to change generation lifecycle and runtime assembly.
 * Keep this class stable enough to support migration, but do not expand it into the
 * final design center for new architecture work.</p>
 */
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
	private final SourceValidationService sourceValidationService;
	private final ValidationRuleEvaluator validationRuleEvaluator;
	private final TransformEvaluator transformEvaluator;

	public ConfigLoader() {
		this(new SourceValidationService(), new ValidationRuleEvaluator(), new TransformEvaluator());
	}

	public ConfigLoader(SourceValidationService sourceValidationService,
	                  ValidationRuleEvaluator validationRuleEvaluator) {
		this(sourceValidationService, validationRuleEvaluator, new TransformEvaluator());
	}

	@Autowired
	public ConfigLoader(SourceValidationService sourceValidationService,
	                  ValidationRuleEvaluator validationRuleEvaluator,
	                  TransformEvaluator transformEvaluator) {
		this.sourceValidationService = sourceValidationService;
		this.validationRuleEvaluator = validationRuleEvaluator;
		this.transformEvaluator = transformEvaluator;
	}

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
				SourceWrapper sourceWrapper = loadRequiredExternalYamlConfig(runtimeConfig.sourceConfigPath(), SourceWrapper.class);
				normalizeSourceConfigPaths(sourceWrapper, parentDirectory(runtimeConfig.sourceConfigPath()));
				applyDefaultSourcePackages(sourceWrapper, runtimeConfig.scenarioName());
				return sourceWrapper;
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
				TargetWrapper targetWrapper = loadRequiredExternalYamlConfig(runtimeConfig.targetConfigPath(), TargetWrapper.class);
				normalizeTargetConfigPaths(targetWrapper, parentDirectory(runtimeConfig.targetConfigPath()));
				applyDefaultTargetPackages(targetWrapper, runtimeConfig.scenarioName());
				return targetWrapper;
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
			if (runtimeConfig.requireExternalConfigs()) {
				normalizeProcessorConfigPaths(config, parentDirectory(runtimeConfig.processorConfigPath()));
			}
			validateProcessorConfig(config, runtimeConfig.scenarioName(), runtimeConfig.processorConfigPath());
			return config;
		} catch (ConfigException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigException("Failed to load or validate processor config YAML", e);
		}
	}

	@Bean
	RunConfigurationMetadata runConfigurationMetadata(JobRuntimeDescriptor jobRuntimeDescriptor) {
		try {
			return RunConfigurationMetadata.fromJobRuntimeDescriptor(jobRuntimeDescriptor);
		} catch (ConfigException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigException("Failed to resolve runtime configuration metadata", e);
		}
	}

	RunConfigurationMetadata runConfigurationMetadata() {
		return runConfigurationMetadata(
				jobRuntimeDescriptor(
						sourceWrapper(),
						targetWrapper(),
						processorConfig(),
						jobRuntimeDescriptorAssembler()
				)
		);
	}

	@Bean
	JobRuntimeDescriptorAssembler jobRuntimeDescriptorAssembler() {
		return new JobRuntimeDescriptorAssembler();
	}

	@Bean
	JobRuntimeDescriptor jobRuntimeDescriptor(SourceWrapper sourceWrapper,
	                                                  TargetWrapper targetWrapper,
	                                                  ProcessorConfig processorConfig,
	                                                  JobRuntimeDescriptorAssembler assembler) {
		try {
			ResolvedRuntimeConfig runtimeConfig = resolveRuntimeConfig();
			return assembler.assemble(
					runtimeConfig.scenarioName(),
					runtimeConfig.jobConfigPath(),
					runtimeConfig.demoFallbackMode() ? JobRunMode.DEMO_FALLBACK : JobRunMode.EXPLICIT_JOB,
					new JobConfigPaths(
							runtimeConfig.sourceConfigPath(),
							runtimeConfig.targetConfigPath(),
							runtimeConfig.processorConfigPath()
					),
					runtimeConfig.steps(),
					sourceWrapper,
					targetWrapper,
					processorConfig
			);
		} catch (ConfigException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigException("Failed to assemble job runtime descriptor", e);
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
		String resolvedConfiguredPath = ConfigBundlePathAliasResolver.resolveExistingPath(configuredPath);
		File externalFile = new File(resolvedConfiguredPath);
		if (externalFile.exists() && externalFile.isFile()) {
			logger.info("Loading {} from external YAML file: {}", targetType.getSimpleName(), resolvedConfiguredPath);
			if (logger.isDebugEnabled()) {
				logger.debug("YAML content from {}:\n{}", resolvedConfiguredPath, readString(externalFile.toPath()));
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
		String resolvedConfiguredPath = ConfigBundlePathAliasResolver.resolveExistingPath(configuredPath);
		File externalFile = new File(resolvedConfiguredPath);
		if (!externalFile.exists() || !externalFile.isFile()) {
			throw new IOException("Required YAML file not found: " + configuredPath);
		}

		logger.info("Loading {} from job-config referenced YAML file: {}", targetType.getSimpleName(), resolvedConfiguredPath);
		if (logger.isDebugEnabled()) {
			logger.debug("YAML content from {}:\n{}", resolvedConfiguredPath, readString(externalFile.toPath()));
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
			validateProcessorConfig(demoProcessorConfig, "demo-fallback", processorConfigPath);
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
		String resolvedRequestedJobConfigPath = resolveSelectedJobConfigPath(jobConfigPath);
		File jobConfigFile = new File(resolvedRequestedJobConfigPath);
		if (!jobConfigFile.exists() || !jobConfigFile.isFile()) {
			logger.error("Configured job config YAML not found at {}. Explicit job selection never falls back automatically.", jobConfigPath);
			throw new ConfigException("Configured job config YAML not found at " + jobConfigPath);
		}

		logger.info("Loading JobConfig from external YAML file: {}", resolvedRequestedJobConfigPath);
		JobConfig jobConfig = mapper.readValue(jobConfigFile, JobConfig.class);
		Path jobConfigDirectory = jobConfigFile.getAbsoluteFile().getParentFile().toPath();
		String scenarioName = deriveScenarioName(jobConfig, jobConfigDirectory);
		String resolvedSourceConfigPath = resolveReferencedPath(jobConfigDirectory, jobConfig.getSourceConfigPath(), "sourceConfigPath");
		String resolvedTargetConfigPath = resolveReferencedPath(jobConfigDirectory, jobConfig.getTargetConfigPath(), "targetConfigPath");
		String resolvedProcessorConfigPath = resolveReferencedPath(jobConfigDirectory, jobConfig.getProcessorConfigPath(), "processorConfigPath");
		SourceWrapper explicitSourceWrapper = loadRequiredExternalYamlConfig(resolvedSourceConfigPath, SourceWrapper.class);
		TargetWrapper explicitTargetWrapper = loadRequiredExternalYamlConfig(resolvedTargetConfigPath, TargetWrapper.class);
		ProcessorConfig explicitProcessorConfig = loadRequiredExternalYamlConfig(resolvedProcessorConfigPath, ProcessorConfig.class, mapper);
		normalizeSourceConfigPaths(explicitSourceWrapper, parentDirectory(resolvedSourceConfigPath));
		normalizeTargetConfigPaths(explicitTargetWrapper, parentDirectory(resolvedTargetConfigPath));
		applyJobScopedPackageDefaults(explicitSourceWrapper, explicitTargetWrapper, scenarioName);
		normalizeProcessorConfigPaths(explicitProcessorConfig, parentDirectory(resolvedProcessorConfigPath));
		sourceValidationService.validateSelectedSources(
				explicitSourceWrapper,
				new SourceValidationContext(scenarioName, resolvedSourceConfigPath)
		);
		validateSelectedTargetConfigs(explicitTargetWrapper, scenarioName, resolvedTargetConfigPath);
		validateProcessorConfig(explicitProcessorConfig, scenarioName, resolvedProcessorConfigPath);
		List<JobConfig.JobStepConfig> resolvedSteps = resolveExplicitSteps(jobConfig, explicitSourceWrapper, explicitTargetWrapper, explicitProcessorConfig);
		validateSelectedGeneratedModelClasses(explicitSourceWrapper, explicitTargetWrapper, resolvedSteps);

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

	private String resolveSelectedJobConfigPath(String configuredJobConfigPath) {
		String resolvedPath = ConfigBundlePathAliasResolver.resolveExistingPath(configuredJobConfigPath);
		if (configuredJobConfigPath != null
				&& !configuredJobConfigPath.isBlank()
				&& !configuredJobConfigPath.trim().equals(resolvedPath)) {
			logger.info("Resolved config bundle alias '{}' -> '{}'.", configuredJobConfigPath.trim(), resolvedPath);
		}
		return resolvedPath;
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

	private void validateSelectedGeneratedModelClasses(SourceWrapper sourceWrapper,
	                                                TargetWrapper targetWrapper,
	                                                List<JobConfig.JobStepConfig> resolvedSteps) {
		if (resolvedSteps == null || resolvedSteps.isEmpty()) {
			return;
		}

		java.util.Map<String, SourceConfig> sourcesByName = new java.util.LinkedHashMap<>();
		if (sourceWrapper.getSources() != null) {
			for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
				sourcesByName.put(sourceConfig.getSourceName(), sourceConfig);
			}
		}

		java.util.Map<String, TargetConfig> targetsByName = new java.util.LinkedHashMap<>();
		if (targetWrapper.getTargets() != null) {
			for (TargetConfig targetConfig : targetWrapper.getTargets()) {
				targetsByName.put(targetConfig.getTargetName(), targetConfig);
			}
		}

		for (JobConfig.JobStepConfig step : resolvedSteps) {
			SourceConfig sourceConfig = sourcesByName.get(step.getSource());
			TargetConfig targetConfig = targetsByName.get(step.getTarget());
			if (sourceConfig != null) {
				GeneratedModelClassResolver.requireSourceModelClassesAvailable(sourceConfig);
			}
			if (targetConfig != null) {
				GeneratedModelClassResolver.requireTargetModelClassesAvailable(targetConfig);
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
		return JobScopedPackageNameResolver.deriveJobName(jobConfig, jobConfigDirectory);
	}

	private void applyJobScopedPackageDefaults(SourceWrapper sourceWrapper,
	                                         TargetWrapper targetWrapper,
	                                         String scenarioName) {
		applyDefaultSourcePackages(sourceWrapper, scenarioName);
		applyDefaultTargetPackages(targetWrapper, scenarioName);
	}

	private void applyDefaultSourcePackages(SourceWrapper sourceWrapper, String scenarioName) {
		if (sourceWrapper == null || sourceWrapper.getSources() == null) {
			return;
		}

		String defaultSourcePackage = JobScopedPackageNameResolver.resolveSourcePackage(scenarioName);
		for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
			if (!hasText(sourceConfig.getPackageName())) {
				sourceConfig.setPackageName(defaultSourcePackage);
			}
		}
	}

	private void applyDefaultTargetPackages(TargetWrapper targetWrapper, String scenarioName) {
		if (targetWrapper == null || targetWrapper.getTargets() == null) {
			return;
		}

		List<TargetConfig> defaultedTargets = new ArrayList<>();
		for (TargetConfig targetConfig : targetWrapper.getTargets()) {
			defaultedTargets.add(applyDefaultTargetPackage(targetConfig, scenarioName));
		}
		targetWrapper.setTargets(List.copyOf(defaultedTargets));
	}

	private TargetConfig applyDefaultTargetPackage(TargetConfig targetConfig, String scenarioName) {
		if (targetConfig == null || hasText(targetConfig.getPackageName())) {
			return targetConfig;
		}

		String defaultTargetPackage = JobScopedPackageNameResolver.resolveTargetPackage(scenarioName);
		if (targetConfig instanceof CsvTargetConfig csvTargetConfig) {
			return new CsvTargetConfig(
					csvTargetConfig.getTargetName(),
					defaultTargetPackage,
					copyColumns(csvTargetConfig.getFields()),
					csvTargetConfig.getFilePath(),
					csvTargetConfig.getDelimiter(),
					csvTargetConfig.isIncludeHeader()
			);
		}
		if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
			return new XmlTargetConfig(
					xmlTargetConfig.getTargetName(),
					defaultTargetPackage,
					copyColumns(xmlTargetConfig.getFields()),
					xmlTargetConfig.getFilePath(),
					xmlTargetConfig.getRootElement(),
					xmlTargetConfig.getRecordElement(),
					xmlTargetConfig.getModelDefinitionPath()
			);
		}
		if (targetConfig instanceof RelationalTargetConfig relationalTargetConfig) {
			return new RelationalTargetConfig(
					relationalTargetConfig.getTargetName(),
					defaultTargetPackage,
					copyColumns(relationalTargetConfig.getFields()),
					relationalTargetConfig.getConnection(),
					relationalTargetConfig.getTable(),
					relationalTargetConfig.getSchema(),
					relationalTargetConfig.getWriteMode().name(),
					relationalTargetConfig.getBatchSize()
			);
		}
		return targetConfig;
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

	private Path parentDirectory(String resolvedConfigPath) {
		Path absolutePath = Path.of(resolvedConfigPath).toAbsolutePath().normalize();
		Path parent = absolutePath.getParent();
		return parent == null ? absolutePath : parent;
	}

	private void normalizeSourceConfigPaths(SourceWrapper sourceWrapper, Path configDirectory) {
		if (sourceWrapper == null || sourceWrapper.getSources() == null) {
			return;
		}

		for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
			if (sourceConfig instanceof FileSourceConfig fileSourceConfig) {
				fileSourceConfig.setFilePath(resolveScenarioPath(configDirectory, fileSourceConfig.getFilePath()));
				if (fileSourceConfig.getArchiveConfig() != null) {
					fileSourceConfig.getArchiveConfig().setSuccessPath(resolveScenarioPath(configDirectory, fileSourceConfig.getArchiveConfig().getSuccessPath()));
				}
			}
			if (sourceConfig instanceof CsvSourceConfig csvSourceConfig) {
				if (csvSourceConfig.getValidation() != null) {
					csvSourceConfig.getValidation().setRejectPath(resolveScenarioPath(configDirectory, csvSourceConfig.getValidation().getRejectPath()));
				}
			}
			if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig) {
				xmlSourceConfig.setModelDefinitionPath(resolveScenarioPath(configDirectory, xmlSourceConfig.getModelDefinitionPath()));
				if (xmlSourceConfig.getValidation() != null) {
					xmlSourceConfig.getValidation().setRejectPath(resolveScenarioPath(configDirectory, xmlSourceConfig.getValidation().getRejectPath()));
				}
			}
		}
	}

	private void normalizeTargetConfigPaths(TargetWrapper targetWrapper, Path configDirectory) {
		if (targetWrapper == null || targetWrapper.getTargets() == null) {
			return;
		}

		List<TargetConfig> normalizedTargets = new ArrayList<>();
		for (TargetConfig targetConfig : targetWrapper.getTargets()) {
			normalizedTargets.add(normalizeTargetConfig(targetConfig, configDirectory));
		}
		targetWrapper.setTargets(List.copyOf(normalizedTargets));
	}

	private TargetConfig normalizeTargetConfig(TargetConfig targetConfig, Path configDirectory) {
		if (targetConfig instanceof CsvTargetConfig csvTargetConfig) {
			return new CsvTargetConfig(
					csvTargetConfig.getTargetName(),
					csvTargetConfig.getPackageName(),
					copyColumns(csvTargetConfig.getFields()),
					resolveScenarioPath(configDirectory, csvTargetConfig.getFilePath()),
					csvTargetConfig.getDelimiter(),
					csvTargetConfig.isIncludeHeader()
			);
		}
		if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
			return new XmlTargetConfig(
					xmlTargetConfig.getTargetName(),
					xmlTargetConfig.getPackageName(),
					copyColumns(xmlTargetConfig.getFields()),
					resolveScenarioPath(configDirectory, xmlTargetConfig.getFilePath()),
					xmlTargetConfig.getRootElement(),
					xmlTargetConfig.getRecordElement(),
					resolveScenarioPath(configDirectory, xmlTargetConfig.getModelDefinitionPath())
			);
		}
		if (targetConfig instanceof RelationalTargetConfig) {
			return targetConfig;
		}
		return targetConfig;
	}

	private void normalizeProcessorConfigPaths(ProcessorConfig processorConfig, Path configDirectory) {
		if (processorConfig == null || processorConfig.getRejectHandling() == null) {
			return;
		}
		processorConfig.getRejectHandling().setOutputPath(
				resolveScenarioPath(configDirectory, processorConfig.getRejectHandling().getOutputPath())
		);
	}

	private List<ColumnConfig> copyColumns(List<? extends FieldDefinition> fields) {
		if (fields == null || fields.isEmpty()) {
			return List.of();
		}

		List<ColumnConfig> columns = new ArrayList<>();
		for (FieldDefinition field : fields) {
			ColumnConfig column = new ColumnConfig();
			column.setName(field.getName());
			column.setType(field.getType());
			columns.add(column);
		}
		return List.copyOf(columns);
	}

	private String resolveScenarioPath(Path configDirectory, String configuredPath) {
		if (configuredPath == null || configuredPath.isBlank()) {
			return configuredPath;
		}

		Path path = Path.of(configuredPath.trim());
		if (path.isAbsolute()) {
			return path.normalize().toString();
		}

		if (isWorkingDirectoryRelativeCompatibilityPath(path)) {
			return path.toAbsolutePath().normalize().toString();
		}

		return configDirectory.resolve(path).normalize().toString();
	}

	private boolean isWorkingDirectoryRelativeCompatibilityPath(Path path) {
		if (path.getNameCount() == 0) {
			return false;
		}

		String firstSegment = path.getName(0).toString();
		return path.isAbsolute()
				|| "src".equals(firstSegment)
				|| "target".equals(firstSegment);
	}

	private String defaultName(String configuredName) {
		return configuredName == null || configuredName.isBlank() ? "unnamed" : configuredName.trim();
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private void validateProcessorConfig(ProcessorConfig config, String scenarioName, String resolvedProcessorConfigPath) {
		try {
			if (config.getMappings() == null || config.getMappings().isEmpty()) {
				throw new IllegalStateException("No entity mappings found in processor YAML");
			}

			for (int i = 0; i < config.getMappings().size(); i++) {
				ProcessorConfig.EntityMapping entityMapping = config.getMappings().get(i);
				logger.debug("Mapping {}: source={}, target={}", i, entityMapping.getSource(), entityMapping.getTarget());
			}
			logger.debug("Validating EntityMapping size: {}", config.getMappings().size());

			for (ProcessorConfig.EntityMapping entityMapping : config.getMappings()) {
				if (entityMapping.getSource() == null || entityMapping.getSource().isEmpty()) {
					throw new IllegalStateException("EntityMapping missing 'source' property: " + entityMapping);
				}
				if (entityMapping.getTarget() == null || entityMapping.getTarget().isEmpty()) {
					throw new IllegalStateException("EntityMapping missing 'target' property: " + entityMapping);
				}
				if (entityMapping.getFields() == null || entityMapping.getFields().isEmpty()) {
					throw new IllegalStateException("EntityMapping for source=" + entityMapping.getSource() + " and target=" + entityMapping.getTarget() + " has no field mappings");
				}

				for (ProcessorConfig.FieldMapping fieldMapping : entityMapping.getFields()) {
					if ((fieldMapping.getFrom() == null || fieldMapping.getFrom().isBlank()) && !allowsDerivedFieldWithoutSource(fieldMapping)) {
						throw new IllegalStateException("FieldMapping missing 'from' in entity " + entityMapping.getSource()
								+ ". Omit 'from' only when the first transform is type 'expression'.");
					}
					if (fieldMapping.getTo() == null || fieldMapping.getTo().isEmpty()) {
						throw new IllegalStateException("FieldMapping missing 'to' in entity " + entityMapping.getSource());
					}
					validateFieldTransforms(entityMapping, fieldMapping);
					validateFieldRules(config, entityMapping, fieldMapping);
				}
			}

			validateRejectHandling(config);
			logger.info("Processor configuration loaded and validated successfully from YAML");
		} catch (ConfigException e) {
			throw e;
		} catch (IllegalArgumentException | IllegalStateException e) {
			throw new ConfigException("Invalid processor configuration for scenario '"
					+ defaultName(scenarioName) + "' in " + resolvedProcessorConfigPath + ": " + e.getMessage(), e);
		}
	}

	private void validateRejectHandling(ProcessorConfig config) {
		ProcessorConfig.RejectHandling rejectHandling = config.getRejectHandling();
		if (rejectHandling == null || !rejectHandling.isEnabled()) {
			return;
		}

		if (rejectHandling.getOutputPath() == null || rejectHandling.getOutputPath().isBlank()) {
			throw new IllegalStateException("ProcessorConfig.rejectHandling.enabled=true requires a non-blank 'outputPath'.");
		}
	}

	private void validateFieldRules(ProcessorConfig config,
	                             ProcessorConfig.EntityMapping entityMapping,
	                             ProcessorConfig.FieldMapping fieldMapping) {
		if (fieldMapping.getRules() == null || fieldMapping.getRules().isEmpty()) {
			return;
		}

		for (int i = 0; i < fieldMapping.getRules().size(); i++) {
			ProcessorConfig.FieldRule rule = fieldMapping.getRules().get(i);
			validateRuleFailureAction(config, entityMapping, fieldMapping, rule);
			validationRuleEvaluator.validateConfiguration(entityMapping, fieldMapping, rule);
		}
	}

	private void validateFieldTransforms(ProcessorConfig.EntityMapping entityMapping,
	                                  ProcessorConfig.FieldMapping fieldMapping) {
		if (fieldMapping.getTransforms() == null || fieldMapping.getTransforms().isEmpty()) {
			return;
		}

		if ((fieldMapping.getFrom() == null || fieldMapping.getFrom().isBlank())
				&& !"expression".equalsIgnoreCase(fieldMapping.getTransforms().get(0).getType())) {
			throw new IllegalStateException("FieldMapping for entity " + entityMapping.getSource() + " -> "
					+ entityMapping.getTarget() + " field '" + fieldMapping.getTo()
					+ "' omits 'from' but its first transform is not type 'expression'.");
		}

		for (int i = 0; i < fieldMapping.getTransforms().size(); i++) {
			ProcessorConfig.FieldTransform transform = fieldMapping.getTransforms().get(i);
			transformEvaluator.validateConfiguration(entityMapping, fieldMapping, transform);
		}
	}

	private boolean allowsDerivedFieldWithoutSource(ProcessorConfig.FieldMapping fieldMapping) {
		return fieldMapping != null
				&& fieldMapping.getTransforms() != null
				&& !fieldMapping.getTransforms().isEmpty()
				&& fieldMapping.getTransforms().get(0) != null
				&& "expression".equalsIgnoreCase(fieldMapping.getTransforms().get(0).getType());
	}

	private void validateRuleFailureAction(ProcessorConfig config,
	                                    ProcessorConfig.EntityMapping entityMapping,
	                                    ProcessorConfig.FieldMapping fieldMapping,
	                                    ProcessorConfig.FieldRule rule) {
		if (rule == null || rule.getOnFailure() == null || rule.getOnFailure().isBlank()) {
			return;
		}

		String normalizedAction = rule.getOnFailure().trim();
		if (!"failStep".equalsIgnoreCase(normalizedAction) && !"rejectRecord".equalsIgnoreCase(normalizedAction)) {
			throw new IllegalStateException("FieldMapping rule '" + rule.getType() + "' for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom()
					+ "' uses unsupported onFailure='" + normalizedAction + "'. Supported values are failStep or rejectRecord.");
		}

		if ("rejectRecord".equalsIgnoreCase(normalizedAction)
				&& (config.getRejectHandling() == null || !config.getRejectHandling().isEnabled())) {
			throw new IllegalStateException("FieldMapping rule '" + rule.getType() + "' for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom()
					+ "' uses onFailure=rejectRecord but rejectHandling.enabled is not true.");
		}
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
