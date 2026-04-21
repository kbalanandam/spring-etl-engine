package com.etl.reader;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.etl.config.source.SourceConfig;
import com.etl.enums.ModelFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

@Component
public class DynamicReaderFactory {

	private static final Logger logger = LoggerFactory.getLogger(DynamicReaderFactory.class);

	/**
	 * DynamicReaderFactory is responsible for creating instances of DynamicReader
	 * based on the format specified in the SourceConfig. It uses a map to store
	 * different DynamicReader implementations keyed by their format.
	 */

	private final Map<ModelFormat, DynamicReader<?>> readers;

	public DynamicReaderFactory(List<DynamicReader<?>> readerList) {
		this.readers = readerList.stream().collect(
				Collectors.toMap(DynamicReader::getFormat, Function.identity(), (existing, replacement) -> existing));
	}

	public DynamicReader<?> getReaderByFormat(ModelFormat format) {
		DynamicReader<?> reader = readers.get(format);
		if (reader == null) {
			logger.error("No reader found for format: {}", format);
			throw new IllegalArgumentException("No reader found for format: " + format);
		}
		return reader;
	}

	@SuppressWarnings("unchecked")
	public <T> ItemReader<T> createReader(SourceConfig config, Class<T> clazz) throws Exception {
		ModelFormat format = config.getFormat();
		DynamicReader<T> reader = (DynamicReader<T>) getReaderByFormat(format);
		return reader.getReader(config, clazz);
	}
}