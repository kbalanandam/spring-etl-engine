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
        if (path.endsWith("/") || new File(path).isDirectory()) {
            path += csvConfig.getTargetName().toLowerCase() + ".csv";
        }

        StagedFlatFileItemWriter<Object> writer = new StagedFlatFileItemWriter<>(path);

        // Dynamic line aggregator – converts object → CSV row
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
            writer.setHeaderCallback(headerWriter -> headerWriter.write(String.join(delimiter, Arrays.asList(fieldNames))));
        }
        writer.afterPropertiesSet();

        return writer;
    }
}
