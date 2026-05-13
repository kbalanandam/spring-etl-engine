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

import java.io.File;
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

        String path = csvConfig.getFilePath();
        // Directory-style CSV targets publish a deterministic file name based on the
        // logical target name so preserved bundles can keep directory-oriented output paths.
        if (path.endsWith("/") || new File(path).isDirectory()) {
            path += csvConfig.getTargetName().toLowerCase() + ".csv";
        }

        StagedFlatFileItemWriter<Object> writer = new StagedFlatFileItemWriter<>(path);

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
}
