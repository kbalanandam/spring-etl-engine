package com.etl.config;

import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetWrapper;
import com.etl.exception.EtlErrorCategory;
import com.etl.exception.config.ConfigException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves explicit job steps and normalizes skip/retry policy contracts.
 */
final class RuntimeStepPolicyResolver {

    List<JobConfig.JobStepConfig> resolveExplicitSteps(JobConfig jobConfig,
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
        Map<String, SourceConfig> sourcesByName = mapSourcesByName(sourceWrapper);
        List<JobConfig.JobStepConfig> resolvedSteps = new ArrayList<>();

        for (int i = 0; i < configuredSteps.size(); i++) {
            JobConfig.JobStepConfig configuredStep = configuredSteps.get(i);
            if (configuredStep == null) {
                throw new ConfigException("JobConfig contains a null step definition at index " + i);
            }

            String stepName = requireNonBlank(configuredStep.getName(), "JobConfig.steps[" + i + "].name");
            String stepKind = configuredStep.normalizedKind();

            if (!stepNames.add(stepName)) {
                throw new ConfigException("JobConfig contains duplicate step name '" + stepName + "'. Step names must be unique.");
            }

            if (!"standard".equals(stepKind) && !"custom".equals(stepKind)) {
                throw new ConfigException("JobConfig step '" + stepName + "' has unsupported kind '" + configuredStep.getKind()
                        + "'. Supported values: standard, custom.");
            }

            JobConfig.JobStepConfig resolvedStep = new JobConfig.JobStepConfig();
            resolvedStep.setName(stepName);
            resolvedStep.setKind(stepKind);

            if ("custom".equals(stepKind)) {
                if (configuredStep.getCustom() == null) {
                    throw new ConfigException("JobConfig step '" + stepName + "' with kind 'custom' must define steps[].custom.");
                }
                String customType = requireNonBlank(configuredStep.getCustom().getType(), "JobConfig step '" + stepName + "' custom.type");
                if (hasText(configuredStep.getSource()) || hasText(configuredStep.getTarget())) {
                    throw new ConfigException("JobConfig step '" + stepName + "' with kind 'custom' must not define source/target."
                            + " Use standard steps for source-target mappings.");
                }
                if (configuredStep.getSkipPolicy() != null && configuredStep.getSkipPolicy().isEnabled()) {
                    throw new ConfigException("JobConfig step '" + stepName + "' with kind 'custom' cannot enable skipPolicy in this slice.");
                }
                if (configuredStep.getRetryPolicy() != null && configuredStep.getRetryPolicy().isEnabled()) {
                    throw new ConfigException("JobConfig step '" + stepName + "' with kind 'custom' cannot enable retryPolicy in this slice.");
                }
                JobConfig.CustomStepConfig customStepConfig = new JobConfig.CustomStepConfig();
                customStepConfig.setType(customType);
                customStepConfig.setPublish(configuredStep.getCustom().getPublish());
                customStepConfig.setConsume(configuredStep.getCustom().getConsume());
                customStepConfig.setOnResult(configuredStep.getCustom().getOnResult());
                customStepConfig.setConfig(configuredStep.getCustom().getConfig());
                resolvedStep.setCustom(customStepConfig);
                resolvedSteps.add(resolvedStep);
                continue;
            }

            String sourceName = requireNonBlank(configuredStep.getSource(), "JobConfig.steps[" + i + "].source");
            String targetName = requireNonBlank(configuredStep.getTarget(), "JobConfig.steps[" + i + "].target");
            if (configuredStep.getCustom() != null) {
                throw new ConfigException("JobConfig step '" + stepName + "' with kind 'standard' must not define steps[].custom.");
            }
            if (!sourceNames.contains(sourceName)) {
                throw new ConfigException("JobConfig step '" + stepName + "' references unknown source '" + sourceName + "'.");
            }
            if (!targetNames.contains(targetName)) {
                throw new ConfigException("JobConfig step '" + stepName + "' references unknown target '" + targetName + "'.");
            }

            ensureProcessorMappingExists(processorConfig, stepName, sourceName, targetName);

            JobConfig.SkipPolicyConfig normalizedSkipPolicy = normalizeAndValidateSkipPolicy(
                    configuredStep.getSkipPolicy(),
                    stepName,
                    sourcesByName.get(sourceName)
            );
            JobConfig.RetryPolicyConfig normalizedRetryPolicy = normalizeAndValidateRetryPolicy(
                    configuredStep.getRetryPolicy(),
                    stepName
            );
            if (normalizedSkipPolicy != null && normalizedRetryPolicy != null) {
                throw new ConfigException("JobConfig step '" + stepName + "' configures both skipPolicy and retryPolicy. This B2 first slice does not support combining those modes.");
            }

            resolvedStep.setSource(sourceName);
            resolvedStep.setTarget(targetName);
            resolvedStep.setSkipPolicy(normalizedSkipPolicy);
            resolvedStep.setRetryPolicy(normalizedRetryPolicy);
            resolvedSteps.add(resolvedStep);
        }

        return List.copyOf(resolvedSteps);
    }

