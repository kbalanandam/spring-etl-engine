package com.etl.writer;

import com.etl.config.target.TargetConfig;
import com.etl.writer.exception.NoWriterFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DynamicWriterFactory {
	private static final Logger logger = LoggerFactory.getLogger(DynamicWriterFactory.class);
    private final Map<String, DynamicWriter> writers;

    public DynamicWriterFactory(List<DynamicWriter> writerList) {
        this.writers = writerList.stream()
            .collect(Collectors.toMap(DynamicWriter::getType, Function.identity()));
    }

    public ItemWriter<Object> createWriter(TargetConfig config, Class<?> clazz) throws Exception {
        DynamicWriter writer = writers.get(config.getType());
        if (writer == null) {
        	logger.error("No writer found for type: {}", config.getType());
            throw new NoWriterFoundException("No writer found for type: " + config.getType());
        }
        return writer.getWriter(config, clazz);
    }
}
