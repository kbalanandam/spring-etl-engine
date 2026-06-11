package com.etl.config;

import java.io.IOException;

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
        String currentKey = configLoader.runtimeConfigCacheKey();
        if (existing != null && currentKey.equals(cachedRuntimeConfigKey)) {
            return existing;
        }

        synchronized (this) {
            String synchronizedKey = configLoader.runtimeConfigCacheKey();
            if (cachedRuntimeConfig == null || !synchronizedKey.equals(cachedRuntimeConfigKey)) {
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
}


