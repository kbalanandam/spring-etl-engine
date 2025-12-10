package com.etl.reader;

import com.etl.config.source.SourceConfig;

import org.springframework.batch.item.ItemReader;

public interface DynamicReader<T> {
	String getType();
	ItemReader<T> getReader(SourceConfig config, Class<T> clazz) throws Exception;
}