package com.etl.writer.impl;

import com.etl.config.FieldDefinition;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.writer.DynamicWriter;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Runtime CSV writer builder for file-based CSV targets.
 *
 * <p>This writer converts the selected CSV target config into a staged flat-file writer.
 * It owns output-path resolution, header publication, delimiter selection, and field
 * extraction order for the generated target model class.</p>
 */
@Component("csvWriter")
public class CsvDynamicWriter implements DynamicWriter {

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.CSV;
    }

    @Override
    public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) throws Exception {

        CsvTargetConfig csvConfig = (CsvTargetConfig) config;

        String path = resolveOutputPath(csvConfig);

        StagedFlatFileItemWriter<Object> writer = new StagedFlatFileItemWriter<>(path, csvConfig.isPackageAsZip());

        // Field extraction order comes from target-config.yaml so generated objects are
        // serialized according to the selected bundle contract rather than reflection order.
        BeanWrapperFieldExtractor<Object> extractor = new BeanWrapperFieldExtractor<>();
        extractor.setNames(
                csvConfig.getFields()
                        .stream()
                        .map(FieldDefinition::getName)
                        .toArray(String[]::new)
        );
        String[] fieldNames = csvConfig.getFields()
                .stream()
                .map(FieldDefinition::getName)
                .toArray(String[]::new);
        String delimiter = csvConfig.getDelimiter();

        DelimitedLineAggregator<Object> aggregator = new DelimitedLineAggregator<>();
        aggregator.setDelimiter(delimiter);
        aggregator.setFieldExtractor(extractor);

        writer.setLineAggregator(aggregator);
        if (csvConfig.isIncludeHeader()) {
            // Header publication is explicit so intermediate handoff CSV files can choose
            // whether downstream steps should see a header row.
            writer.setHeaderCallback(headerWriter -> headerWriter.write(String.join(delimiter, Arrays.asList(fieldNames))));
        }
        writer.afterPropertiesSet();

        return writer;
    }

    private String resolveOutputPath(CsvTargetConfig csvConfig) {
        String configuredPath = csvConfig.getFilePath();
        if (configuredPath == null) {
            return null;
        }

        Path configuredOutputPath = Path.of(configuredPath);
        if (configuredPath.endsWith("/") || configuredPath.endsWith("\\") || Files.isDirectory(configuredOutputPath)) {
            // Join with Path.resolve so directory-style targets never create malformed names.
            String defaultFileName = csvConfig.getTargetName().toLowerCase() + ".csv";
            return configuredOutputPath.resolve(defaultFileName).toString();
        }

        return configuredPath;
    }
}