    void ensureProcessorMappingExists(ProcessorConfig processorConfig,
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Map<String, SourceConfig> mapSourcesByName(SourceWrapper sourceWrapper) {
        Map<String, SourceConfig> sourcesByName = new LinkedHashMap<>();
        if (sourceWrapper == null || sourceWrapper.getSources() == null) {
            return sourcesByName;
        }
        for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
            if (sourceConfig != null && sourceConfig.getSourceName() != null && !sourceConfig.getSourceName().isBlank()) {
                sourcesByName.put(sourceConfig.getSourceName().trim(), sourceConfig);
            }
        }
        return sourcesByName;
    }

    private static JobConfig.SkipPolicyConfig normalizeAndValidateSkipPolicy(JobConfig.SkipPolicyConfig skipPolicy,
                                                                              String stepName,
                                                                              SourceConfig sourceConfig) {
        if (skipPolicy == null || !skipPolicy.isEnabled()) {
            return null;
        }

        if (!(sourceConfig instanceof CsvSourceConfig)) {
            throw new ConfigException("JobConfig step '" + stepName + "' enables skipPolicy, but skipPolicy is currently supported only for CSV sources.");
        }

        Integer skipLimit = skipPolicy.getSkipLimit();
        if (skipLimit == null || skipLimit <= 0) {
            throw new ConfigException("JobConfig step '" + stepName + "' skipPolicy.skipLimit must be a positive integer when skipPolicy.enabled=true.");
        }

        List<String> normalizedCategories = normalizeAndValidateSkipCategories(skipPolicy.getSkippableCategories(), stepName);
        List<String> configuredExceptions = skipPolicy.getSkippableExceptions();
        if (normalizedCategories.isEmpty() && (configuredExceptions == null || configuredExceptions.isEmpty())) {
            throw new ConfigException("JobConfig step '" + stepName + "' skipPolicy must define at least one of skipPolicy.skippableCategories or skipPolicy.skippableExceptions when skipPolicy.enabled=true.");
        }

        List<String> normalizedExceptions = new ArrayList<>();
        if (configuredExceptions != null) {
            for (String configuredException : configuredExceptions) {
                String className = requireNonBlank(configuredException, "JobConfig step '" + stepName + "' skipPolicy.skippableExceptions[]");
                Class<?> exceptionClass;
                try {
                    exceptionClass = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    throw new ConfigException("JobConfig step '" + stepName + "' skipPolicy.skippableExceptions contains unknown class '" + className + "'.", e);
                }
                if (!Throwable.class.isAssignableFrom(exceptionClass)) {
                    throw new ConfigException("JobConfig step '" + stepName + "' skipPolicy.skippableExceptions class '" + className + "' must extend Throwable.");
                }
                normalizedExceptions.add(exceptionClass.getName());
            }
        }

        JobConfig.SkipPolicyConfig normalized = new JobConfig.SkipPolicyConfig();
        normalized.setEnabled(true);
        normalized.setSkipLimit(skipLimit);
        normalized.setSkippableCategories(normalizedCategories.isEmpty() ? List.of() : List.copyOf(normalizedCategories));
        normalized.setSkippableExceptions(List.copyOf(normalizedExceptions));
        return normalized;
    }

    private static List<String> normalizeAndValidateSkipCategories(List<String> configuredCategories, String stepName) {
        if (configuredCategories == null || configuredCategories.isEmpty()) {
            return List.of();
        }

        List<String> normalizedCategories = new ArrayList<>();
        for (String configuredCategory : configuredCategories) {
            String categoryToken = requireNonBlank(configuredCategory, "JobConfig step '" + stepName + "' skipPolicy.skippableCategories[]");
            EtlErrorCategory category = resolveEtlErrorCategory(categoryToken, stepName, "skipPolicy.skippableCategories");
            normalizedCategories.add(category.logValue());
        }
        return normalizedCategories;
    }

