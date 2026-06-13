package com.etl.step;

import com.etl.config.job.JobConfig;
import com.etl.exception.config.ConfigException;
import com.etl.extension.ExtensionConflictPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves custom step handlers from registered providers.
 */
@Component
public class DynamicCustomStepFactory {

    private static final Logger logger = LoggerFactory.getLogger(DynamicCustomStepFactory.class);

    private final Map<String, CustomStepProvider> providersByType;

    public DynamicCustomStepFactory(List<CustomStepProvider> providers) {
        List<ExtensionConflictPolicy.Candidate<String, CustomStepProvider>> candidates = new ArrayList<>();
        for (CustomStepProvider provider : providers == null ? List.<CustomStepProvider>of() : providers) {
            if (provider == null) {
                continue;
            }
            String stepType = normalizeType(provider.customType());
            if (stepType.isBlank()) {
                throw new ConfigException("Custom step provider '" + provider.providerId() + "' returned a blank customType().");
            }
            candidates.add(new ExtensionConflictPolicy.Candidate<>(
                    stepType,
                    provider,
                    new ExtensionConflictPolicy.ProviderMetadata(provider.providerId(), provider.isOverride())
            ));
        }

        this.providersByType = ExtensionConflictPolicy.resolve(candidates, new ExtensionConflictPolicy.ConflictReporter<>() {
            @Override
            public void onOverride(String key,
                                   ExtensionConflictPolicy.ProviderMetadata winner,
                                   ExtensionConflictPolicy.ProviderMetadata replaced) {
                logger.info("Custom step type '{}' overridden by provider '{}' replacing provider '{}'.",
                        key, winner.providerId(), replaced.providerId());
            }

            @Override
            public void onIgnored(String key,
                                  ExtensionConflictPolicy.ProviderMetadata ignored,
                                  ExtensionConflictPolicy.ProviderMetadata winner) {
                logger.debug("Ignoring non-override custom step provider '{}' for type '{}' because override provider '{}' is already registered.",
                        ignored.providerId(), key, winner.providerId());
            }

            @Override
            public RuntimeException duplicateFailure(String key,
                                                     ExtensionConflictPolicy.ProviderMetadata existing,
                                                     ExtensionConflictPolicy.ProviderMetadata candidate) {
                return new ConfigException("Multiple custom step providers are registered for type '" + key
                        + "' (providers: " + existing.providerId() + ", " + candidate.providerId() + ")."
                        + " Set exactly one provider with isOverride=true to replace an existing registration.");
            }
        });
    }

    public CustomStepHandler getHandler(String stepName, JobConfig.CustomStepConfig customConfig) {
        if (customConfig == null) {
            throw new ConfigException("Job step '" + stepName + "' must define custom configuration.");
        }
        String customType = normalizeType(customConfig.getType());
        if (customType.isBlank()) {
            throw new ConfigException("Job step '" + stepName + "' custom.type must be non-blank.");
        }

        CustomStepProvider provider = providersByType.get(customType);
        if (provider == null) {
            throw new ConfigException("Job step '" + stepName + "' references unknown custom.type '" + customType + "'."
                    + " Register a CustomStepProvider for that type.");
        }

        try {
            CustomStepHandler handler = provider.createHandler(customConfig);
            if (handler == null) {
                throw new ConfigException("Custom step provider '" + provider.providerId() + "' returned null handler for type '" + customType + "'.");
            }
            return handler;
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException("Failed to create custom step handler for step '" + stepName + "' and type '" + customType + "'.", e);
        }
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }
}

