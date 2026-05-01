package com.etl.generation.xml.build;

import com.etl.common.util.ValidationUtils;
import com.etl.config.job.JobConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import com.etl.generation.xml.XmlModelDefinition;
import com.etl.generation.xml.XmlModelDefinitionLoader;
import com.etl.generation.xml.XmlModelGenerationResult;
import com.etl.generation.xml.XmlStructureClassGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generates XML source/target model classes for the XML assets referenced by one explicit job config.
 */
public class XmlJobScopedGenerationService {

    private final ObjectMapper yamlMapper;
    private final XmlModelDefinitionLoader definitionLoader;
    private final XmlStructureClassGenerator classGenerator;
    private final XmlConfigToModelDefinitionMapper configMapper;

    public XmlJobScopedGenerationService() {
        this(buildYamlMapper(), new XmlModelDefinitionLoader(), new XmlStructureClassGenerator(), new XmlConfigToModelDefinitionMapper());
    }

    XmlJobScopedGenerationService(ObjectMapper yamlMapper,
                                  XmlModelDefinitionLoader definitionLoader,
                                  XmlStructureClassGenerator classGenerator,
                                  XmlConfigToModelDefinitionMapper configMapper) {
        this.yamlMapper = yamlMapper;
        this.definitionLoader = definitionLoader;
        this.classGenerator = classGenerator;
        this.configMapper = configMapper;
    }

