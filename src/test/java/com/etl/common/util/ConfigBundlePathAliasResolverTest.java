package com.etl.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigBundlePathAliasResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesLegacyConfigScenariosPathToCanonicalConfigJobsBundleWhenAliasPathDoesNotExist() throws Exception {
        Path canonicalJobConfig = tempDir.resolve("src/main/resources/config-jobs/customer-load/job-config.yaml");
        Files.createDirectories(canonicalJobConfig.getParent());
        Files.writeString(canonicalJobConfig, "name: customer-load\n");

        Path requestedAliasPath = tempDir.resolve("src/main/resources/config-scenarios/customer-load/job-config.yaml");

        assertEquals(canonicalJobConfig.normalize(), ConfigBundlePathAliasResolver.resolveExistingPath(requestedAliasPath));
    }

    @Test
    void resolvesExistingResourceNameAcrossConfigScenariosAndConfigJobs() throws Exception {
        Path resourceRoot = tempDir.resolve("classpath-root");
        Path canonicalJobConfig = resourceRoot.resolve("config-jobs/customer-load/job-config.yaml");
        Files.createDirectories(canonicalJobConfig.getParent());
        Files.writeString(canonicalJobConfig, "name: customer-load\n");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{resourceRoot.toUri().toURL()}, null)) {
            String resolved = ConfigBundlePathAliasResolver.resolveExistingResourceName(
                    classLoader,
                    "config-scenarios/customer-load/job-config.yaml"
            );

            assertEquals("config-jobs/customer-load/job-config.yaml", resolved);
        }
    }

    @Test
    void prefersConfigJobsBundleRootWhenPresentOtherwiseFallsBackToLegacyRoot() throws Exception {
        Path resourceRoot = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourceRoot.resolve("config-scenarios"));
        assertEquals(resourceRoot.resolve("config-scenarios"), ConfigBundlePathAliasResolver.resolveBundleRoot(resourceRoot));

        Files.createDirectories(resourceRoot.resolve("config-jobs"));
        assertEquals(resourceRoot.resolve("config-jobs"), ConfigBundlePathAliasResolver.resolveBundleRoot(resourceRoot));
    }
}
