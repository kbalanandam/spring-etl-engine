package com.etl.reader;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.etl.config.source.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

@Component
public class DynamicReaderFactory {

	private static final Logger logger = LoggerFactory.getLogger(DynamicReaderFactory.class);

	/**
	 * DynamicReaderFactory is responsible for creating instances of DynamicReader
	 * based on the type specified in the SourceConfig. It uses a map to store
	 * different DynamicReader implementations keyed by their type.
	 */

	private final Map<String, DynamicReader<?>> readers;

	public DynamicReaderFactory(List<DynamicReader<?>> readerList) {
		this.readers = readerList.stream().collect(
				Collectors.toMap(DynamicReader::getType, Function.identity(), (existing, replacement) -> existing));
	}

	public DynamicReader<?> getReaderByType(String type) {
		DynamicReader<?> reader = readers.get(type);
		if (reader == null) {
			logger.error("No reader found for type: {}", type);
			throw new IllegalArgumentException("No reader found for type: " + type);
		}
		return reader;
	}

	@SuppressWarnings("unchecked")
	public <T> ItemReader<T> createReader(SourceConfig config, Class<T> clazz) throws Exception {
		DynamicReader<T> reader = (DynamicReader<T>) getReaderByType(config.getFormat());
		return reader.getReader(config, clazz);
	}
}