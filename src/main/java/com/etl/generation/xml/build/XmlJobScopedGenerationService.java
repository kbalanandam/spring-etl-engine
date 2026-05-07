package com.etl.generation.xml.build;

import com.etl.common.util.JobScopedPackageNameResolver;
import com.etl.common.util.ValidationUtils;
import com.etl.config.job.JobConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.RelationalTargetConfig;
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
 * Generates selected scenario-scoped model classes for one explicit job config.
 * <p>
 * XML sources and XML targets still use structural generation, while flat CSV and
 * relational sources/targets now generate simple POJOs from their configured fields.
 * The existing class and profile names are retained for compatibility with the current
 * build workflow.
 * </p>
 */
public class XmlJobScopedGenerationService {

    private final ObjectMapper yamlMapper;
    private final XmlModelDefinitionLoader definitionLoader;
    private final XmlStructureClassGenerator classGenerator;
    private final FlatTargetModelClassGenerator flatTargetModelClassGenerator;
    private final XmlConfigToModelDefinitionMapper configMapper;

    public XmlJobScopedGenerationService() {
        this(buildYamlMapper(), new XmlModelDefinitionLoader(), new XmlStructureClassGenerator(), new FlatTargetModelClassGenerator(), new XmlConfigToModelDefinitionMapper());
    }

    XmlJobScopedGenerationService(ObjectMapper yamlMapper,
                                  XmlModelDefinitionLoader definitionLoader,
                                  XmlStructureClassGenerator classGenerator,
                                  FlatTargetModelClassGenerator flatTargetModelClassGenerator,
                                  XmlConfigToModelDefinitionMapper configMapper) {
        this.yamlMapper = yamlMapper;
        this.definitionLoader = definitionLoader;
        this.classGenerator = classGenerator;
        this.flatTargetModelClassGenerator = flatTargetModelClassGenerator;
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
        String jobName = JobScopedPackageNameResolver.deriveJobName(jobConfig, jobDirectory);
        Path sourceConfigPath = resolveReferencedPath(jobDirectory, jobConfig.getSourceConfigPath(), "sourceConfigPath");
        Path targetConfigPath = resolveReferencedPath(jobDirectory, jobConfig.getTargetConfigPath(), "targetConfigPath");

        SourceWrapper sourceWrapper = yamlMapper.readValue(sourceConfigPath.toFile(), SourceWrapper.class);
        TargetWrapper targetWrapper = yamlMapper.readValue(targetConfigPath.toFile(), TargetWrapper.class);
        applyJobScopedPackageDefaults(sourceWrapper, targetWrapper, jobName);
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
                .filter(this::supportsBuildTimeSourceGeneration)
                .map(config -> generateSourceModel(config, sourceConfigPath.getParent(), sourceOutputRoot))
                .toList();

        List<XmlModelGenerationResult> targetResults = selectedTargetNames.stream()
                .map(targetsByName::get)
                .filter(this::supportsBuildTimeTargetGeneration)
                .map(config -> generateTargetModel(config, targetConfigPath.getParent(), targetOutputRoot))
                .toList();

        return new XmlJobScopedGenerationResult(jobName, sourceResults, targetResults);
    }

    private void applyJobScopedPackageDefaults(SourceWrapper sourceWrapper,
                                              TargetWrapper targetWrapper,
                                              String jobName) {
        applyDefaultSourcePackages(sourceWrapper, jobName);
        applyDefaultTargetPackages(targetWrapper, jobName);
    }

    private void applyDefaultSourcePackages(SourceWrapper sourceWrapper, String jobName) {
        if (sourceWrapper == null || sourceWrapper.getSources() == null) {
            return;
        }

        String defaultSourcePackage = JobScopedPackageNameResolver.resolveSourcePackage(jobName);
        for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
            if (!hasText(sourceConfig.getPackageName())) {
                sourceConfig.setPackageName(defaultSourcePackage);
            }
        }
    }

    private void applyDefaultTargetPackages(TargetWrapper targetWrapper, String jobName) {
        if (targetWrapper == null || targetWrapper.getTargets() == null) {
            return;
        }

        List<TargetConfig> defaultedTargets = targetWrapper.getTargets().stream()
                .map(targetConfig -> applyDefaultTargetPackage(targetConfig, jobName))
                .toList();
        targetWrapper.setTargets(defaultedTargets);
    }

    private TargetConfig applyDefaultTargetPackage(TargetConfig targetConfig, String jobName) {
        if (targetConfig == null || hasText(targetConfig.getPackageName())) {
            return targetConfig;
        }

        String defaultTargetPackage = JobScopedPackageNameResolver.resolveTargetPackage(jobName);
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

    private XmlModelGenerationResult generateSourceModel(SourceConfig config, Path sourceConfigDirectory, Path outputRoot) {
        if (config instanceof XmlSourceConfig xmlSourceConfig) {
            try {
                XmlModelDefinition definition = resolveSourceDefinition(xmlSourceConfig, sourceConfigDirectory);
                return classGenerator.generate(definition, outputRoot);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to generate XML source model for source '" + xmlSourceConfig.getSourceName() + "'.", e);
            }
        }

        return generateFlatModel(config.getPackageName(), config.getSourceName(), config.getFields(), outputRoot,
                "source", config.getSourceName());
    }

    private XmlModelGenerationResult generateTargetModel(TargetConfig config, Path targetConfigDirectory, Path outputRoot) {
        if (!(config instanceof XmlTargetConfig xmlTargetConfig)) {
            return generateFlatModel(config.getPackageName(), config.getTargetName(), config.getFields(), outputRoot,
                    "target", config.getTargetName());
        }

        try {
            XmlModelDefinition definition = resolveTargetDefinition(xmlTargetConfig, targetConfigDirectory);
            return classGenerator.generate(definition, outputRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate XML target model for target '" + xmlTargetConfig.getTargetName() + "'.", e);
        }
    }

    private boolean supportsBuildTimeSourceGeneration(SourceConfig config) {
        return config instanceof XmlSourceConfig
                || config.getFormat() == com.etl.enums.ModelFormat.CSV
                || config.getFormat() == com.etl.enums.ModelFormat.RELATIONAL;
    }

    private boolean supportsBuildTimeTargetGeneration(TargetConfig config) {
        return config instanceof XmlTargetConfig
                || config.getFormat() == com.etl.enums.ModelFormat.CSV
                || config.getFormat() == com.etl.enums.ModelFormat.RELATIONAL;
    }

    private XmlModelGenerationResult generateFlatModel(String packageName,
                                                       String className,
                                                       List<? extends com.etl.config.FieldDefinition> fields,
                                                       Path outputRoot,
                                                       String role,
                                                       String configName) {
        try {
            return flatTargetModelClassGenerator.generate(packageName, className, fields, outputRoot);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to generate flat " + role + " model for '" + configName + "'.", e);
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

    private List<com.etl.config.ColumnConfig> copyColumns(List<? extends com.etl.config.FieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }

        return fields.stream()
                .map(field -> {
                    com.etl.config.ColumnConfig column = new com.etl.config.ColumnConfig();
                    column.setName(field.getName());
                    column.setType(field.getType());
                    return column;
                })
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

