package com.etl.step;

import com.etl.config.job.JobConfig;
import com.etl.exception.config.ConfigException;
import com.etl.extension.ExtensionConflictPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves custom step handlers from registered providers.
 *
 * <p>In production, providers are discovered lazily from the Spring bean factory by
 * matching {@link CustomStepBinding#type()} to `steps[].custom.type`. Resolved bindings
 * are cached per normalized type so repeated steps do not repeat metadata scans.</p>
 */
@Component
public class DynamicCustomStepFactory {

    private static final Logger logger = LoggerFactory.getLogger(DynamicCustomStepFactory.class);

    private final Map<String, CustomStepProvider> providersByType;
    private final ListableBeanFactory beanFactory;
    private final ConcurrentMap<String, ProviderRegistration> providersByTypeCache;

    /**
     * Runtime constructor used by Spring. Provider beans are resolved lazily by type on demand.
     */
    @Autowired
    public DynamicCustomStepFactory(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        this.providersByType = null;
        this.providersByTypeCache = new ConcurrentHashMap<>();
    }

    /**
     * Test-friendly constructor for direct provider registration without a bean factory.
     */
    public DynamicCustomStepFactory(List<CustomStepProvider> providers) {
        this.beanFactory = null;
        this.providersByTypeCache = new ConcurrentHashMap<>();
        List<ExtensionConflictPolicy.Candidate<String, CustomStepProvider>> candidates = new ArrayList<>();
        for (CustomStepProvider provider : providers == null ? List.<CustomStepProvider>of() : providers) {
            if (provider == null) {
                continue;
            }
            CustomStepBinding binding = provider.getClass().getAnnotation(CustomStepBinding.class);
            if (binding == null) {
                throw new ConfigException("Custom step provider '" + provider.providerId()
                        + "' must declare @CustomStepBinding(type=...) for custom step registration.");
            }
            String stepType = normalizeType(binding.type());
            if (stepType.isBlank()) {
                throw new ConfigException("Custom step provider '" + provider.providerId()
                        + "' declares a blank @CustomStepBinding type.");
            }
            candidates.add(new ExtensionConflictPolicy.Candidate<>(
                    stepType,
                    provider,
                    new ExtensionConflictPolicy.ProviderMetadata(provider.providerId(), binding.override())
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

    /**
     * Resolves and builds a handler for one configured custom step.
     */
    public CustomStepHandler getHandler(String stepName, JobConfig.CustomStepConfig customConfig) {
        if (customConfig == null) {
            throw new ConfigException("Job step '" + stepName + "' must define custom configuration.");
        }
        String customType = normalizeType(customConfig.getType());
        if (customType.isBlank()) {
            throw new ConfigException("Job step '" + stepName + "' custom.type must be non-blank.");
        }

        CustomStepProvider provider = resolveProvider(customType);
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

    /**
     * Converts configured type aliases into a canonical lowercase key.
     */
    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the provider for a type from eager map or lazy bean-factory cache.
     */
    private CustomStepProvider resolveProvider(String customType) {
        if (providersByType != null) {
            return providersByType.get(customType);
        }
        ProviderRegistration registration = providersByTypeCache.computeIfAbsent(customType, this::resolveProviderRegistration);
        return registration == null ? null : beanFactory.getBean(registration.beanName(), CustomStepProvider.class);
    }

    /**
     * Scans provider beans, filters by bound type, and resolves override conflicts.
     */
    private ProviderRegistration resolveProviderRegistration(String customType) {
        String[] beanNames = beanFactory.getBeanNamesForType(CustomStepProvider.class, true, false);
        if (beanNames == null || beanNames.length == 0) {
            return null;
        }

        List<ExtensionConflictPolicy.Candidate<String, ProviderRegistration>> candidates = new ArrayList<>();
        for (String beanName : beanNames) {
            CustomStepBinding binding = beanFactory.findAnnotationOnBean(beanName, CustomStepBinding.class);
            if (binding == null) {
                throw new ConfigException("Custom step provider bean '" + beanName
                        + "' must declare @CustomStepBinding(type=...) for custom step registration.");
            }

            String boundType = normalizeType(binding.type());
            if (boundType.isBlank()) {
                throw new ConfigException("Custom step provider bean '" + beanName + "' declares a blank @CustomStepBinding type.");
            }
            if (!customType.equals(boundType)) {
                continue;
            }
            String providerId = providerIdForBean(beanName);
            candidates.add(new ExtensionConflictPolicy.Candidate<>(
                    customType,
                    new ProviderRegistration(beanName, providerId),
                    new ExtensionConflictPolicy.ProviderMetadata(providerId, binding.override())
            ));
        }

        Map<String, ProviderRegistration> resolved = ExtensionConflictPolicy.resolve(candidates, new ExtensionConflictPolicy.ConflictReporter<>() {
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

        return resolved.get(customType);
    }

    /**
     * Uses implementation FQCN when available so conflict reports stay stable across bean names.
     */
    private String providerIdForBean(String beanName) {
        Class<?> beanType = beanFactory.getType(beanName, false);
        return beanType == null ? beanName : beanType.getName();
    }

    private record ProviderRegistration(String beanName, String providerId) {
    }
}
