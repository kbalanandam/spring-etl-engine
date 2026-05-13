package com.etl.common.util;

import com.etl.config.job.JobConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates selected-job generated-model naming guardrails before runtime class resolution
 * or build-time source generation proceeds.
 *
 * <p>This validator keeps runtime and build-time enforcement aligned for the active
 * explicit job contract:</p>
 * <ul>
 *     <li>selected logical source names must be unique within the selected job</li>
 *     <li>selected logical target names must be unique within the selected job</li>
 *     <li>cross-side logical-name reuse is only valid for a downstream-readable handoff
 *     where a target is produced before a later step consumes the same logical name as a source</li>
 *     <li>generated class names must not collide within the same package after normalization</li>
 *     <li>job-scoped {@code packageName} values must align with the derived package for the selected job</li>
 * </ul>
 */
public final class SelectedJobNamingValidator {

    private SelectedJobNamingValidator() {
    }

    public static void validate(String jobName,
                                SourceWrapper sourceWrapper,
                                TargetWrapper targetWrapper,
                                List<JobConfig.JobStepConfig> steps) {
        Objects.requireNonNull(sourceWrapper, "sourceWrapper must not be null.");
        Objects.requireNonNull(targetWrapper, "targetWrapper must not be null.");
        Objects.requireNonNull(steps, "steps must not be null.");
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalStateException("Selected job naming validation requires a non-blank job name.");
        }

        Set<String> selectedSourceNames = new LinkedHashSet<>();
        Set<String> selectedTargetNames = new LinkedHashSet<>();
        Map<String, Integer> firstProducedStepIndexByName = new LinkedHashMap<>();
        Map<String, Integer> firstConsumedStepIndexByName = new LinkedHashMap<>();

        for (int i = 0; i < steps.size(); i++) {
            JobConfig.JobStepConfig step = Objects.requireNonNull(steps.get(i), "Job step must not be null.");
            String sourceName = requireNonBlank(step.getSource(), "steps[" + i + "].source");
            String targetName = requireNonBlank(step.getTarget(), "steps[" + i + "].target");
            selectedSourceNames.add(sourceName);
            selectedTargetNames.add(targetName);
            firstConsumedStepIndexByName.putIfAbsent(sourceName, i);
            firstProducedStepIndexByName.putIfAbsent(targetName, i);
        }

        Map<String, SourceConfig> selectedSourcesByName = selectedSourcesByName(sourceWrapper, selectedSourceNames, jobName);
        Map<String, TargetConfig> selectedTargetsByName = selectedTargetsByName(targetWrapper, selectedTargetNames, jobName);

