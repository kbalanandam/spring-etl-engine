package com.etl.reader.impl;

import com.etl.config.FieldDefinition;
import com.etl.config.source.CsvSourceConfig;
import com.etl.reader.DynamicReader;
import com.etl.config.source.SourceConfig;
import com.etl.reader.mapper.DynamicFieldSetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CsvDynamicReader is a DynamicReader implementation for reading CSV files.
 * It supports dynamic column mapping using SourceConfig and allows flexible
 * reading of different CSV structures.
 *
 * @param <T> The target object type for each CSV row
 */
@Component("csv")
public class CsvDynamicReader<T> implements DynamicReader<T> {

	private static final Logger logger = LoggerFactory.getLogger(CsvDynamicReader.class);

	@Override
	public String getType() {
		return "csv";
	}

	/**
	 * Builds an ItemReader for the given CSV file configuration and target class.
	 *
	 * @param config The source configuration containing file path, delimiter, and columns
	 * @param clazz  The target class type for mapping CSV rows
	 * @return Configured ItemReader for reading CSV rows
	 */
	@Override
	public ItemReader<T> getReader(SourceConfig config, Class<T> clazz) {

		CsvSourceConfig csvConfig = (CsvSourceConfig) config;

		if (config == null || clazz == null) {
			throw new IllegalArgumentException("SourceConfig and target class must not be null.");
		}

		FlatFileItemReader<T> reader = new FlatFileItemReader<>();
		reader.setResource(new FileSystemResource(csvConfig.getFilePath()));
		reader.setLinesToSkip(1); // Skip header

		// ------------------------------
		// Build dynamic line mapper
		// ------------------------------
		DefaultLineMapper<T> lineMapper = new DefaultLineMapper<>();

		// Configure tokenizer
		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setDelimiter(csvConfig.getDelimiter());

		// Extract column names
		String[] columnNames = config.getFields().stream()
				.map(FieldDefinition::getName)
				.toArray(String[]::new);

		// Log configured column names (S2629 compliant)
		if (logger.isInfoEnabled()) {
			logger.info("Column names configured for tokenizer:: {}",
					Arrays.stream(columnNames)
							.map(name -> "[" + name + "]")
							.collect(Collectors.joining(", ")));
		}

		tokenizer.setNames(columnNames);
		lineMapper.setLineTokenizer(tokenizer);
		List<? extends FieldDefinition> fields = config.getFields();

		// Map fields to target class dynamically
		lineMapper.setFieldSetMapper(new DynamicFieldSetMapper<>(fields, clazz));

		reader.setLineMapper(lineMapper);
		reader.setStrict(true);

		return reader;
	}
}
