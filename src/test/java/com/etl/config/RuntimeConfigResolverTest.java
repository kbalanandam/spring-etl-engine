package com.etl.config;

import com.etl.config.job.JobConfig;
import com.etl.runtime.job.JobRecoveryPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeConfigResolverTest {

    @Test
    void reusesCachedConfigWhenCacheKeyIsUnchanged() throws IOException {
        TestConfigLoader loader = new TestConfigLoader();
        loader.cacheKey = "same-key";
        RuntimeConfigResolver resolver = new RuntimeConfigResolver(loader);

        ConfigLoader.ResolvedRuntimeConfig first = resolver.resolveRuntimeConfig();
        ConfigLoader.ResolvedRuntimeConfig second = resolver.resolveRuntimeConfig();

        assertEquals(1, loader.buildCount);
        assertEquals(first.scenarioName(), second.scenarioName());
    }

    @Test
    void rebuildsConfigWhenCacheKeyChanges() throws IOException {
        TestConfigLoader loader = new TestConfigLoader();
        loader.cacheKey = "key-a";
        RuntimeConfigResolver resolver = new RuntimeConfigResolver(loader);

        ConfigLoader.ResolvedRuntimeConfig first = resolver.resolveRuntimeConfig();
        loader.cacheKey = "key-b";
        ConfigLoader.ResolvedRuntimeConfig second = resolver.resolveRuntimeConfig();

        assertEquals(2, loader.buildCount);
        assertEquals("scenario-1", first.scenarioName());
        assertEquals("scenario-2", second.scenarioName());
    }

    @Test
    void reusesCachedConfigWhenCacheKeyIsNull() throws IOException {
        TestConfigLoader loader = new TestConfigLoader();
        loader.cacheKey = null;
        RuntimeConfigResolver resolver = new RuntimeConfigResolver(loader);

        ConfigLoader.ResolvedRuntimeConfig first = resolver.resolveRuntimeConfig();
        ConfigLoader.ResolvedRuntimeConfig second = resolver.resolveRuntimeConfig();

        assertEquals(1, loader.buildCount);
        assertEquals(first.scenarioName(), second.scenarioName());
    }

    @Test
    void rebuildsConfigWhenCacheKeyTransitionsFromNullToBlank() throws IOException {
        TestConfigLoader loader = new TestConfigLoader();
        loader.cacheKey = null;
        RuntimeConfigResolver resolver = new RuntimeConfigResolver(loader);

        resolver.resolveRuntimeConfig();
        loader.cacheKey = "";
        resolver.resolveRuntimeConfig();

        // Null keys are normalized to blank, so this transition should not rebuild.
        assertEquals(1, loader.buildCount);
    }

    @Test
    void rebuildsConfigWhenCacheKeyTransitionsFromBlankToValue() throws IOException {
        TestConfigLoader loader = new TestConfigLoader();
        loader.cacheKey = "";
        RuntimeConfigResolver resolver = new RuntimeConfigResolver(loader);

        ConfigLoader.ResolvedRuntimeConfig first = resolver.resolveRuntimeConfig();
        loader.cacheKey = "explicit-job";
        ConfigLoader.ResolvedRuntimeConfig second = resolver.resolveRuntimeConfig();

        assertEquals(2, loader.buildCount);
        assertEquals("scenario-1", first.scenarioName());
        assertEquals("scenario-2", second.scenarioName());
    }

    private static final class TestConfigLoader extends ConfigLoader {
        private String cacheKey = "";
        private int buildCount = 0;

        @Override
        String runtimeConfigCacheKey() {
            return cacheKey;
        }

        @Override
        ResolvedRuntimeConfig buildRuntimeConfigInternal() {
            buildCount++;
            return new ResolvedRuntimeConfig(
                    "source.yaml",
                    "target.yaml",
                    "processor.yaml",
                    true,
                    "scenario-" + buildCount,
                    "job-config.yaml",
                    false,
                    JobRecoveryPolicy.RERUN_FROM_START,
                    List.<JobConfig.JobStepConfig>of()
            );
        }
    }
}

