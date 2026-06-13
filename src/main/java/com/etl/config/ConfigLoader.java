package com.etl.config;

import com.etl.exception.config.ConfigException;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.common.util.ConfigPackageNamePropertyValidator;
import com.etl.common.util.ConfigBundlePathAliasResolver;
import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.JobScopedPackageNameResolver;
import com.etl.common.util.SelectedJobNamingValidator;
import com.etl.config.source.validation.SourceValidationContext;
import com.etl.config.source.validation.SourceValidationService;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.runtime.job.JobConfigPaths;
import com.etl.runtime.job.JobRecoveryPolicy;
import com.etl.runtime.job.JobRunMode;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.runtime.job.JobRuntimeDescriptorAssembler;
import com.etl.processor.ProcessorExtensionDefaults;
import com.etl.processor.transform.TransformEvaluator;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.etl.runtime.FileIngestionRuntimeSupport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

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

	private String sourceConfigPath;

	private String targetConfigPath;

	private String processorConfigPath;

	private String jobConfigPath;

	private boolean allowDemoFallback;

	private final SourceValidationService sourceValidationService;
	private final RuntimeConfigResolver runtimeConfigResolver;
	private final RuntimeConfigValidation runtimeConfigValidation;
	private final RuntimeStepPolicyResolver runtimeStepPolicyResolver;

	public ConfigLoader() {
		this(
				new EtlConfigProperties(),
				new SourceValidationService(),
				new ValidationRuleEvaluator(ProcessorExtensionDefaults.defaultValidationRules(new FileIngestionRuntimeSupport())),
				new TransformEvaluator(ProcessorExtensionDefaults.defaultTransforms())
		);
	}

	public ConfigLoader(SourceValidationService sourceValidationService,
	                  ValidationRuleEvaluator validationRuleEvaluator) {
		this(
				new EtlConfigProperties(),
				sourceValidationService,
				validationRuleEvaluator,
				new TransformEvaluator(ProcessorExtensionDefaults.defaultTransforms())
		);
	}

	public ConfigLoader(SourceValidationService sourceValidationService,
	                  ValidationRuleEvaluator validationRuleEvaluator,
	                  TransformEvaluator transformEvaluator) {
		this(new EtlConfigProperties(), sourceValidationService, validationRuleEvaluator, transformEvaluator);
	}

	@Autowired
	public ConfigLoader(EtlConfigProperties etlConfigProperties,
	                  SourceValidationService sourceValidationService,
	                  ValidationRuleEvaluator validationRuleEvaluator,
	                  TransformEvaluator transformEvaluator) {
		applyEtlConfigProperties(etlConfigProperties);
		this.sourceValidationService = sourceValidationService;
		this.runtimeConfigResolver = new RuntimeConfigResolver(this);
		this.runtimeConfigValidation = new RuntimeConfigValidation(validationRuleEvaluator, transformEvaluator);
		this.runtimeStepPolicyResolver = new RuntimeStepPolicyResolver();
	}

	private void applyEtlConfigProperties(EtlConfigProperties etlConfigProperties) {
		EtlConfigProperties properties = etlConfigProperties == null ? new EtlConfigProperties() : etlConfigProperties;
		this.sourceConfigPath = properties.getSource();
		this.targetConfigPath = properties.getTarget();
		this.processorConfigPath = properties.getProcessor();
		this.jobConfigPath = properties.getJob();
		this.allowDemoFallback = properties.isAllowDemoFallback();
	}

	String runtimeConfigCacheKey() {
		return String.join("|",
			normalizeCacheToken(sourceConfigPath),
			normalizeCacheToken(targetConfigPath),
			normalizeCacheToken(processorConfigPath),
			normalizeCacheToken(jobConfigPath),
			Boolean.toString(allowDemoFallback)
		);
	}

	private static String normalizeCacheToken(String value) {
		return value == null ? "" : value.trim();
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
			ResolvedRuntimeConfig runtimeConfig = resolveRuntimeConfig();
			RunConfigurationMetadata descriptorMetadata = RunConfigurationMetadata.fromJobRuntimeDescriptor(jobRuntimeDescriptor);
			return new RunConfigurationMetadata(
					descriptorMetadata.scenarioName(),
					descriptorMetadata.jobConfigPath(),
					descriptorMetadata.demoFallbackMode(),
					descriptorMetadata.mainFlowName(),
					descriptorMetadata.subFlowName(),
					descriptorMetadata.recoveryPolicy(),
					runtimeConfig.steps()
			);
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
			RunConfigurationMetadata descriptorMetadata = RunConfigurationMetadata.fromJobRuntimeDescriptor(descriptor);
			return new RunConfigurationMetadata(
					descriptorMetadata.scenarioName(),
					descriptorMetadata.jobConfigPath(),
					descriptorMetadata.demoFallbackMode(),
					descriptorMetadata.mainFlowName(),
					descriptorMetadata.subFlowName(),
					descriptorMetadata.recoveryPolicy(),
					runtimeConfig.steps()
			);
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
			ResolvedRuntimeConfig runtimeConfig = runtimeConfigResolver.peekCachedRuntimeConfig();
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
			RuntimePackageDefaults.applyDefaultSourcePackages(sourceWrapper, runtimeConfig.scenarioName());
			return sourceWrapper;
		}

		SourceWrapper sourceWrapper = loadYamlConfig(
				runtimeConfig.sourceConfigPath(),
				"source-config.yaml",
				SourceWrapper.class,
				buildYamlMapper(),
				directSourcePackageContract()
		);
		RuntimePackageDefaults.applyDirectConfigSourcePackages(sourceWrapper);
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
			RuntimePackageDefaults.applyDefaultTargetPackages(targetWrapper, runtimeConfig.scenarioName());
			return targetWrapper;
		}

		TargetWrapper targetWrapper = loadYamlConfig(
				runtimeConfig.targetConfigPath(),
				"target-config.yaml",
				TargetWrapper.class,
				buildYamlMapper(),
				directTargetPackageContract()
		);
		RuntimePackageDefaults.applyDirectConfigTargetPackages(targetWrapper);
		return targetWrapper;
	}

	private ProcessorConfig loadProcessorConfigForRuntime(ResolvedRuntimeConfig runtimeConfig) throws IOException {
		ObjectMapper mapper = buildYamlMapper();
		ProcessorConfig config = runtimeConfig.requireExternalConfigs()
				? loadRequiredExternalYamlConfig(runtimeConfig.processorConfigPath(), ProcessorConfig.class, mapper, null)
				: loadYamlConfig(runtimeConfig.processorConfigPath(), "processor-config.yaml", ProcessorConfig.class, mapper, null);
		if (runtimeConfig.requireExternalConfigs()) {
			normalizeProcessorConfigPaths(config, parentDirectory(runtimeConfig.processorConfigPath()));
		}
		runtimeConfigValidation.validateProcessorConfig(
				config,
				runtimeConfig.scenarioName(),
				runtimeConfig.processorConfigPath(),
				loadSourceWrapperForRuntime(runtimeConfig)
		);
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
				runtimeConfig.recoveryPolicy(),
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
		return runtimeConfigResolver.resolveRuntimeConfig();
	}

	ResolvedRuntimeConfig buildRuntimeConfigInternal() throws IOException {
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
			ProcessorConfig demoProcessorConfig = loadYamlConfig(
					processorConfigPath,
					"processor-config.yaml",
					ProcessorConfig.class,
					buildYamlMapper(),
					null
			);
			runtimeConfigValidation.validateProcessorConfig(demoProcessorConfig, "demo-fallback", processorConfigPath, demoSourceWrapper);
			return new ResolvedRuntimeConfig(
					sourceConfigPath,
					targetConfigPath,
					processorConfigPath,
					false,
					"demo-fallback",
					"",
					true,
					JobRecoveryPolicy.RERUN_FROM_START,
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
		JobRecoveryPolicy recoveryPolicy = resolveRecoveryPolicy(jobConfig, scenarioName, jobConfigFile.toPath());
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
		ProcessorConfig explicitProcessorConfig = loadRequiredExternalYamlConfig(
				resolvedProcessorConfigPath,
				ProcessorConfig.class,
				mapper,
				null
		);
		normalizeSourceConfigPaths(explicitSourceWrapper, parentDirectory(resolvedSourceConfigPath));
		normalizeTargetConfigPaths(explicitTargetWrapper, parentDirectory(resolvedTargetConfigPath));
		applyJobScopedPackageDefaults(explicitSourceWrapper, explicitTargetWrapper, scenarioName);
		normalizeProcessorConfigPaths(explicitProcessorConfig, parentDirectory(resolvedProcessorConfigPath));
		sourceValidationService.validateSelectedSources(
				explicitSourceWrapper,
				new SourceValidationContext(scenarioName, resolvedSourceConfigPath)
		);
		runtimeConfigValidation.validateSelectedTargetConfigs(explicitTargetWrapper, scenarioName, resolvedTargetConfigPath);
		runtimeConfigValidation.validateProcessorConfig(explicitProcessorConfig, scenarioName, resolvedProcessorConfigPath, explicitSourceWrapper);
		List<JobConfig.JobStepConfig> resolvedSteps = runtimeStepPolicyResolver.resolveExplicitSteps(
				jobConfig,
				explicitSourceWrapper,
				explicitTargetWrapper,
				explicitProcessorConfig
		);
		List<JobConfig.JobStepConfig> standardSteps = resolvedSteps.stream()
				.filter(JobConfig.JobStepConfig::isStandardStep)
				.toList();
		SelectedJobNamingValidator.validate(scenarioName, explicitSourceWrapper, explicitTargetWrapper, standardSteps);
		validateSelectedGeneratedModelClasses(explicitSourceWrapper, explicitTargetWrapper, standardSteps, scenarioName, jobConfigFile.getAbsolutePath());

		return new ResolvedRuntimeConfig(
				resolvedSourceConfigPath,
				resolvedTargetConfigPath,
				resolvedProcessorConfigPath,
				true,
				scenarioName,
				jobConfigFile.getAbsolutePath(),
				false,
				recoveryPolicy,
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

	private static JobRecoveryPolicy resolveRecoveryPolicy(JobConfig jobConfig, String scenarioName, Path jobConfigPath) {
		if (jobConfig == null || jobConfig.getRecoveryPolicy() == null || jobConfig.getRecoveryPolicy().isBlank()) {
			return JobRecoveryPolicy.RERUN_FROM_START;
		}
		String configuredValue = jobConfig.getRecoveryPolicy().trim();
		return JobRecoveryPolicy.fromLogValue(configuredValue)
				.orElseThrow(() -> new ConfigException("Selected job '" + defaultName(scenarioName)
						+ "' in " + jobConfigPath.toAbsolutePath().normalize()
						+ " configures unsupported recoveryPolicy='" + configuredValue
						+ "'. Supported values: " + JobRecoveryPolicy.RERUN_FROM_START.logValue()
						+ ", " + JobRecoveryPolicy.RESUME_FROM_CHECKPOINT.logValue() + "."));
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
			runtimeStepPolicyResolver.ensureProcessorMappingExists(processorConfig, stepName, sourceName, targetName);

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


	private static String requireNonBlank(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new ConfigException("Missing required property '" + propertyName + "'.");
		}
		return value.trim();
	}



	private void applyJobScopedPackageDefaults(SourceWrapper sourceWrapper,
	                                         TargetWrapper targetWrapper,
	                                         String scenarioName) {
		RuntimePackageDefaults.applyDefaultSourcePackages(sourceWrapper, scenarioName);
		RuntimePackageDefaults.applyDefaultTargetPackages(targetWrapper, scenarioName);
	}

	private static String resolveReferencedPath(Path jobConfigDirectory, String configuredPath, String propertyName) {
		return RuntimeConfigIO.resolveReferencedPath(jobConfigDirectory, configuredPath, propertyName);
	}

	private static Path parentDirectory(String resolvedConfigPath) {
		return RuntimeConfigIO.parentDirectory(resolvedConfigPath);
	}

	private void normalizeSourceConfigPaths(SourceWrapper sourceWrapper, Path configDirectory) {
		RuntimeConfigIO.normalizeSourceConfigPaths(sourceWrapper, configDirectory);
	}

	private static void normalizeTargetConfigPaths(TargetWrapper targetWrapper, Path configDirectory) {
		RuntimeConfigIO.normalizeTargetConfigPaths(targetWrapper, configDirectory);
	}

	private static void normalizeProcessorConfigPaths(ProcessorConfig processorConfig, Path configDirectory) {
		RuntimeConfigIO.normalizeProcessorConfigPaths(processorConfig, configDirectory);
	}

	private static String defaultName(String configuredName) {
		return configuredName == null || configuredName.isBlank() ? "unnamed" : configuredName.trim();
	}



	record ResolvedRuntimeConfig(
			String sourceConfigPath,
			String targetConfigPath,
			String processorConfigPath,
			boolean requireExternalConfigs,
			String scenarioName,
			String jobConfigPath,
			boolean demoFallbackMode,
			JobRecoveryPolicy recoveryPolicy,
			List<JobConfig.JobStepConfig> steps
	) {
	}


}
