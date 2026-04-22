package com.etl.reader;

import com.etl.config.source.SourceConfig;
import com.etl.enums.ModelFormat;
import org.springframework.batch.item.ItemReader;

public interface DynamicReader<T> {
	ModelFormat getFormat();
	ItemReader<T> getReader(SourceConfig config, Class<T> clazz) throws Exception;
}