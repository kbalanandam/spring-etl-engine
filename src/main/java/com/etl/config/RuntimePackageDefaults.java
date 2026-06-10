package com.etl.config;

import com.etl.common.util.JobScopedPackageNameResolver;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.JsonTargetConfig;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;

import java.util.ArrayList;
import java.util.List;

import static com.etl.config.RuntimeConfigIO.copyColumns;

/**
 * Applies generated package-name defaults for source/target config models.
 */
final class RuntimePackageDefaults {

    private RuntimePackageDefaults() {
    }

    static void applyDefaultSourcePackages(SourceWrapper sourceWrapper, String scenarioName) {
        if (sourceWrapper == null || sourceWrapper.getSources() == null) {
            return;
        }

        String defaultSourcePackage = JobScopedPackageNameResolver.resolveSourcePackage(scenarioName);
        for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
            sourceConfig.setPackageName(defaultSourcePackage);
        }
    }

    static void applyDirectConfigSourcePackages(SourceWrapper sourceWrapper) {
        if (sourceWrapper == null || sourceWrapper.getSources() == null) {
            return;
        }

        for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
            if (sourceConfig != null) {
                sourceConfig.setPackageName("com.etl.model.source");
            }
        }
    }

    static void applyDefaultTargetPackages(TargetWrapper targetWrapper, String scenarioName) {
        if (targetWrapper == null || targetWrapper.getTargets() == null) {
            return;
        }

        List<TargetConfig> defaultedTargets = new ArrayList<>();
        for (TargetConfig targetConfig : targetWrapper.getTargets()) {
            defaultedTargets.add(applyDefaultTargetPackage(targetConfig, scenarioName));
        }
        targetWrapper.setTargets(List.copyOf(defaultedTargets));
    }

    static void applyDirectConfigTargetPackages(TargetWrapper targetWrapper) {
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
}

