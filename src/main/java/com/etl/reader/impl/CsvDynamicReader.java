package com.etl.reader.impl;

import com.etl.config.FieldDefinition;
import com.etl.config.source.CsvSourceConfig;
import com.etl.enums.ModelFormat;
import com.etl.reader.DynamicReader;
import com.etl.config.source.SourceConfig;
import com.etl.runtime.FileSourceArtifactSupport;
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
 * Runtime CSV reader builder for explicit-job source configs.
 *
 * <p>This reader converts the configured CSV contract into a Spring Batch
 * {@link FlatFileItemReader}. It owns delimiter and quote handling, optional header
 * skipping, tokenizer column ordering, and dynamic field mapping from CSV columns
 * into the generated source model class.</p>
 *
 * <p>The runtime contract is intentionally config-driven: field order comes from
 * {@code source-config.yaml}, not from reflection over the generated class. That keeps
 * CSV parsing aligned with the selected bundle instead of relying on Java property order.</p>
 *
 * @param <T> The target object type for each CSV row
 */
@Component("csv")
public class CsvDynamicReader<T> implements DynamicReader<T> {

	private static final Logger logger = LoggerFactory.getLogger(CsvDynamicReader.class);
	private static final FileSourceArtifactSupport FILE_SOURCE_ARTIFACT_SUPPORT = new FileSourceArtifactSupport();

	@Override
	public ModelFormat getFormat() {
		return ModelFormat.CSV;
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
		if (config == null || clazz == null) {
			throw new IllegalArgumentException("SourceConfig and target class must not be null.");
		}

		CsvSourceConfig csvConfig = (CsvSourceConfig) config;
		csvConfig.validateParserConfiguration();

		FlatFileItemReader<T> reader = new FlatFileItemReader<>();
		reader.setResource(resolveResource(csvConfig));
		reader.setLinesToSkip(linesToSkip(csvConfig));
		reader.setLineMapper(buildLineMapper(config, clazz));
		reader.setStrict(true);

		// Wrap the concrete reader so CSV parsing failures use the same categorized
		// runtime failure path as the other active source formats.
		return new RuntimeCategorizingItemStreamReader<>(reader, csvConfig.getSourceName());
	}

	private FileSystemResource resolveResource(CsvSourceConfig csvConfig) {
		return new FileSystemResource(FILE_SOURCE_ARTIFACT_SUPPORT.resolveReadablePath(csvConfig));
	}

	private int linesToSkip(CsvSourceConfig csvConfig) {
		// Header skipping is controlled explicitly by config so intermediate handoff
		// CSV files can opt in or out depending on how the upstream step published them.
		return csvConfig.isSkipHeader() ? 1 : 0;
	}

	private DefaultLineMapper<T> buildLineMapper(SourceConfig config, Class<T> clazz) {
		DefaultLineMapper<T> lineMapper = new DefaultLineMapper<>();
		lineMapper.setLineTokenizer(buildTokenizer((CsvSourceConfig) config));

		List<? extends FieldDefinition> fields = config.getFields();
		// Field mapping stays dynamic so one reader implementation can support many
		// different generated source model shapes across preserved bundles.
		lineMapper.setFieldSetMapper(new DynamicFieldSetMapper<>(fields, clazz));
		return lineMapper;
	}

	private DelimitedLineTokenizer buildTokenizer(CsvSourceConfig csvConfig) {
		// Configure delimiter and quote handling from the selected source config so the
		// tokenizer matches the file contract instead of assuming default CSV behavior.
		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setDelimiter(csvConfig.getDelimiter());

		Character quoteCharacter = csvConfig.resolveQuoteCharacter();
		if (quoteCharacter != null) {
			tokenizer.setQuoteCharacter(quoteCharacter);
		}

		String[] columnNames = configuredColumnNames(csvConfig);
		logConfiguredColumnNames(columnNames);
		tokenizer.setNames(columnNames);
		return tokenizer;
	}

	private String[] configuredColumnNames(SourceConfig config) {
		// Column names come from config field order and drive both token binding and
		// DynamicFieldSetMapper property assignment into the generated model class.
		return config.getFields().stream()
				.map(FieldDefinition::getName)
				.toArray(String[]::new);
	}

	private void logConfiguredColumnNames(String[] columnNames) {
		if (!logger.isInfoEnabled()) {
			return;
		}

		logger.info("Column names configured for tokenizer:: {}",
				Arrays.stream(columnNames)
						.map(name -> "[" + name + "]")
						.collect(Collectors.joining(", ")));
	}
}
