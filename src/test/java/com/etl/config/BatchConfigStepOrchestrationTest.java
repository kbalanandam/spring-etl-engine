package com.etl.config;

import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import com.etl.job.listener.JobCompletionNotificationListener;
import com.etl.job.listener.StepLoggingContextListener;
import com.etl.processor.DynamicProcessorFactory;
import com.etl.reader.DynamicReaderFactory;
import com.etl.runtime.FileIngestionRuntimeSupport;
import com.etl.writer.DynamicWriterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchConfigStepOrchestrationTest {

    @TempDir
    Path tempDir;

    @Test
    void buildStepsUsesExplicitStepOrderInsteadOfSourceTargetPosition() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(
                csvSource("Customers", tempCsv("customers.csv")),
                csvSource("Department", tempCsv("departments.csv"))
        ));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(
                xmlTarget("Customers", "Customer"),
                xmlTarget("Departments", "Department")
        ));

        ProcessorConfig processorConfig = processorConfig(
                mapping("Customers", "Customers"),
                mapping("Department", "Departments")
        );

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "cust-dept-load",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        List.of(
                                step("departments-step", "Department", "Departments"),
                                step("customers-step", "Customers", "Customers")
                        )
                        ),
                        new FileIngestionRuntimeSupport()
        );

        List<Step> steps = batchConfig.buildSteps();

        assertEquals(List.of("departments-step", "customers-step"), steps.stream().map(Step::getName).toList());
    }

    @Test
    void buildStepsFailsFastWhenProcessorMappingIsMissingForConfiguredStep() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", tempCsv("customers.csv"))));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(xmlTarget("Customers", "Customer")));

        ProcessorConfig processorConfig = processorConfig(mapping("OtherSource", "Customers"));

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customer-load",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        List.of(step("customers-step", "Customers", "Customers"))
                        ),
                        new FileIngestionRuntimeSupport()
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, batchConfig::buildSteps);
        assertEquals("Step 'customers-step' does not have a matching processor mapping for source 'Customers' and target 'Customers'.", exception.getMessage());
    }

    private CsvSourceConfig csvSource(String sourceName, Path filePath) {
        CsvSourceConfig config = new CsvSourceConfig();
        config.setSourceName(sourceName);
        config.setPackageName("com.etl.model.source");
        config.setFilePath(filePath.toString());
        config.setDelimiter(",");
        config.setFields(List.of(column("id", "int")));
        return config;
    }

    private TargetConfig xmlTarget(String targetName, String recordElement) {
        return new XmlTargetConfig(targetName, "com.etl.model.target", List.of(column("id", "int")), tempDir.toString(), targetName, recordElement);
    }

    private ProcessorConfig processorConfig(ProcessorConfig.EntityMapping... mappings) {
        ProcessorConfig config = new ProcessorConfig();
        config.setType("default");
        config.setMappings(List.of(mappings));
        return config;
    }

    private ProcessorConfig.EntityMapping mapping(String source, String target) {
        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource(source);
        mapping.setTarget(target);
        ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
        fieldMapping.setFrom("id");
        fieldMapping.setTo("id");
        mapping.setFields(List.of(fieldMapping));
        return mapping;
    }

    private ColumnConfig column(String name, String type) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType(type);
        return column;
    }

    private JobConfig.JobStepConfig step(String name, String source, String target) {
        JobConfig.JobStepConfig step = new JobConfig.JobStepConfig();
        step.setName(name);
        step.setSource(source);
        step.setTarget(target);
        return step;
    }

    private DynamicReaderFactory mockReaderFactory() throws Exception {
        DynamicReaderFactory readerFactory = mock(DynamicReaderFactory.class);
        ItemReader<Object> reader = () -> null;
        when(readerFactory.createReader(any(SourceConfig.class), any(Class.class))).thenReturn(reader);
        return readerFactory;
    }

    private DynamicWriterFactory mockWriterFactory() throws Exception {
        DynamicWriterFactory writerFactory = mock(DynamicWriterFactory.class);
        ItemWriter<Object> writer = chunk -> { };
        when(writerFactory.createWriter(any(TargetConfig.class), any(Class.class))).thenReturn(writer);
        return writerFactory;
    }

    private DynamicProcessorFactory mockProcessorFactory() throws Exception {
        DynamicProcessorFactory processorFactory = mock(DynamicProcessorFactory.class);
        ItemProcessor<Object, Object> processor = item -> item;
        when(processorFactory.getProcessor(any(ProcessorConfig.class), any(SourceConfig.class), any(TargetConfig.class), any())).thenReturn(processor);
        return processorFactory;
    }

    private Path tempCsv(String fileName) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, "id\n1\n");
        return file;
    }
}

