package com.etl.writer.impl;

import com.etl.config.FieldDefinition;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.writer.DynamicWriter;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;

@Component("csvWriter")
public class CsvDynamicWriter implements DynamicWriter {

    @Override
    public String getType() {
        return "csv";
    }

    @Override
    public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) throws Exception {

        CsvTargetConfig csvConfig = (CsvTargetConfig) config;

        FlatFileItemWriter<Object> writer = new FlatFileItemWriter<>();

        String path = csvConfig.getFilePath();
        if (path.endsWith("/") || new File(path).isDirectory()) {
            path += csvConfig.getTargetName().toLowerCase() + ".csv";
        }

        writer.setResource(new FileSystemResource(path));

        // Dynamic line aggregator – converts object → CSV row
        BeanWrapperFieldExtractor<Object> extractor = new BeanWrapperFieldExtractor<>();
        extractor.setNames(
                csvConfig.getFields()
                        .stream()
                        .map(FieldDefinition::getName)
                        .toArray(String[]::new)
        );

        DelimitedLineAggregator<Object> aggregator = new DelimitedLineAggregator<>();
        aggregator.setDelimiter(",");
        aggregator.setFieldExtractor(extractor);

        writer.setLineAggregator(aggregator);
        writer.afterPropertiesSet();

        return writer;
    }
}
