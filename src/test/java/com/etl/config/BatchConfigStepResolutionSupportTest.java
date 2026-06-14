package com.etl.config;

import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.TargetConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchConfigStepResolutionSupportTest {

    @Test
    void mapSourcesByNameFailsFastOnDuplicateSourceNames() {
        BatchConfigStepResolutionSupport support = new BatchConfigStepResolutionSupport(processorConfig());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> support.mapSourcesByName(List.of(csvSource("Customers"), csvSource("Customers")))
        );

        assertTrue(exception.getMessage().contains("Duplicate sourceName found in source configuration: Customers"));
    }

    @Test
    void mapTargetsByNameFailsFastOnDuplicateTargetNames() {
        BatchConfigStepResolutionSupport support = new BatchConfigStepResolutionSupport(processorConfig());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> support.mapTargetsByName(List.of(csvTarget("CustomersOut"), csvTarget("CustomersOut")))
        );

        assertTrue(exception.getMessage().contains("Duplicate targetName found in target configuration: CustomersOut"));
    }

    @Test
    void requireSourceAndTargetResolveNamedEntries() {
        BatchConfigStepResolutionSupport support = new BatchConfigStepResolutionSupport(processorConfig());
        JobConfig.JobStepConfig step = step("customers-step", "Customers", "CustomersOut");

        Map<String, SourceConfig> sources = support.mapSourcesByName(List.of(csvSource("Customers")));
        Map<String, TargetConfig> targets = support.mapTargetsByName(List.of(csvTarget("CustomersOut")));

        assertNotNull(support.requireSource(step, sources));
        assertNotNull(support.requireTarget(step, targets));
    }

    @Test
    void requireProcessorMappingFailsFastWhenMappingIsMissing() {
        BatchConfigStepResolutionSupport support = new BatchConfigStepResolutionSupport(processorConfig(mapping("Other", "Target")));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> support.requireProcessorMapping(step("customers-step", "Customers", "CustomersOut"))
        );

        assertEquals(
                "Step 'customers-step' does not have a matching processor mapping for source 'Customers' and target 'CustomersOut'.",
                exception.getMessage()
        );
    }

    @SuppressWarnings("SameParameterValue")
    private JobConfig.JobStepConfig step(String name, String source, String target) {
        JobConfig.JobStepConfig step = new JobConfig.JobStepConfig();
        step.setName(name);
        step.setSource(source);
        step.setTarget(target);
        return step;
    }

    private ProcessorConfig processorConfig(ProcessorConfig.EntityMapping... mappings) {
        ProcessorConfig config = new ProcessorConfig();
        config.setType("default");
        config.setMappings(List.of(mappings));
        return config;
    }

    @SuppressWarnings("SameParameterValue")
    private ProcessorConfig.EntityMapping mapping(String source, String target) {
        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource(source);
        mapping.setTarget(target);
        return mapping;
    }

    @SuppressWarnings("SameParameterValue")
    private CsvSourceConfig csvSource(String sourceName) {
        CsvSourceConfig sourceConfig = new CsvSourceConfig();
        sourceConfig.setSourceName(sourceName);
        sourceConfig.setDelimiter(",");
        sourceConfig.setFilePath("input.csv");
        return sourceConfig;
    }

    @SuppressWarnings("SameParameterValue")
    private CsvTargetConfig csvTarget(String targetName) {
        return new CsvTargetConfig(targetName, null, List.of(), "output.csv", ",");
    }
}


