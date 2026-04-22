package com.etl.writer;

import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import org.springframework.batch.item.ItemWriter;

public interface DynamicWriter {
    ModelFormat getFormat();
    ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) throws Exception;
}
