package com.etl.config;

import java.io.IOException;
import java.util.Objects;

/**
 * Resolves and caches one selected runtime configuration per application lifecycle.
 */
final class RuntimeConfigResolver {

    private final ConfigLoader configLoader;
    private volatile ConfigLoader.ResolvedRuntimeConfig cachedRuntimeConfig;
    private volatile String cachedRuntimeConfigKey;

    RuntimeConfigResolver(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    ConfigLoader.ResolvedRuntimeConfig resolveRuntimeConfig() throws IOException {
        ConfigLoader.ResolvedRuntimeConfig existing = cachedRuntimeConfig;
        String currentKey = normalizeCacheKey(configLoader.runtimeConfigCacheKey());
        if (existing != null && Objects.equals(currentKey, cachedRuntimeConfigKey)) {
            return existing;
        }

        synchronized (this) {
            String synchronizedKey = normalizeCacheKey(configLoader.runtimeConfigCacheKey());
            if (cachedRuntimeConfig == null || !Objects.equals(synchronizedKey, cachedRuntimeConfigKey)) {
                cachedRuntimeConfig = buildRuntimeConfig();
                cachedRuntimeConfigKey = synchronizedKey;
            }
            return cachedRuntimeConfig;
        }
    }

    ConfigLoader.ResolvedRuntimeConfig peekCachedRuntimeConfig() {
        return cachedRuntimeConfig;
    }

    private ConfigLoader.ResolvedRuntimeConfig buildRuntimeConfig() throws IOException {
        return configLoader.buildRuntimeConfigInternal();
    }

    private String normalizeCacheKey(String cacheKey) {
        return cacheKey == null ? "" : cacheKey;
    }
}