    private static EtlErrorCategory resolveEtlErrorCategory(String categoryToken, String stepName, String propertyPath) {
        Optional<EtlErrorCategory> resolvedCategory = EtlErrorCategory.fromToken(categoryToken);
        if (resolvedCategory.isPresent()) {
            return resolvedCategory.get();
        }
        throw new ConfigException("JobConfig step '" + stepName + "' " + propertyPath + " contains unknown ETL category '"
                + categoryToken + "'. Supported categories are: " + supportedSkipCategories());
    }

    private static String supportedSkipCategories() {
        List<String> values = new ArrayList<>();
        for (EtlErrorCategory category : EtlErrorCategory.values()) {
            values.add(category.logValue());
        }
        return String.join(", ", values);
    }

    private static JobConfig.RetryPolicyConfig normalizeAndValidateRetryPolicy(JobConfig.RetryPolicyConfig retryPolicy,
                                                                                String stepName) {
        if (retryPolicy == null || !retryPolicy.isEnabled()) {
            return null;
        }

        Integer maxAttempts = retryPolicy.getMaxAttempts();
        if (maxAttempts == null || maxAttempts < 2) {
            throw new ConfigException("JobConfig step '" + stepName + "' retryPolicy.maxAttempts must be an integer >= 2 when retryPolicy.enabled=true.");
        }

        Long backoffMs = retryPolicy.getBackoffMs();
        if (backoffMs == null || backoffMs < 0) {
            throw new ConfigException("JobConfig step '" + stepName + "' retryPolicy.backoffMs must be a non-negative integer when retryPolicy.enabled=true.");
        }

        List<String> normalizedCategories = normalizeAndValidateRetryCategories(retryPolicy.getRetryableCategories(), stepName);
        List<String> configuredExceptions = retryPolicy.getRetryableExceptions();
        if (normalizedCategories.isEmpty() && (configuredExceptions == null || configuredExceptions.isEmpty())) {
            throw new ConfigException("JobConfig step '" + stepName + "' retryPolicy must define at least one of retryPolicy.retryableCategories or retryPolicy.retryableExceptions when retryPolicy.enabled=true.");
        }

        List<String> normalizedExceptions = new ArrayList<>();
        if (configuredExceptions != null) {
            for (String configuredException : configuredExceptions) {
                String className = requireNonBlank(configuredException, "JobConfig step '" + stepName + "' retryPolicy.retryableExceptions[]");
                Class<?> exceptionClass;
                try {
                    exceptionClass = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    throw new ConfigException("JobConfig step '" + stepName + "' retryPolicy.retryableExceptions contains unknown class '" + className + "'.", e);
                }
                if (!Throwable.class.isAssignableFrom(exceptionClass)) {
                    throw new ConfigException("JobConfig step '" + stepName + "' retryPolicy.retryableExceptions class '" + className + "' must extend Throwable.");
                }
                normalizedExceptions.add(exceptionClass.getName());
            }
        }

        JobConfig.RetryPolicyConfig normalized = new JobConfig.RetryPolicyConfig();
        normalized.setEnabled(true);
        normalized.setMaxAttempts(maxAttempts);
        normalized.setBackoffMs(backoffMs);
        normalized.setRetryableCategories(normalizedCategories.isEmpty() ? List.of() : List.copyOf(normalizedCategories));
        normalized.setRetryableExceptions(List.copyOf(normalizedExceptions));
        return normalized;
    }

    private static List<String> normalizeAndValidateRetryCategories(List<String> configuredCategories, String stepName) {
        if (configuredCategories == null || configuredCategories.isEmpty()) {
            return List.of();
        }

        List<String> normalizedCategories = new ArrayList<>();
        for (String configuredCategory : configuredCategories) {
            String categoryToken = requireNonBlank(configuredCategory, "JobConfig step '" + stepName + "' retryPolicy.retryableCategories[]");
            EtlErrorCategory category = resolveEtlErrorCategory(categoryToken, stepName, "retryPolicy.retryableCategories");
            normalizedCategories.add(category.logValue());
        }
        return normalizedCategories;
    }
}

