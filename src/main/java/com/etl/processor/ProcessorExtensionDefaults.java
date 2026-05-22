package com.etl.processor;

import com.etl.enums.ModelFormat;
import com.etl.extension.ExtensionConflictPolicy;
import com.etl.processor.transform.ProcessorFieldTransform;
import com.etl.processor.validation.ProcessorValidationRule;
import com.etl.processor.spi.BuiltInProcessorExtensionProvider;
import com.etl.processor.spi.ProcessorExtensionProvider;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Built-in extension registrations for non-Spring/manual construction paths.
 */
public final class ProcessorExtensionDefaults {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorExtensionDefaults.class);

    private static final ProcessorExtensionProvider BUILT_IN_PROVIDER = new BuiltInProcessorExtensionProvider();

    private ProcessorExtensionDefaults() {
    }

    public static List<ProcessorValidationRule> defaultValidationRules(FileIngestionRuntimeSupport runtimeSupport) {
        return resolveValidationRules(discoverProviders(), runtimeSupport);
    }

    static List<ProcessorValidationRule> resolveValidationRules(List<ProcessorExtensionProvider> providers,
                                                                FileIngestionRuntimeSupport runtimeSupport) {
        List<ExtensionConflictPolicy.Candidate<RuleKey, ProcessorValidationRule>> candidates = new ArrayList<>();
        for (ProcessorExtensionProvider provider : providers == null ? List.<ProcessorExtensionProvider>of() : providers) {
            for (ProcessorValidationRule rule : provider.validationRules(runtimeSupport)) {
                if (rule == null) {
                    continue;
                }
                for (RuleKey key : ruleKeys(rule)) {
                    candidates.add(new ExtensionConflictPolicy.Candidate<>(
                            key,
                            rule,
                            new ExtensionConflictPolicy.ProviderMetadata(provider.providerId(), provider.isOverride())
                    ));
                }
            }
        }
        Map<RuleKey, ProcessorValidationRule> selectedRules = ExtensionConflictPolicy.resolve(candidates, ruleConflictReporter());
        return deduplicateByIdentity(selectedRules.values());
    }

    public static List<ProcessorFieldTransform> defaultTransforms() {
        return resolveTransforms(discoverProviders());
    }

    static List<ProcessorFieldTransform> resolveTransforms(List<ProcessorExtensionProvider> providers) {
        List<ExtensionConflictPolicy.Candidate<TransformKey, ProcessorFieldTransform>> candidates = new ArrayList<>();
        for (ProcessorExtensionProvider provider : providers == null ? List.<ProcessorExtensionProvider>of() : providers) {
            for (ProcessorFieldTransform transform : provider.transforms()) {
                if (transform == null) {
                    continue;
                }
                for (TransformKey key : transformKeys(transform)) {
                    candidates.add(new ExtensionConflictPolicy.Candidate<>(
                            key,
                            transform,
                            new ExtensionConflictPolicy.ProviderMetadata(provider.providerId(), provider.isOverride())
                    ));
                }
            }
        }
        Map<TransformKey, ProcessorFieldTransform> selectedTransforms = ExtensionConflictPolicy.resolve(candidates, transformConflictReporter());
        return deduplicateByIdentity(selectedTransforms.values());
    }

    private static List<ProcessorExtensionProvider> discoverProviders() {
        List<ProcessorExtensionProvider> providers = new ArrayList<>();
        providers.add(BUILT_IN_PROVIDER);

        ServiceLoader<ProcessorExtensionProvider> discovered = ServiceLoader.load(ProcessorExtensionProvider.class);
        for (ProcessorExtensionProvider provider : discovered) {
            if (provider.getClass().equals(BuiltInProcessorExtensionProvider.class)) {
                continue;
            }
            providers.add(provider);
        }

        providers.sort(Comparator
                .comparingInt(ProcessorExtensionProvider::order)
                .thenComparing(ProcessorExtensionProvider::providerId)
                .thenComparing(provider -> provider.getClass().getName()));

        if (logger.isDebugEnabled()) {
            logger.debug("Discovered processor extension providers: {}", providers.stream()
                    .map(provider -> provider.providerId() + "(" + provider.getClass().getSimpleName() + ")")
                    .toList());
        }

        return List.copyOf(providers);
    }

    private static ExtensionConflictPolicy.ConflictReporter<RuleKey> ruleConflictReporter() {
        return new ExtensionConflictPolicy.ConflictReporter<>() {
            @Override
            public void onOverride(RuleKey key,
                                   ExtensionConflictPolicy.ProviderMetadata winner,
                                   ExtensionConflictPolicy.ProviderMetadata replaced) {
                logger.info("Processor rule '{}'{} overridden by provider '{}' replacing provider '{}'.",
                        key.ruleType(), formatSuffix(key.sourceFormat()),
                        winner.providerId(), replaced.providerId());
            }

            @Override
            public void onIgnored(RuleKey key,
                                  ExtensionConflictPolicy.ProviderMetadata ignored,
                                  ExtensionConflictPolicy.ProviderMetadata winner) {
                logger.debug("Ignoring non-override processor rule provider '{}' for key '{}'{} because override provider '{}' already registered.",
                        ignored.providerId(), key.ruleType(), formatSuffix(key.sourceFormat()), winner.providerId());
            }

            @Override
            public RuntimeException duplicateFailure(RuleKey key,
                                                     ExtensionConflictPolicy.ProviderMetadata existing,
                                                     ExtensionConflictPolicy.ProviderMetadata candidate) {
                return new IllegalStateException("Multiple processor validation rules registered for type '" + key.ruleType()
                        + "'" + formatSuffix(key.sourceFormat())
                        + " (providers: " + existing.providerId() + ", " + candidate.providerId() + ")"
                        + ". Set exactly one provider with isOverride=true to replace an existing rule.");
            }
        };
    }

    private static ExtensionConflictPolicy.ConflictReporter<TransformKey> transformConflictReporter() {
        return new ExtensionConflictPolicy.ConflictReporter<>() {
            @Override
            public void onOverride(TransformKey key,
                                   ExtensionConflictPolicy.ProviderMetadata winner,
                                   ExtensionConflictPolicy.ProviderMetadata replaced) {
                logger.info("Processor transform '{}'{} overridden by provider '{}' replacing provider '{}'.",
                        key.transformType(), formatSuffix(key.sourceFormat()),
                        winner.providerId(), replaced.providerId());
            }

            @Override
            public void onIgnored(TransformKey key,
                                  ExtensionConflictPolicy.ProviderMetadata ignored,
                                  ExtensionConflictPolicy.ProviderMetadata winner) {
                logger.debug("Ignoring non-override processor transform provider '{}' for key '{}'{} because override provider '{}' already registered.",
                        ignored.providerId(), key.transformType(), formatSuffix(key.sourceFormat()), winner.providerId());
            }

            @Override
            public RuntimeException duplicateFailure(TransformKey key,
                                                     ExtensionConflictPolicy.ProviderMetadata existing,
                                                     ExtensionConflictPolicy.ProviderMetadata candidate) {
                return new IllegalStateException("Multiple processor transforms registered for type '" + key.transformType()
                        + "'" + formatSuffix(key.sourceFormat())
                        + " (providers: " + existing.providerId() + ", " + candidate.providerId() + ")"
                        + ". Set exactly one provider with isOverride=true to replace an existing transform.");
            }
        };
    }

    private static List<RuleKey> ruleKeys(ProcessorValidationRule rule) {
        String ruleType = normalizeType(rule.getRuleType(), "validation rule");
        Set<ModelFormat> scopedFormats = normalizedFormats(rule.supportedSourceFormats(), "validation rule", ruleType);
        if (scopedFormats.isEmpty()) {
            return List.of(new RuleKey(ruleType, null));
        }
        return scopedFormats.stream().map(format -> new RuleKey(ruleType, format)).toList();
    }

    private static List<TransformKey> transformKeys(ProcessorFieldTransform transform) {
        String transformType = normalizeType(transform.getTransformType(), "transform");
        Set<ModelFormat> scopedFormats = normalizedFormats(transform.supportedSourceFormats(), "transform", transformType);
        if (scopedFormats.isEmpty()) {
            return List.of(new TransformKey(transformType, null));
        }
        return scopedFormats.stream().map(format -> new TransformKey(transformType, format)).toList();
    }

    private static String normalizeType(String type, String registrationLabel) {
        if (type == null || type.isBlank()) {
            throw new IllegalStateException("Processor " + registrationLabel + " type must not be blank.");
        }
        return type.trim();
    }

    private static Set<ModelFormat> normalizedFormats(Set<ModelFormat> formats,
                                                      String registrationLabel,
                                                      String type) {
        if (formats == null || formats.isEmpty()) {
            return Set.of();
        }
        Set<ModelFormat> normalized = new HashSet<>();
        for (ModelFormat format : formats) {
            if (format == null) {
                throw new IllegalStateException("Processor " + registrationLabel + " '" + type
                        + "' contains a null source-format scope.");
            }
            normalized.add(format);
        }
        return Set.copyOf(normalized);
    }

    private static String formatSuffix(ModelFormat sourceFormat) {
        return sourceFormat == null ? "" : " for source format '" + sourceFormat.getFormat() + "'";
    }

    private static <T> List<T> deduplicateByIdentity(Iterable<T> registrations) {
        Set<T> seen = new HashSet<>();
        List<T> orderedUnique = new ArrayList<>();
        for (T registration : registrations) {
            if (seen.add(registration)) {
                orderedUnique.add(registration);
            }
        }
        return List.copyOf(orderedUnique);
    }

    private record RuleKey(String ruleType, ModelFormat sourceFormat) {
    }

    private record TransformKey(String transformType, ModelFormat sourceFormat) {
    }
}

