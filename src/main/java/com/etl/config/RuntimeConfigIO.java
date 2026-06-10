package com.etl.config;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.FileSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.JsonTargetConfig;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import com.etl.exception.config.ConfigException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * IO/path helpers for selected runtime config loading.
 */
final class RuntimeConfigIO {

    private RuntimeConfigIO() {
    }

    static String resolveReferencedPath(Path jobConfigDirectory, String configuredPath, String propertyName) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new ConfigException("JobConfig missing required property '" + propertyName + "'");
        }

        String normalizedPath = configuredPath.trim();
        Path path = Path.of(normalizedPath);
        return path.isAbsolute()
                ? path.normalize().toString()
                : jobConfigDirectory.resolve(path).normalize().toString();
    }

    static Path parentDirectory(String resolvedConfigPath) {
        Path absolutePath = Path.of(resolvedConfigPath).toAbsolutePath().normalize();
        Path parent = absolutePath.getParent();
        return parent == null ? absolutePath : parent;
    }

    static void normalizeSourceConfigPaths(SourceWrapper sourceWrapper, Path configDirectory) {
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

    static void normalizeTargetConfigPaths(TargetWrapper targetWrapper, Path configDirectory) {
        if (targetWrapper == null || targetWrapper.getTargets() == null) {
            return;
        }

        List<TargetConfig> normalizedTargets = new ArrayList<>();
        for (TargetConfig targetConfig : targetWrapper.getTargets()) {
            normalizedTargets.add(normalizeTargetConfig(targetConfig, configDirectory));
        }
        targetWrapper.setTargets(List.copyOf(normalizedTargets));
    }

    static void normalizeProcessorConfigPaths(ProcessorConfig processorConfig, Path configDirectory) {
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

    static List<ColumnConfig> copyColumns(List<? extends FieldDefinition> fields) {
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
        return "src".equals(firstSegment)
                || "target".equals(firstSegment);
    }
}
