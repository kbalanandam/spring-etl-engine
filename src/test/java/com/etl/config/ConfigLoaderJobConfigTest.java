package com.etl.config;

import com.etl.config.exception.ConfigException;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigLoaderJobConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsReferencedConfigsFromJobConfigUsingRelativePaths() throws IOException {
        Path sourceConfig = tempDir.resolve("source-config.yaml");
        Path targetConfig = tempDir.resolve("target-config.yaml");
        Path processorConfig = tempDir.resolve("processor-config.yaml");
        Path jobConfig = tempDir.resolve("job-config.yaml");

        Files.writeString(sourceConfig, """
                sources:
                  - format: csv
                    sourceName: Customers
                    packageName: com.etl.model.source
                    filePath: input/customers.csv
                    delimiter: ","
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                """);

        Files.writeString(targetConfig, """
                targets:
                  - format: csv
                    targetName: CustomersOut
                    packageName: com.etl.model.target
                    filePath: output/customers.csv
                    delimiter: ","
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                """);

        Files.writeString(processorConfig, """
                type: default
                mappings:
                  - source: Customers
                    target: CustomersOut
                    fields:
                      - from: id
                        to: id
                      - from: name
                        to: name
                """);

        Files.writeString(jobConfig, """
                name: csv-to-csv-test
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                """);

        ConfigLoader loader = new ConfigLoader();
        ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
        ReflectionTestUtils.setField(loader, "sourceConfigPath", "missing-source.yaml");
        ReflectionTestUtils.setField(loader, "targetConfigPath", "missing-target.yaml");
        ReflectionTestUtils.setField(loader, "processorConfigPath", "missing-processor.yaml");

        SourceWrapper sourceWrapper = loader.sourceWrapper();
        TargetWrapper targetWrapper = loader.targetWrapper();
        ProcessorConfig loadedProcessorConfig = loader.processorConfig();

        assertEquals(1, sourceWrapper.getSources().size());
        assertEquals("Customers", sourceWrapper.getSources().get(0).getSourceName());

        assertEquals(1, targetWrapper.getTargets().size());
        assertEquals("CustomersOut", targetWrapper.getTargets().get(0).getTargetName());

        assertEquals("default", loadedProcessorConfig.getType());
        assertEquals(1, loadedProcessorConfig.getMappings().size());
        assertEquals("Customers", loadedProcessorConfig.getMappings().get(0).getSource());
        assertEquals("CustomersOut", loadedProcessorConfig.getMappings().get(0).getTarget());
    }

    @Test
    void failsFastWhenJobConfigReferencesMissingFiles() throws IOException {
        Path jobConfig = tempDir.resolve("job-config.yaml");
        Files.writeString(jobConfig, """
                name: broken-job
                sourceConfigPath: missing-source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                """);

        ConfigLoader loader = new ConfigLoader();
        ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());

        assertThrows(ConfigException.class, loader::sourceWrapper);
    }
}


