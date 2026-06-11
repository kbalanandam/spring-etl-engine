package com.etl.config;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.enums.ModelFormat;
import com.etl.exception.config.ConfigException;
import com.etl.exception.config.ProcessorExtensionBindingConfigException;
import com.etl.processor.transform.TransformEvaluator;
import com.etl.processor.validation.ValidationRuleEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Validation helper for runtime-loaded processor/target configs.
 */
final class RuntimeConfigValidation {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeConfigValidation.class);

    private final ValidationRuleEvaluator validationRuleEvaluator;
    private final TransformEvaluator transformEvaluator;

    RuntimeConfigValidation(ValidationRuleEvaluator validationRuleEvaluator,
                            TransformEvaluator transformEvaluator) {
        this.validationRuleEvaluator = validationRuleEvaluator;
        this.transformEvaluator = transformEvaluator;
    }

    void validateSelectedTargetConfigs(TargetWrapper targetWrapper, String scenarioName, String targetConfigPath) {
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

    void validateProcessorConfig(ProcessorConfig config,
                                 String scenarioName,
                                 String resolvedProcessorConfigPath,
                                 SourceWrapper sourceWrapper) {
        try {
            validateProcessorType(config);
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

                ModelFormat sourceFormat = resolveSourceFormat(entityMapping, sourceWrapper);

                for (ProcessorConfig.FieldMapping fieldMapping : entityMapping.getFields()) {
                    if ((fieldMapping.getFrom() == null || fieldMapping.getFrom().isBlank()) && !allowsDerivedFieldWithoutSource(fieldMapping)) {
                        throw new IllegalStateException("FieldMapping missing 'from' in entity " + entityMapping.getSource()
                                + ". Omit 'from' only when the first transform is type 'expression'.");
                    }
                    if (fieldMapping.getTo() == null || fieldMapping.getTo().isEmpty()) {
                        throw new IllegalStateException("FieldMapping missing 'to' in entity " + entityMapping.getSource());
                    }
                    validateFieldTransforms(entityMapping, fieldMapping, sourceFormat,
                            defaultName(scenarioName), resolvedProcessorConfigPath);
                    validateFieldRules(config, entityMapping, fieldMapping, sourceFormat,
                            defaultName(scenarioName), resolvedProcessorConfigPath);
                }
            }

            validateRejectHandling(config);
            logger.info("Processor configuration loaded and validated successfully from YAML");
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ConfigException("Invalid processor configuration for scenario '"
                    + defaultName(scenarioName) + "' in " + resolvedProcessorConfigPath + ": " + e.getMessage(), e);
        }
    }

    private static void validateProcessorType(ProcessorConfig config) {
        if (config == null || config.getType() == null || config.getType().isBlank()) {
            throw new IllegalStateException("ProcessorConfig.type must be 'default'. The active processor contract no longer supports blank or legacy processor types.");
        }

        String normalizedType = config.getType().trim();
        if (!"default".equalsIgnoreCase(normalizedType)) {
            throw new IllegalStateException("ProcessorConfig.type='" + normalizedType + "' is no longer supported. The active runtime only accepts 'type: default'; migrate custom behavior into shared processor transforms, processor rules, or supported extension providers.");
        }
    }

    private static ModelFormat resolveSourceFormat(ProcessorConfig.EntityMapping entityMapping, SourceWrapper sourceWrapper) {
        if (sourceWrapper == null || sourceWrapper.getSources() == null || sourceWrapper.getSources().isEmpty()) {
            return null;
        }

        for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
            if (sourceConfig != null
                    && sourceConfig.getSourceName() != null
                    && sourceConfig.getSourceName().equalsIgnoreCase(entityMapping.getSource())) {
                if (sourceConfig.getFormat() == null) {
                    throw new ProcessorExtensionBindingConfigException("EntityMapping source '" + entityMapping.getSource()
                            + "' has no declared source format in source-config.yaml.");
                }
                return sourceConfig.getFormat();
            }
        }

        throw new ProcessorExtensionBindingConfigException("EntityMapping references unknown source '" + entityMapping.getSource()
                + "' in processor config. Add that source to source-config.yaml.");
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
                                    ProcessorConfig.FieldMapping fieldMapping,
                                    ModelFormat sourceFormat,
                                    String scenarioName,
                                    String processorConfigPath) {
        if (fieldMapping.getRules() == null || fieldMapping.getRules().isEmpty()) {
            return;
        }

        for (int i = 0; i < fieldMapping.getRules().size(); i++) {
            ProcessorConfig.FieldRule rule = fieldMapping.getRules().get(i);
            validateRuleFailureAction(config, entityMapping, fieldMapping, rule);
            try {
                validationRuleEvaluator.validateConfiguration(entityMapping, fieldMapping, rule, sourceFormat);
            } catch (IllegalArgumentException | IllegalStateException e) {
                String formatLabel = sourceFormat == null ? "unknown" : sourceFormat.getFormat();
                throw new ProcessorExtensionBindingConfigException("Invalid processor configuration for scenario '"
                        + scenarioName + "' in " + processorConfigPath + ": Invalid or unsupported FieldMapping rule type '"
                        + defaultName(rule.getType()) + "' in processor config for scenario '" + scenarioName
                        + "' (path='" + processorConfigPath + "', entity=" + entityLabel(entityMapping)
                        + ", field=" + fieldLabel(fieldMapping) + ", sourceFormat=" + formatLabel + "). "
                        + "Use a supported processor rule type or add a ProcessorValidationRule extension provider. "
                        + "Cause: " + e.getMessage(), e);
            }
        }
    }

    private void validateFieldTransforms(ProcessorConfig.EntityMapping entityMapping,
                                         ProcessorConfig.FieldMapping fieldMapping,
                                         ModelFormat sourceFormat,
                                         String scenarioName,
                                         String processorConfigPath) {
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
            try {
                transformEvaluator.validateConfiguration(entityMapping, fieldMapping, transform, sourceFormat);
            } catch (IllegalArgumentException | IllegalStateException e) {
                String formatLabel = sourceFormat == null ? "unknown" : sourceFormat.getFormat();
                throw new ProcessorExtensionBindingConfigException("Invalid processor configuration for scenario '"
                        + scenarioName + "' in " + processorConfigPath + ": Invalid or unsupported FieldMapping transform type '"
                        + defaultName(transform.getType()) + "' in processor config for scenario '" + scenarioName
                        + "' (path='" + processorConfigPath + "', entity=" + entityLabel(entityMapping)
                        + ", field=" + fieldLabel(fieldMapping) + ", sourceFormat=" + formatLabel + "). "
                        + "Use a supported processor transform type or add a ProcessorFieldTransform extension provider. "
                        + "Cause: " + e.getMessage(), e);
            }
        }
    }

    private static String entityLabel(ProcessorConfig.EntityMapping entityMapping) {
        return defaultName(entityMapping == null ? null : entityMapping.getSource())
                + "->"
                + defaultName(entityMapping == null ? null : entityMapping.getTarget());
    }

    private static String fieldLabel(ProcessorConfig.FieldMapping fieldMapping) {
        String from = defaultName(fieldMapping == null ? null : fieldMapping.getFrom());
        String to = defaultName(fieldMapping == null ? null : fieldMapping.getTo());
        return from + "->" + to;
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

    private static String defaultName(String configuredName) {
        return configuredName == null || configuredName.isBlank() ? "unnamed" : configuredName.trim();
    }
}

