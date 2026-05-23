package com.etl.processor;

import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.target.JsonTargetConfig;
import com.etl.exception.FactoryException;
import com.etl.processor.impl.DefaultDynamicProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ItemProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicProcessorFactoryTest {

    @Test
    void rejectsLegacyProcessorTypeBeforeLookup() {
        DynamicProcessorFactory factory = new DynamicProcessorFactory(new DefaultDynamicProcessor());
        ProcessorConfig processorConfig = minimalProcessorConfig("customerProcessor");

        FactoryException failure = assertThrows(
                FactoryException.class,
                () -> factory.getProcessor(processorConfig, sourceConfig(), targetConfig(), metadata())
        );

        assertTrue(failure.getMessage().contains("type='customerProcessor'"));
        assertTrue(failure.getMessage().contains("type: default"));
    }

    @Test
    void createsProcessorWhenSharedDefaultTypeIsConfigured() throws Exception {
        DynamicProcessorFactory factory = new DynamicProcessorFactory(new DefaultDynamicProcessor());
        ProcessorConfig processorConfig = minimalProcessorConfig("default");

        ItemProcessor<Object, Object> processor = factory.getProcessor(
                processorConfig,
                sourceConfig(),
                targetConfig(),
                metadata()
        );

        assertNotNull(processor);
    }

    private ProcessorConfig minimalProcessorConfig(String type) {
        ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
        fieldMapping.setFrom("id");
        fieldMapping.setTo("id");

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Customers");
        mapping.setTarget("CustomersOut");
        mapping.setFields(List.of(fieldMapping));

        ProcessorConfig processorConfig = new ProcessorConfig();
        processorConfig.setType(type);
        processorConfig.setMappings(List.of(mapping));
        return processorConfig;
    }

    private CsvSourceConfig sourceConfig() {
        return new CsvSourceConfig("Customers", null, List.of(), "ignored.csv", ",");
    }

    private JsonTargetConfig targetConfig() {
        return new JsonTargetConfig("CustomersOut", null, List.of(), "ignored.json");
    }

    private ResolvedModelMetadata metadata() {
        return new ResolvedModelMetadata(
                "java.lang.Object",
                "java.util.LinkedHashMap",
                "java.util.LinkedHashMap",
                false,
                null
        );
    }
}


