package com.etl.writer;

import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.writer.exception.NoWriterFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates writer implementations for configured target formats.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This remains the active runtime dispatch seam for writer selection in 1.4.x.
 * Keep it stable during migration, but avoid expanding it into the final architecture
 * center while the next-generation generation/runtime model is being introduced.</p>
 */
@Component
public class DynamicWriterFactory {
	private static final Logger logger = LoggerFactory.getLogger(DynamicWriterFactory.class);
    private final Map<ModelFormat, DynamicWriter> writers;

    public DynamicWriterFactory(List<DynamicWriter> writerList) {
        this.writers = writerList.stream()
            .collect(Collectors.toMap(DynamicWriter::getFormat, Function.identity()));
    }

    public ItemWriter<Object> createWriter(TargetConfig config, Class<?> clazz) throws Exception {
        ModelFormat format = config.getFormat();
        DynamicWriter writer = writers.get(format);
        if (writer == null) {
	        	logger.error("No writer found for format: {}", format);
	            throw new NoWriterFoundException("No writer found for format: " + format);
        }
        return writer.getWriter(config, clazz);
    }
}
