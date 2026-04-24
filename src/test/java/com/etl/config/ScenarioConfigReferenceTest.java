package com.etl.config;

import com.etl.config.job.JobConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioConfigReferenceTest {

    private static final Path SCENARIO_ROOT = scenarioRootPath();

    @Test
    void allTrackedScenarioFoldersContainResolvableJobReferencedFiles() throws IOException {
        List<String> scenarioNames = List.of(
                "csv-to-sqlserver",
                "relational-to-relational",
                "customer-load",
                "department-load",
                "cust-dept-load"
        );

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        for (String scenarioName : scenarioNames) {
            Path scenarioDirectory = SCENARIO_ROOT.resolve(scenarioName);
            assertTrue(Files.isDirectory(scenarioDirectory), () -> "Missing scenario directory: " + scenarioDirectory);

            Path jobConfigPath = scenarioDirectory.resolve("job-config.yaml");
            assertTrue(Files.isRegularFile(jobConfigPath), () -> "Missing job-config.yaml for scenario: " + scenarioName);

            JobConfig jobConfig = mapper.readValue(jobConfigPath.toFile(), JobConfig.class);

            assertTrue(Files.isRegularFile(scenarioDirectory.resolve(jobConfig.getSourceConfigPath())),
                    () -> "Missing source config for scenario: " + scenarioName);
            assertTrue(Files.isRegularFile(scenarioDirectory.resolve(jobConfig.getTargetConfigPath())),
                    () -> "Missing target config for scenario: " + scenarioName);
            assertTrue(Files.isRegularFile(scenarioDirectory.resolve(jobConfig.getProcessorConfigPath())),
                    () -> "Missing processor config for scenario: " + scenarioName);
        }
    }

    private static Path scenarioRootPath() {
        String resourceName = "config-scenarios";
        try {
            return Path.of(Objects.requireNonNull(
                    ScenarioConfigReferenceTest.class.getClassLoader().getResource(resourceName),
                    () -> "Missing test resource: " + resourceName
            ).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to resolve test resource: " + resourceName, e);
        }
    }
}


