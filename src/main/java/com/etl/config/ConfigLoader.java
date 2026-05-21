package com.etl.config;

import com.etl.config.exception.ConfigException;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.FileSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.common.util.ConfigPackageNamePropertyValidator;
import com.etl.common.util.ConfigBundlePathAliasResolver;
import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.JobScopedPackageNameResolver;
import com.etl.common.util.SelectedJobNamingValidator;
import com.etl.config.source.validation.SourceValidationContext;
import com.etl.config.source.validation.SourceValidationService;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.JsonTargetConfig;
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
import java.nio.charset.StandardCharsets;
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

    @Bean(name = "sourceWrapper")
	public SourceWrapper createSourceWrapper() {
		try {
			return loadSourceWrapperForRuntime(resolveRuntimeConfig());
		} catch (Exception e) {
			if (e instanceof ConfigException configException) {
				throw configException;
			}
			throw new ConfigException("Failed to load source config YAML", e);
		}
	}

	SourceWrapper sourceWrapper() {
		return createSourceWrapper();
	}

	@Bean
	public TargetWrapper targetWrapper() {
		try {
			return loadTargetWrapperForRuntime(resolveRuntimeConfig());
		} catch (Exception e) {
			if (e instanceof ConfigException configException) {
				throw configException;
			}

			throw new ConfigException("Failed to load target config YAML", e);
		}
	}

	@Bean
	public ProcessorConfig processorConfig() {
		try {
			return loadProcessorConfigForRuntime(resolveRuntimeConfig());
		} catch (Exception e) {
			if (e instanceof ConfigException configException) {
				throw configException;
			}
			throw new ConfigException("Failed to load or validate processor config YAML", e);
		}
	}

	@Bean
	public RunConfigurationMetadata runConfigurationMetadata(JobRuntimeDescriptor jobRuntimeDescriptor) {
		try {
			return RunConfigurationMetadata.fromJobRuntimeDescriptor(jobRuntimeDescriptor);
		} catch (Exception e) {
			if (e instanceof ConfigException configException) {
				throw configException;
			}
			throw new ConfigException("Failed to resolve runtime configuration metadata", e);
		}
	}

	RunConfigurationMetadata buildRunConfigurationMetadata() {
		try {
			ResolvedRuntimeConfig runtimeConfig = resolveRuntimeConfig();
			SourceWrapper sourceWrapper = loadSourceWrapperForRuntime(runtimeConfig);
			TargetWrapper targetWrapper = loadTargetWrapperForRuntime(runtimeConfig);
			ProcessorConfig processorConfig = loadProcessorConfigForRuntime(runtimeConfig);
			JobRuntimeDescriptor descriptor = buildJobRuntimeDescriptor(
					runtimeConfig,
					sourceWrapper,
					targetWrapper,
					processorConfig,
					new JobRuntimeDescriptorAssembler()
			);
			return RunConfigurationMetadata.fromJobRuntimeDescriptor(descriptor);
		} catch (Exception e) {
			if (e instanceof ConfigException configException) {
				throw configException;
			}
			throw new ConfigException("Failed to resolve runtime configuration metadata", e);
		}
	}

	@Bean(name = "jobRuntimeDescriptorAssembler")
	public JobRuntimeDescriptorAssembler createJobRuntimeDescriptorAssembler() {
		return new JobRuntimeDescriptorAssembler();
	}


	@Bean
	public JobRuntimeDescriptor jobRuntimeDescriptor(SourceWrapper sourceWrapper,
	                                                  TargetWrapper targetWrapper,
	                                                  ProcessorConfig processorConfig,
	                                                  JobRuntimeDescriptorAssembler assembler) {
		try {
			return buildJobRuntimeDescriptor(resolveRuntimeConfig(), sourceWrapper, targetWrapper, processorConfig, assembler);
		} catch (IllegalArgumentException | IllegalStateException e) {
			ResolvedRuntimeConfig runtimeConfig = cachedRuntimeConfig;
			throw new ConfigException("Invalid runtime descriptor configuration for scenario '"
					+ defaultName(runtimeConfig == null ? null : runtimeConfig.scenarioName()) + "' in "
					+ defaultJobPath(runtimeConfig == null ? null : runtimeConfig.jobConfigPath()) + ": " + e.getMessage(), e);
		} catch (Exception e) {
			if (e instanceof ConfigException configException) {
				throw configException;
			}
			throw new ConfigException("Failed to assemble job runtime descriptor", e);
		}
	}

	private SourceWrapper loadSourceWrapperForRuntime(ResolvedRuntimeConfig runtimeConfig) throws IOException {
		if (runtimeConfig.requireExternalConfigs()) {
			SourceWrapper sourceWrapper = loadRequiredExternalYamlConfig(
					runtimeConfig.sourceConfigPath(),
					SourceWrapper.class,
					buildYamlMapper(),
					selectedJobSourcePackageContract(runtimeConfig.scenarioName())
			);
			normalizeSourceConfigPaths(sourceWrapper, parentDirectory(runtimeConfig.sourceConfigPath()));
			applyDefaultSourcePackages(sourceWrapper, runtimeConfig.scenarioName());
			return sourceWrapper;
		}

		SourceWrapper sourceWrapper = loadYamlConfig(
				runtimeConfig.sourceConfigPath(),
				"source-config.yaml",
				SourceWrapper.class,
				buildYamlMapper(),
				directSourcePackageContract()
		);
		applyDirectConfigSourcePackages(sourceWrapper);
		return sourceWrapper;
	}

	private TargetWrapper loadTargetWrapperForRuntime(ResolvedRuntimeConfig runtimeConfig) throws IOException {
		if (runtimeConfig.requireExternalConfigs()) {
			TargetWrapper targetWrapper = loadRequiredExternalYamlConfig(
					runtimeConfig.targetConfigPath(),
					TargetWrapper.class,
					buildYamlMapper(),
					selectedJobTargetPackageContract(runtimeConfig.scenarioName())
			);
			normalizeTargetConfigPaths(targetWrapper, parentDirectory(runtimeConfig.targetConfigPath()));
			applyDefaultTargetPackages(targetWrapper, runtimeConfig.scenarioName());
			return targetWrapper;
		}

		TargetWrapper targetWrapper = loadYamlConfig(
				runtimeConfig.targetConfigPath(),
				"target-config.yaml",
				TargetWrapper.class,
				buildYamlMapper(),
				directTargetPackageContract()
		);
		applyDirectConfigTargetPackages(targetWrapper);
		return targetWrapper;
	}

	private ProcessorConfig loadProcessorConfigForRuntime(ResolvedRuntimeConfig runtimeConfig) throws IOException {
		ObjectMapper mapper = buildYamlMapper();
		ProcessorConfig config = runtimeConfig.requireExternalConfigs()
				? loadRequiredExternalYamlConfig(runtimeConfig.processorConfigPath(), ProcessorConfig.class, mapper)
				: loadYamlConfig(runtimeConfig.processorConfigPath(), "processor-config.yaml", ProcessorConfig.class, mapper);
		if (runtimeConfig.requireExternalConfigs()) {
			normalizeProcessorConfigPaths(config, parentDirectory(runtimeConfig.processorConfigPath()));
		}
		validateProcessorConfig(config, runtimeConfig.scenarioName(), runtimeConfig.processorConfigPath());
		return config;
	}

	private JobRuntimeDescriptor buildJobRuntimeDescriptor(ResolvedRuntimeConfig runtimeConfig,
	                                                     SourceWrapper sourceWrapper,
	                                                     TargetWrapper targetWrapper,
	                                                     ProcessorConfig processorConfig,
	                                                     JobRuntimeDescriptorAssembler assembler) {
		// Build the observability/runtime descriptor from the same selected config contract
		// that drives actual execution so logging and execution describe the same scenario.
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
	}

	private static ObjectMapper buildYamlMapper() {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.findAndRegisterModules();
		return mapper;
	}

	private <T> T loadYamlConfig(String configuredPath, String fallbackClasspathResource, Class<T> targetType) throws IOException {
		return loadYamlConfig(configuredPath, fallbackClasspathResource, targetType, buildYamlMapper(), null);
	}

	private <T> T loadRequiredExternalYamlConfig(String configuredPath, Class<T> targetType) throws IOException {
		return loadRequiredExternalYamlConfig(configuredPath, targetType, buildYamlMapper(), null);
	}

	private <T> T loadYamlConfig(String configuredPath, String fallbackClasspathResource, Class<T> targetType, ObjectMapper mapper) throws IOException {
		return loadYamlConfig(configuredPath, fallbackClasspathResource, targetType, mapper, null);
	}

	private <T> T loadYamlConfig(String configuredPath,
	                           String fallbackClasspathResource,
	                           Class<T> targetType,
	                           ObjectMapper mapper,
	                           PackageNameContract packageNameContract) throws IOException {
		String resolvedConfiguredPath = configuredPath == null ? null : configuredPath.trim();
		File externalFile = resolvedConfiguredPath == null || resolvedConfiguredPath.isBlank()
				? null
				: new File(resolvedConfiguredPath);
		if (externalFile != null && externalFile.exists() && externalFile.isFile()) {
			logger.info("Loading {} from external YAML file: {}", targetType.getSimpleName(), resolvedConfiguredPath);
			String yamlContent = readString(externalFile.toPath());
			if (logger.isDebugEnabled()) {
				logger.debug("YAML content from {}:\n{}", resolvedConfiguredPath, yamlContent);
			}
			validateUnsupportedPackageNameProperties(mapper, yamlContent, resolvedConfiguredPath, targetType, packageNameContract);
			return mapper.readValue(yamlContent, targetType);
		}

		logger.warn("Configured YAML file not found at {}. Falling back to classpath resource: {}", configuredPath, fallbackClasspathResource);
		ClassPathResource classPathResource = new ClassPathResource(fallbackClasspathResource);
		if (!classPathResource.exists()) {
			throw new IOException("Fallback classpath resource not found: " + fallbackClasspathResource);
		}

		try (InputStream inputStream = classPathResource.getInputStream()) {
			String yamlContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			validateUnsupportedPackageNameProperties(mapper, yamlContent, fallbackClasspathResource, targetType, packageNameContract);
			return mapper.readValue(yamlContent, targetType);
		}
	}

	private <T> T loadRequiredExternalYamlConfig(String configuredPath, Class<T> targetType, ObjectMapper mapper) throws IOException {
		return loadRequiredExternalYamlConfig(configuredPath, targetType, mapper, null);
	}

	private <T> T loadRequiredExternalYamlConfig(String configuredPath,
	                                           Class<T> targetType,
	                                           ObjectMapper mapper,
	                                           PackageNameContract packageNameContract) throws IOException {
		String resolvedConfiguredPath = configuredPath == null ? null : configuredPath.trim();
		File externalFile = resolvedConfiguredPath == null || resolvedConfiguredPath.isBlank()
				? null
				: new File(resolvedConfiguredPath);
		if (externalFile == null || !externalFile.exists() || !externalFile.isFile()) {
			throw new IOException("Required YAML file not found: " + configuredPath);
		}

		logger.info("Loading {} from job-config referenced YAML file: {}", targetType.getSimpleName(), resolvedConfiguredPath);
		String yamlContent = readString(externalFile.toPath());
		if (logger.isDebugEnabled()) {
			logger.debug("YAML content from {}:\n{}", resolvedConfiguredPath, yamlContent);
		}
		validateUnsupportedPackageNameProperties(mapper, yamlContent, resolvedConfiguredPath, targetType, packageNameContract);
		return mapper.readValue(yamlContent, targetType);
	}

	private ResolvedRuntimeConfig resolveRuntimeConfig() throws IOException {
		// Runtime config is resolved once per application startup because all downstream beans
		// must agree on one selected scenario or one explicit demo-fallback contract.
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
		// The shipped runtime chooses exactly one selected job bundle per run. When no explicit
		// job-config is provided, demo fallback is allowed only if operators enabled it on purpose.
		if (jobConfigPath == null || jobConfigPath.isBlank()) {
			if (!allowDemoFallback) {
				logger.error("Missing required runtime property 'etl.config.job'. Demo fallback is disabled, so startup cannot continue.");
				throw new ConfigException(
						"Missing required runtime property 'etl.config.job'. " +
						"Set it to a job-config.yaml path, or enable demo fallback with 'etl.config.allow-demo-fallback=true' for local/demo runs."
				);
			}

			logger.warn("No 'etl.config.job' was provided. Demo fallback is enabled, so the runtime will use direct config paths and may fall back to bundled classpath YAML resources. This mode is intended for local/demo use only.");
			SourceWrapper demoSourceWrapper = loadYamlConfig(sourceConfigPath, "source-config.yaml", SourceWrapper.class, buildYamlMapper(), directSourcePackageContract());
			TargetWrapper demoTargetWrapper = loadYamlConfig(targetConfigPath, "target-config.yaml", TargetWrapper.class, buildYamlMapper(), directTargetPackageContract());
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
		String scenarioName = requireExplicitJobName(jobConfig, jobConfigFile.toPath());
		requireActiveSelectedJob(jobConfig, scenarioName, jobConfigFile.toPath());
		String resolvedSourceConfigPath = resolveReferencedPath(jobConfigDirectory, jobConfig.getSourceConfigPath(), "sourceConfigPath");
		String resolvedTargetConfigPath = resolveReferencedPath(jobConfigDirectory, jobConfig.getTargetConfigPath(), "targetConfigPath");
		String resolvedProcessorConfigPath = resolveReferencedPath(jobConfigDirectory, jobConfig.getProcessorConfigPath(), "processorConfigPath");
		SourceWrapper explicitSourceWrapper = loadRequiredExternalYamlConfig(
				resolvedSourceConfigPath,
				SourceWrapper.class,
				mapper,
				selectedJobSourcePackageContract(scenarioName)
		);
		TargetWrapper explicitTargetWrapper = loadRequiredExternalYamlConfig(
				resolvedTargetConfigPath,
				TargetWrapper.class,
				mapper,
				selectedJobTargetPackageContract(scenarioName)
		);
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
		SelectedJobNamingValidator.validate(scenarioName, explicitSourceWrapper, explicitTargetWrapper, resolvedSteps);
		validateSelectedGeneratedModelClasses(explicitSourceWrapper, explicitTargetWrapper, resolvedSteps, scenarioName, jobConfigFile.getAbsolutePath());

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

	private static String requireExplicitJobName(JobConfig jobConfig, Path jobConfigPath) {
		try {
			return JobScopedPackageNameResolver.requireExplicitJobName(jobConfig, jobConfigPath);
		} catch (IllegalStateException e) {
			throw new ConfigException(e.getMessage(), e);
		}
	}

	private static void requireActiveSelectedJob(JobConfig jobConfig, String scenarioName, Path jobConfigPath) {
		if (jobConfig != null && Boolean.FALSE.equals(jobConfig.getIsActive())) {
			throw new ConfigException("Selected job '" + defaultName(scenarioName) + "' is inactive in "
					+ jobConfigPath.toAbsolutePath().normalize()
					+ ". Set 'isActive: true' or select a different job-config.yaml.");
		}
	}

	private static String resolveSelectedJobConfigPath(String configuredJobConfigPath) {
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
		List<? extends SourceConfig> sources = sourceWrapper.getSources();
		List<TargetConfig> targets = requireDemoFallbackTargets(targetWrapper, sources);

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

	private static List<TargetConfig> requireDemoFallbackTargets(TargetWrapper targetWrapper, List<? extends SourceConfig> sources) {
		List<TargetConfig> targets = targetWrapper.getTargets();
		if (sources == null || sources.isEmpty()) {
			throw new ConfigException("Demo fallback source configuration contains no sources.");
		}
		if (targets == null || targets.isEmpty()) {
			throw new ConfigException("Demo fallback target configuration contains no targets.");
		}
		if (sources.size() != targets.size()) {
			throw new ConfigException("Demo fallback requires the same number of sources and targets because it synthesizes step definitions by index.");
		}
		return targets;
	}

	private void validateSelectedGeneratedModelClasses(SourceWrapper sourceWrapper,
	                                                TargetWrapper targetWrapper,
	                                                List<JobConfig.JobStepConfig> resolvedSteps,
	                                                String scenarioName,
	                                                String jobConfigPath) {
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
			try {
				if (sourceConfig != null) {
					GeneratedModelClassResolver.requireSourceModelClassesAvailable(sourceConfig);
				}
				if (targetConfig != null) {
					GeneratedModelClassResolver.requireTargetModelClassesAvailable(targetConfig);
				}
			} catch (IllegalArgumentException | IllegalStateException e) {
				throw new ConfigException("Invalid generated model configuration for scenario '"
						+ defaultName(scenarioName) + "' in " + defaultJobPath(jobConfigPath)
						+ " (step='" + defaultName(step.getName()) + "', source='" + defaultName(step.getSource())
						+ "', target='" + defaultName(step.getTarget()) + "'): " + e.getMessage(), e);
			}
		}
	}

	private static String defaultJobPath(String jobPath) {
		return jobPath == null || jobPath.isBlank() ? "selected job-config" : jobPath.trim();
	}

	private static PackageNameContract selectedJobSourcePackageContract(String scenarioName) {
		return new PackageNameContract(
				"Selected job '" + defaultName(scenarioName) + "'",
				"the selected job",
				JobScopedPackageNameResolver.resolveSourcePackage(scenarioName),
				"from job-config.yaml -> name"
		);
	}

	private static PackageNameContract selectedJobTargetPackageContract(String scenarioName) {
		return new PackageNameContract(
				"Selected job '" + defaultName(scenarioName) + "'",
				"the selected job",
				JobScopedPackageNameResolver.resolveTargetPackage(scenarioName),
				"from job-config.yaml -> name"
		);
	}

	private static PackageNameContract directSourcePackageContract() {
		return new PackageNameContract(
				"Direct-config runtime",
				"runtime",
				"com.etl.model.source",
				"internally"
		);
	}

	private static PackageNameContract directTargetPackageContract() {
		return new PackageNameContract(
				"Direct-config runtime",
				"runtime",
				"com.etl.model.target",
				"internally"
		);
	}

	private static <T> void validateUnsupportedPackageNameProperties(ObjectMapper mapper,
	                                                               String yamlContent,
	                                                               String configLocation,
	                                                               Class<T> targetType,
	                                                               PackageNameContract packageNameContract) throws IOException {
		if (packageNameContract == null) {
			return;
		}

		if (SourceWrapper.class.equals(targetType)) {
			ConfigPackageNamePropertyValidator.requireNoSourcePackageNameProperties(
					mapper,
					yamlContent,
					configLocation,
					packageNameContract.contextDescription,
					packageNameContract.derivationOwnerDescription,
					packageNameContract.derivedPackageName,
					packageNameContract.derivationSourceDescription
			);
		}

		if (TargetWrapper.class.equals(targetType)) {
			ConfigPackageNamePropertyValidator.requireNoTargetPackageNameProperties(
					mapper,
					yamlContent,
					configLocation,
					packageNameContract.contextDescription,
					packageNameContract.derivationOwnerDescription,
					packageNameContract.derivedPackageName,
					packageNameContract.derivationSourceDescription
			);
		}
	}

	private static final class PackageNameContract {
		private final String contextDescription;
		private final String derivationOwnerDescription;
		private final String derivedPackageName;
		private final String derivationSourceDescription;

		private PackageNameContract(String contextDescription,
		                          String derivationOwnerDescription,
		                          String derivedPackageName,
		                          String derivationSourceDescription) {
			this.contextDescription = contextDescription;
			this.derivationOwnerDescription = derivationOwnerDescription;
			this.derivedPackageName = derivedPackageName;
			this.derivationSourceDescription = derivationSourceDescription;
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

	private static void ensureProcessorMappingExists(ProcessorConfig processorConfig,
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

	private static Set<String> sourceNames(SourceWrapper sourceWrapper) {
		Set<String> names = new HashSet<>();
		if (sourceWrapper.getSources() != null) {
			for (int i = 0; i < sourceWrapper.getSources().size(); i++) {
				String sourceName = requireNonBlank(sourceWrapper.getSources().get(i).getSourceName(), "sources[" + i + "].sourceName");
				names.add(sourceName);
			}
		}
		return names;
	}

	private static Set<String> targetNames(TargetWrapper targetWrapper) {
		Set<String> names = new HashSet<>();
		if (targetWrapper.getTargets() != null) {
			for (int i = 0; i < targetWrapper.getTargets().size(); i++) {
				String targetName = requireNonBlank(targetWrapper.getTargets().get(i).getTargetName(), "targets[" + i + "].targetName");
				names.add(targetName);
			}
		}
		return names;
	}

	private static String requireNonBlank(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new ConfigException("Missing required property '" + propertyName + "'.");
		}
		return value.trim();
	}


	private void applyJobScopedPackageDefaults(SourceWrapper sourceWrapper,
	                                         TargetWrapper targetWrapper,
	                                         String scenarioName) {
		applyDefaultSourcePackages(sourceWrapper, scenarioName);
		applyDefaultTargetPackages(targetWrapper, scenarioName);
	}

	private static void applyDefaultSourcePackages(SourceWrapper sourceWrapper, String scenarioName) {
		if (sourceWrapper == null || sourceWrapper.getSources() == null) {
			return;
		}

		String defaultSourcePackage = JobScopedPackageNameResolver.resolveSourcePackage(scenarioName);
		for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
			sourceConfig.setPackageName(defaultSourcePackage);
		}
	}

	private static void applyDirectConfigSourcePackages(SourceWrapper sourceWrapper) {
		if (sourceWrapper == null || sourceWrapper.getSources() == null) {
			return;
		}

		for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
			if (sourceConfig != null) {
				sourceConfig.setPackageName("com.etl.model.source");
			}
		}
	}

	private static void applyDefaultTargetPackages(TargetWrapper targetWrapper, String scenarioName) {
		if (targetWrapper == null || targetWrapper.getTargets() == null) {
			return;
		}

		List<TargetConfig> defaultedTargets = new ArrayList<>();
		for (TargetConfig targetConfig : targetWrapper.getTargets()) {
			defaultedTargets.add(applyDefaultTargetPackage(targetConfig, scenarioName));
		}
		targetWrapper.setTargets(List.copyOf(defaultedTargets));
	}

	private static void applyDirectConfigTargetPackages(TargetWrapper targetWrapper) {
		if (targetWrapper == null || targetWrapper.getTargets() == null) {
			return;
		}

		List<TargetConfig> defaultedTargets = new ArrayList<>();
		for (TargetConfig targetConfig : targetWrapper.getTargets()) {
			defaultedTargets.add(applyDirectConfigTargetPackage(targetConfig));
		}
		targetWrapper.setTargets(List.copyOf(defaultedTargets));
	}

	private static TargetConfig applyDirectConfigTargetPackage(TargetConfig targetConfig) {
		if (targetConfig == null) {
			return targetConfig;
		}

		String defaultTargetPackage = "com.etl.model.target";
		if (targetConfig instanceof CsvTargetConfig csvTargetConfig) {
			return new CsvTargetConfig(
					csvTargetConfig.getTargetName(),
					defaultTargetPackage,
					copyColumns(csvTargetConfig.getFields()),
					csvTargetConfig.getFilePath(),
					csvTargetConfig.getDelimiter(),
					csvTargetConfig.isIncludeHeader(),
					csvTargetConfig.isPackageAsZip()
			);
		}
		if (targetConfig instanceof JsonTargetConfig jsonTargetConfig) {
			return new JsonTargetConfig(
					jsonTargetConfig.getTargetName(),
					defaultTargetPackage,
					copyColumns(jsonTargetConfig.getFields()),
					jsonTargetConfig.getFilePath(),
					jsonTargetConfig.isPackageAsZip()
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
					xmlTargetConfig.getModelDefinitionPath(),
					xmlTargetConfig.isPackageAsZip()
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

	private static TargetConfig applyDefaultTargetPackage(TargetConfig targetConfig, String scenarioName) {
		if (targetConfig == null) {
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
					csvTargetConfig.isIncludeHeader(),
					csvTargetConfig.isPackageAsZip()
			);
		}
		if (targetConfig instanceof JsonTargetConfig jsonTargetConfig) {
			return new JsonTargetConfig(
					jsonTargetConfig.getTargetName(),
					defaultTargetPackage,
					copyColumns(jsonTargetConfig.getFields()),
					jsonTargetConfig.getFilePath(),
					jsonTargetConfig.isPackageAsZip()
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
					xmlTargetConfig.getModelDefinitionPath(),
					xmlTargetConfig.isPackageAsZip()
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

	private static String resolveReferencedPath(Path jobConfigDirectory, String configuredPath, String propertyName) {
		if (configuredPath == null || configuredPath.isBlank()) {
			throw new ConfigException("JobConfig missing required property '" + propertyName + "'");
		}

		String normalizedPath = configuredPath.trim();
		Path path = Path.of(normalizedPath);
		return path.isAbsolute()
				? path.normalize().toString()
				: jobConfigDirectory.resolve(path).normalize().toString();
	}

	private static Path parentDirectory(String resolvedConfigPath) {
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
				if (fileSourceConfig.getUnzipConfig() != null) {
					fileSourceConfig.getUnzipConfig().setExtractDir(resolveScenarioPath(configDirectory, fileSourceConfig.getUnzipConfig().getExtractDir()));
				}
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
					xmlSourceConfig.getValidation().setSchemaPath(resolveScenarioPath(configDirectory, xmlSourceConfig.getValidation().getSchemaPath()));
					xmlSourceConfig.getValidation().setRejectPath(resolveScenarioPath(configDirectory, xmlSourceConfig.getValidation().getRejectPath()));
				}
			}
		}
	}

	private static void normalizeTargetConfigPaths(TargetWrapper targetWrapper, Path configDirectory) {
		if (targetWrapper == null || targetWrapper.getTargets() == null) {
			return;
		}

		List<TargetConfig> normalizedTargets = new ArrayList<>();
		for (TargetConfig targetConfig : targetWrapper.getTargets()) {
			normalizedTargets.add(normalizeTargetConfig(targetConfig, configDirectory));
		}
		targetWrapper.setTargets(List.copyOf(normalizedTargets));
	}

	private static TargetConfig normalizeTargetConfig(TargetConfig targetConfig, Path configDirectory) {
		if (targetConfig instanceof CsvTargetConfig csvTargetConfig) {
			return new CsvTargetConfig(
					csvTargetConfig.getTargetName(),
					csvTargetConfig.getPackageName(),
					copyColumns(csvTargetConfig.getFields()),
					resolveScenarioPath(configDirectory, csvTargetConfig.getFilePath()),
					csvTargetConfig.getDelimiter(),
					csvTargetConfig.isIncludeHeader(),
					csvTargetConfig.isPackageAsZip()
			);
		}
		if (targetConfig instanceof JsonTargetConfig jsonTargetConfig) {
			return new JsonTargetConfig(
					jsonTargetConfig.getTargetName(),
					jsonTargetConfig.getPackageName(),
					copyColumns(jsonTargetConfig.getFields()),
					resolveScenarioPath(configDirectory, jsonTargetConfig.getFilePath()),
					jsonTargetConfig.isPackageAsZip()
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
					resolveScenarioPath(configDirectory, xmlTargetConfig.getModelDefinitionPath()),
					xmlTargetConfig.isPackageAsZip()
			);
		}
		if (targetConfig instanceof RelationalTargetConfig) {
			return targetConfig;
		}
		return targetConfig;
	}

	private static void normalizeProcessorConfigPaths(ProcessorConfig processorConfig, Path configDirectory) {
		if (processorConfig == null || processorConfig.getRejectHandling() == null) {
			return;
		}
		processorConfig.getRejectHandling().setOutputPath(
				resolveScenarioPath(configDirectory, processorConfig.getRejectHandling().getOutputPath())
		);
		processorConfig.getRejectHandling().setQuarantinePath(
				resolveScenarioPath(configDirectory, processorConfig.getRejectHandling().getQuarantinePath())
		);
	}

	private static List<ColumnConfig> copyColumns(List<? extends FieldDefinition> fields) {
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

	private static String resolveScenarioPath(Path configDirectory, String configuredPath) {
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

	private static boolean isWorkingDirectoryRelativeCompatibilityPath(Path path) {
		if (path.getNameCount() == 0) {
			return false;
		}

		String firstSegment = path.getName(0).toString();
		return path.isAbsolute()
				|| "src".equals(firstSegment)
				|| "target".equals(firstSegment);
	}

	private static String defaultName(String configuredName) {
		return configuredName == null || configuredName.isBlank() ? "unnamed" : configuredName.trim();
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
		} catch (IllegalArgumentException | IllegalStateException e) {
			throw new ConfigException("Invalid processor configuration for scenario '"
					+ defaultName(scenarioName) + "' in " + resolvedProcessorConfigPath + ": " + e.getMessage(), e);
		}
	}

	private static void validateRejectHandling(ProcessorConfig config) {
		ProcessorConfig.RejectHandling rejectHandling = config.getRejectHandling();
		if (rejectHandling == null || !rejectHandling.isEnabled()) {
			return;
		}

		if (rejectHandling.getOutputPath() == null || rejectHandling.getOutputPath().isBlank()) {
			throw new IllegalStateException("ProcessorConfig.rejectHandling.enabled=true requires a non-blank 'outputPath'.");
		}

		validateRejectOutputDirectoryStyle(rejectHandling.getOutputPath());

		if (rejectHandling.getQuarantinePath() != null && !rejectHandling.getQuarantinePath().isBlank()) {
			validateRejectQuarantineDirectoryStyle(rejectHandling.getQuarantinePath());
		}
	}

	private static void validateRejectOutputDirectoryStyle(String outputPath) {
		String trimmedPath = outputPath.trim();
		if (trimmedPath.endsWith("/") || trimmedPath.endsWith("\\")) {
			return;
		}

		Path normalizedPath = Path.of(trimmedPath).normalize();
		Path fileName = normalizedPath.getFileName();
		if (fileName == null || !fileName.toString().contains(".")) {
			return;
		}

		throw new IllegalStateException("ProcessorConfig.rejectHandling.outputPath must be a directory-style path. "
				+ "Reject file names are runtime-generated as '<step-name>-rejects.csv' (or '.csv.zip' when packageAsZip=true).");
	}

	private static void validateRejectQuarantineDirectoryStyle(String quarantinePath) {
		String trimmedPath = quarantinePath.trim();
		if (trimmedPath.endsWith("/") || trimmedPath.endsWith("\\")) {
			return;
		}

		Path normalizedPath = Path.of(trimmedPath).normalize();
		Path fileName = normalizedPath.getFileName();
		if (fileName == null || !fileName.toString().contains(".")) {
			return;
		}

		throw new IllegalStateException("ProcessorConfig.rejectHandling.quarantinePath must be a directory-style path. "
				+ "Quarantined reject artifact names are runtime-generated from '<step-name>-rejects.csv' (or '.csv.zip' when packageAsZip=true).");
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

	private static boolean allowsDerivedFieldWithoutSource(ProcessorConfig.FieldMapping fieldMapping) {
		return fieldMapping != null
				&& fieldMapping.getTransforms() != null
				&& !fieldMapping.getTransforms().isEmpty()
				&& fieldMapping.getTransforms().get(0) != null
				&& "expression".equalsIgnoreCase(fieldMapping.getTransforms().get(0).getType());
	}

	private static void validateRuleFailureAction(ProcessorConfig config,
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
