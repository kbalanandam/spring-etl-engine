package com.etl.config;

import java.io.IOException;

/**
 * Resolves and caches one selected runtime configuration per application lifecycle.
 */
final class RuntimeConfigResolver {

    private final ConfigLoader configLoader;
    private volatile ConfigLoader.ResolvedRuntimeConfig cachedRuntimeConfig;

    RuntimeConfigResolver(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    ConfigLoader.ResolvedRuntimeConfig resolveRuntimeConfig() throws IOException {
        ConfigLoader.ResolvedRuntimeConfig existing = cachedRuntimeConfig;
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            if (cachedRuntimeConfig == null) {
                cachedRuntimeConfig = buildRuntimeConfig();
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