        validateCrossSideReuse(jobName, selectedSourceNames, selectedTargetNames, firstProducedStepIndexByName, firstConsumedStepIndexByName);
        validateSourceGeneratedClassCollisions(jobName, selectedSourcesByName.values());
        validateTargetGeneratedClassCollisions(jobName, selectedTargetsByName.values());
    }

    private static Map<String, SourceConfig> selectedSourcesByName(SourceWrapper sourceWrapper,
                                                                   Set<String> selectedSourceNames,
                                                                   String jobName) {
        String expectedPackage = JobScopedPackageNameResolver.resolveSourcePackage(jobName);
        Map<String, SourceConfig> selectedByName = new LinkedHashMap<>();
        if (sourceWrapper.getSources() == null) {
            return selectedByName;
        }

        for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
            if (sourceConfig == null || !selectedSourceNames.contains(sourceConfig.getSourceName())) {
                continue;
            }

            String sourceName = requireNonBlank(sourceConfig.getSourceName(), "sourceName");
            validateDerivedPackageAlignment(sourceConfig.getPackageName(), expectedPackage, "source", sourceName);
            SourceConfig previous = selectedByName.putIfAbsent(sourceName, sourceConfig);
            if (previous != null) {
                throw new IllegalStateException("Selected job '" + jobName + "' contains duplicate sourceName '" + sourceName
                        + "'. Logical source names must be unique within the selected job.");
            }
        }
        return selectedByName;
    }

    private static Map<String, TargetConfig> selectedTargetsByName(TargetWrapper targetWrapper,
                                                                   Set<String> selectedTargetNames,
                                                                   String jobName) {
        String expectedPackage = JobScopedPackageNameResolver.resolveTargetPackage(jobName);
        Map<String, TargetConfig> selectedByName = new LinkedHashMap<>();
        if (targetWrapper.getTargets() == null) {
            return selectedByName;
        }

        for (TargetConfig targetConfig : targetWrapper.getTargets()) {
            if (targetConfig == null || !selectedTargetNames.contains(targetConfig.getTargetName())) {
                continue;
            }

            String targetName = requireNonBlank(targetConfig.getTargetName(), "targetName");
            validateDerivedPackageAlignment(targetConfig.getPackageName(), expectedPackage, "target", targetName);
            TargetConfig previous = selectedByName.putIfAbsent(targetName, targetConfig);
            if (previous != null) {
                throw new IllegalStateException("Selected job '" + jobName + "' contains duplicate targetName '" + targetName
                        + "'. Logical target names must be unique within the selected job.");
            }
        }
        return selectedByName;
    }

    private static void validateDerivedPackageAlignment(String packageName,
                                                        String expectedPackage,
                                                        String configType,
                                                        String configName) {
        if (packageName == null || packageName.isBlank()) {
            return;
        }

        String trimmed = packageName.trim();
        if (GeneratedModelNamingPolicy.usesDerivedJobScopedNaming(trimmed) && !expectedPackage.equals(trimmed)) {
            throw new IllegalStateException(configType + " config '" + configName + "' declares packageName='" + trimmed
                    + "' but the selected job requires derived " + configType + " package '" + expectedPackage + "'.");
        }
    }

    private static void validateCrossSideReuse(String jobName,
                                               Set<String> selectedSourceNames,
                                               Set<String> selectedTargetNames,
                                               Map<String, Integer> firstProducedStepIndexByName,
                                               Map<String, Integer> firstConsumedStepIndexByName) {
        Set<String> reusedNames = new LinkedHashSet<>(selectedSourceNames);
        reusedNames.retainAll(selectedTargetNames);

        for (String logicalName : reusedNames) {
            int producedIndex = firstProducedStepIndexByName.getOrDefault(logicalName, Integer.MAX_VALUE);
            int consumedIndex = firstConsumedStepIndexByName.getOrDefault(logicalName, Integer.MAX_VALUE);
            if (consumedIndex == producedIndex) {
                throw new IllegalStateException("Selected job '" + jobName + "' reuses logical name '" + logicalName
                        + "' as both source and target in the same ordered step. Intermediate handoff names must be produced first and consumed only by a later step.");
            }
            if (consumedIndex < producedIndex) {
                throw new IllegalStateException("Selected job '" + jobName + "' reuses logical name '" + logicalName
                        + "' before it is produced. Intermediate handoff names must first appear as a target and only later as a downstream source.");
            }
        }
    }

    private static void validateSourceGeneratedClassCollisions(String jobName,
                                                               Iterable<SourceConfig> sourceConfigs) {
        Map<String, String> classToLogicalName = new LinkedHashMap<>();
        for (SourceConfig sourceConfig : sourceConfigs) {
            for (String generatedClassName : generatedSourceClassNames(sourceConfig)) {
                registerGeneratedClass(jobName, "source", sourceConfig.getSourceName(), generatedClassName, classToLogicalName);
            }
        }
    }

    private static void validateTargetGeneratedClassCollisions(String jobName,
                                                               Iterable<TargetConfig> targetConfigs) {
        Map<String, String> classToLogicalName = new LinkedHashMap<>();
        for (TargetConfig targetConfig : targetConfigs) {
            for (String generatedClassName : generatedTargetClassNames(targetConfig)) {
                registerGeneratedClass(jobName, "target", targetConfig.getTargetName(), generatedClassName, classToLogicalName);
            }
        }
    }

    private static void registerGeneratedClass(String jobName,
                                               String configType,
                                               String logicalName,
                                               String generatedClassName,
                                               Map<String, String> classToLogicalName) {
        String previousLogicalName = classToLogicalName.putIfAbsent(generatedClassName, logicalName);
        if (previousLogicalName != null && !previousLogicalName.equals(logicalName)) {
            throw new IllegalStateException("Selected job '" + jobName + "' contains a generated-model naming collision for "
                    + configType + " configs '" + previousLogicalName + "' and '" + logicalName + "': " + generatedClassName
                    + ". Adjust the logical names so the derived generated class names stay unique.");
        }
    }

    private static List<String> generatedSourceClassNames(SourceConfig sourceConfig) {
        String packageName = requireNonBlank(sourceConfig.getPackageName(), "packageName");
        List<String> classNames = new ArrayList<>();
        classNames.add(packageName + "." + GeneratedModelNamingPolicy.resolveSourceSimpleClassName(sourceConfig));
        if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig) {
            classNames.add(packageName + "." + GeneratedModelNamingPolicy.resolveSourceRootSimpleClassName(xmlSourceConfig));
        }
        return classNames.stream().distinct().toList();
    }

    private static List<String> generatedTargetClassNames(TargetConfig targetConfig) {
        String packageName = requireNonBlank(targetConfig.getPackageName(), "packageName");
        List<String> classNames = new ArrayList<>();
        classNames.add(packageName + "." + GeneratedModelNamingPolicy.resolveTargetWriteSimpleClassName(targetConfig));
        if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
            classNames.add(packageName + "." + GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(xmlTargetConfig));
        }
        return classNames.stream().distinct().toList();
    }

    private static String requireNonBlank(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property '" + propertyName + "' for selected-job naming validation.");
        }
        return value.trim();
    }
}

