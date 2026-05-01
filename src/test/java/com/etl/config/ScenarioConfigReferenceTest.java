package com.etl.config;

import com.etl.config.job.JobConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.XmlTargetConfig;
import com.etl.config.target.TargetWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioConfigReferenceTest {

    private static final Path SCENARIO_ROOT = scenarioRootPath();

    @Test
    void allTrackedScenarioFoldersContainResolvableJobReferencedFiles() throws IOException {
        List<String> scenarioNames = List.of(
                "csv-validation-reject-archive",
                "csv-to-sqlserver",
                "relational-to-relational",
                "xml-to-csv-events",
                "xml-nested-to-csv-tag-validation",
                "xml-nested-tag-validation",
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
                  assertFalse(jobConfig.getSteps() == null || jobConfig.getSteps().isEmpty(), () -> "Missing explicit steps for scenario: " + scenarioName);

                  SourceWrapper sourceWrapper = mapper.readValue(scenarioDirectory.resolve(jobConfig.getSourceConfigPath()).toFile(), SourceWrapper.class);
                  TargetWrapper targetWrapper = mapper.readValue(scenarioDirectory.resolve(jobConfig.getTargetConfigPath()).toFile(), TargetWrapper.class);
                  Set<String> sourceNames = sourceWrapper.getSources().stream().map(SourceConfig::getSourceName).collect(Collectors.toSet());
                  Set<String> targetNames = targetWrapper.getTargets().stream().map(TargetConfig::getTargetName).collect(Collectors.toSet());

                  sourceWrapper.getSources().stream()
                          .filter(XmlSourceConfig.class::isInstance)
                          .map(XmlSourceConfig.class::cast)
                          .filter(source -> source.getModelDefinitionPath() != null && !source.getModelDefinitionPath().isBlank())
                          .forEach(source -> assertTrue(
                                  Files.isRegularFile(scenarioDirectory.resolve(source.getModelDefinitionPath())),
                                  () -> "Missing XML source model definition '" + source.getModelDefinitionPath() + "' for scenario: " + scenarioName
                          ));
                  targetWrapper.getTargets().stream()
                          .filter(XmlTargetConfig.class::isInstance)
                          .map(XmlTargetConfig.class::cast)
                          .filter(target -> target.getModelDefinitionPath() != null && !target.getModelDefinitionPath().isBlank())
                          .forEach(target -> assertTrue(
                                  Files.isRegularFile(scenarioDirectory.resolve(target.getModelDefinitionPath())),
                                  () -> "Missing XML target model definition '" + target.getModelDefinitionPath() + "' for scenario: " + scenarioName
                          ));

            assertTrue(Files.isRegularFile(scenarioDirectory.resolve(jobConfig.getSourceConfigPath())),
                    () -> "Missing source config for scenario: " + scenarioName);
            assertTrue(Files.isRegularFile(scenarioDirectory.resolve(jobConfig.getTargetConfigPath())),
                    () -> "Missing target config for scenario: " + scenarioName);
            assertTrue(Files.isRegularFile(scenarioDirectory.resolve(jobConfig.getProcessorConfigPath())),
                    () -> "Missing processor config for scenario: " + scenarioName);

                  for (JobConfig.JobStepConfig step : jobConfig.getSteps()) {
                    assertTrue(step.getName() != null && !step.getName().isBlank(), () -> "Blank step name for scenario: " + scenarioName);
                    assertTrue(sourceNames.contains(step.getSource()), () -> "Unknown step source '" + step.getSource() + "' for scenario: " + scenarioName);
                    assertTrue(targetNames.contains(step.getTarget()), () -> "Unknown step target '" + step.getTarget() + "' for scenario: " + scenarioName);
                  }
        }
    }

    private static Path scenarioRootPath() {
        Path mainResourceRoot = Path.of("src", "main", "resources", "config-scenarios").toAbsolutePath().normalize();
        if (Files.isDirectory(mainResourceRoot)) {
            return mainResourceRoot;
        }
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


