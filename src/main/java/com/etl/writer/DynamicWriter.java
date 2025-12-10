package com.etl.writer;

import com.etl.config.target.TargetConfig;
import org.springframework.batch.item.ItemWriter;

import com.etl.config.target.TargetWrapper;

public interface DynamicWriter {
    String getType(); // e.g., "xml", "csv", etc.
    ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) throws Exception;
}
