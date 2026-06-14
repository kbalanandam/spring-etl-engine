package com.etl.config.runtime;

import com.etl.config.ConfigLoader;
import java.io.IOException;
import java.util.Objects;

/**
 * Resolves and caches one selected runtime configuration per application lifecycle.
 */
public final class RuntimeConfigResolver {

    private final ConfigLoader configLoader;
    private volatile ConfigLoader.ResolvedRuntimeConfig cachedRuntimeConfig;
    private volatile String cachedRuntimeConfigKey;

    public RuntimeConfigResolver(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public ConfigLoader.ResolvedRuntimeConfig resolveRuntimeConfig() throws IOException {
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

    public ConfigLoader.ResolvedRuntimeConfig peekCachedRuntimeConfig() {
        return cachedRuntimeConfig;
    }

    private ConfigLoader.ResolvedRuntimeConfig buildRuntimeConfig() throws IOException {
        return configLoader.buildRuntimeConfigInternal();
    }

    private String normalizeCacheKey(String cacheKey) {
        return cacheKey == null ? "" : cacheKey;
    }
}