    public XmlJobScopedGenerationResult generate(Path jobConfigPath, Path outputRoot) throws IOException {
        ValidationUtils.requireNonNull(jobConfigPath, "jobConfigPath must not be null.");
        ValidationUtils.requireNonNull(outputRoot, "outputRoot must not be null.");

        Path normalizedJobConfigPath = jobConfigPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedJobConfigPath)) {
            throw new IOException("Job config not found: " + normalizedJobConfigPath);
        }

        JobConfig jobConfig = yamlMapper.readValue(normalizedJobConfigPath.toFile(), JobConfig.class);
        Path jobDirectory = Objects.requireNonNull(normalizedJobConfigPath.getParent(), "Job config parent directory must exist.");
        Path sourceConfigPath = resolveReferencedPath(jobDirectory, jobConfig.getSourceConfigPath(), "sourceConfigPath");
        Path targetConfigPath = resolveReferencedPath(jobDirectory, jobConfig.getTargetConfigPath(), "targetConfigPath");

        SourceWrapper sourceWrapper = yamlMapper.readValue(sourceConfigPath.toFile(), SourceWrapper.class);
        TargetWrapper targetWrapper = yamlMapper.readValue(targetConfigPath.toFile(), TargetWrapper.class);
        List<JobConfig.JobStepConfig> steps = requireSteps(jobConfig);

        Map<String, SourceConfig> sourcesByName = indexSources(sourceWrapper);
        Map<String, TargetConfig> targetsByName = indexTargets(targetWrapper);
        Set<String> selectedSourceNames = new LinkedHashSet<>();
        Set<String> selectedTargetNames = new LinkedHashSet<>();
        for (JobConfig.JobStepConfig step : steps) {
            String sourceName = requireNonBlank(step.getSource(), "Job step source");
            String targetName = requireNonBlank(step.getTarget(), "Job step target");
            if (!sourcesByName.containsKey(sourceName)) {
                throw new IllegalStateException("Job step references unknown source '" + sourceName + "'.");
            }
            if (!targetsByName.containsKey(targetName)) {
                throw new IllegalStateException("Job step references unknown target '" + targetName + "'.");
            }
            selectedSourceNames.add(sourceName);
            selectedTargetNames.add(targetName);
        }

        Path sourceOutputRoot = outputRoot.resolve("source");
        Path targetOutputRoot = outputRoot.resolve("target");
        Files.createDirectories(sourceOutputRoot);
        Files.createDirectories(targetOutputRoot);

        List<XmlModelGenerationResult> sourceResults = selectedSourceNames.stream()
                .map(sourcesByName::get)
                .filter(XmlSourceConfig.class::isInstance)
                .map(XmlSourceConfig.class::cast)
                .map(config -> generateSourceModel(config, sourceConfigPath.getParent(), sourceOutputRoot))
                .toList();

        List<XmlModelGenerationResult> targetResults = selectedTargetNames.stream()
                .map(targetsByName::get)
                .filter(XmlTargetConfig.class::isInstance)
                .map(XmlTargetConfig.class::cast)
                .map(config -> generateTargetModel(config, targetConfigPath.getParent(), targetOutputRoot))
                .toList();

        return new XmlJobScopedGenerationResult(deriveJobName(jobConfig, jobDirectory), sourceResults, targetResults);
    }

    private XmlModelGenerationResult generateSourceModel(XmlSourceConfig config, Path sourceConfigDirectory, Path outputRoot) {
        try {
            XmlModelDefinition definition = resolveSourceDefinition(config, sourceConfigDirectory);
            return classGenerator.generate(definition, outputRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate XML source model for source '" + config.getSourceName() + "'.", e);
        }
    }

    private XmlModelGenerationResult generateTargetModel(XmlTargetConfig config, Path targetConfigDirectory, Path outputRoot) {
        try {
            XmlModelDefinition definition = resolveTargetDefinition(config, targetConfigDirectory);
            return classGenerator.generate(definition, outputRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate XML target model for target '" + config.getTargetName() + "'.", e);
        }
    }

    private XmlModelDefinition resolveSourceDefinition(XmlSourceConfig config, Path configDirectory) throws IOException {
        if (config.getModelDefinitionPath() == null || config.getModelDefinitionPath().isBlank()) {
            return configMapper.fromSourceConfig(config);
        }
        XmlModelDefinition definition = definitionLoader.load(resolveModelDefinitionPath(configDirectory, config.getModelDefinitionPath()));
        return applyConfigContract(definition, config.getPackageName(), config.getRootElement(), config.getRecordElement());
    }

    private XmlModelDefinition resolveTargetDefinition(XmlTargetConfig config, Path configDirectory) throws IOException {
        if (config.getModelDefinitionPath() == null || config.getModelDefinitionPath().isBlank()) {
            return configMapper.fromTargetConfig(config);
        }
        XmlModelDefinition definition = definitionLoader.load(resolveModelDefinitionPath(configDirectory, config.getModelDefinitionPath()));
        return applyConfigContract(definition, config.getPackageName(), config.getRootElement(), config.getRecordElement());
    }

    private XmlModelDefinition applyConfigContract(XmlModelDefinition definition,
                                                   String packageName,
                                                   String rootElement,
                                                   String recordElement) {
        ValidationUtils.requireNonNull(definition, "XmlModelDefinition must not be null.");
        ValidationUtils.requireNonBlank("Invalid XML generation contract", packageName, rootElement, recordElement);
        if (definition.getRootElement() != null && !rootElement.equals(definition.getRootElement())) {
            throw new IllegalStateException("XML model definition root element '" + definition.getRootElement()
                    + "' does not match config root element '" + rootElement + "'.");
        }
        if (definition.getRecordElement() != null && !recordElement.equals(definition.getRecordElement())) {
            throw new IllegalStateException("XML model definition record element '" + definition.getRecordElement()
                    + "' does not match config record element '" + recordElement + "'.");
        }
        definition.setPackageName(packageName);
        definition.setRootElement(rootElement);
        definition.setRecordElement(recordElement);
        return definition;
    }

    private List<JobConfig.JobStepConfig> requireSteps(JobConfig jobConfig) {
        if (jobConfig.getSteps() == null || jobConfig.getSteps().isEmpty()) {
            throw new IllegalStateException("Job config must define at least one step for XML generation.");
        }
        return jobConfig.getSteps();
    }

    private Map<String, SourceConfig> indexSources(SourceWrapper sourceWrapper) {
        Map<String, SourceConfig> sourcesByName = new LinkedHashMap<>();
        if (sourceWrapper.getSources() == null) {
            return sourcesByName;
        }
        for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
            sourcesByName.put(requireNonBlank(sourceConfig.getSourceName(), "sourceName"), sourceConfig);
        }
        return Map.copyOf(sourcesByName);
    }

    private Map<String, TargetConfig> indexTargets(TargetWrapper targetWrapper) {
        Map<String, TargetConfig> targetsByName = new LinkedHashMap<>();
        if (targetWrapper.getTargets() == null) {
            return targetsByName;
        }
        for (TargetConfig targetConfig : targetWrapper.getTargets()) {
            targetsByName.put(requireNonBlank(targetConfig.getTargetName(), "targetName"), targetConfig);
        }
        return Map.copyOf(targetsByName);
    }

    private Path resolveModelDefinitionPath(Path configDirectory, String modelDefinitionPath) {
        Path path = Path.of(modelDefinitionPath);
        return path.isAbsolute() ? path.normalize() : configDirectory.resolve(path).normalize();
    }

    private Path resolveReferencedPath(Path baseDirectory, String configuredPath, String propertyName) {
        String pathValue = requireNonBlank(configuredPath, propertyName);
        Path path = Path.of(pathValue);
        return path.isAbsolute() ? path.normalize() : baseDirectory.resolve(path).normalize();
    }

    private String deriveJobName(JobConfig jobConfig, Path jobDirectory) {
        String configuredName = jobConfig.getName();
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName.trim();
        }
        Path fileName = jobDirectory.getFileName();
        return fileName == null ? "selected-job" : fileName.toString();
    }

    private String requireNonBlank(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property '" + propertyName + "'.");
        }
        return value.trim();
    }

    private static ObjectMapper buildYamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.findAndRegisterModules();
        return mapper;
    }
}

