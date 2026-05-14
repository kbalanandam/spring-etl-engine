package com.etl.writer;

import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.exception.EtlException;
import com.etl.exception.FactoryException;
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
            .collect(Collectors.toMap(
                DynamicWriter::getFormat,
                Function.identity(),
                (existing, replacement) -> {
                    throw new FactoryException("Multiple writers registered for format: " + existing.getFormat());
                }
            ));
    }

    /**
     * Creates the concrete Spring Batch writer for the supplied target config.
     *
     * <p>This factory owns only the cross-format creation contract: the target config
     * and generated class must be present, a writer must be registered for the target
     * format, and uncategorized creation failures are wrapped consistently. Format-specific
     * publication rules stay inside each writer implementation.</p>
     */
    public ItemWriter<Object> createWriter(TargetConfig config, Class<?> clazz) throws Exception {
	    	if (config == null || clazz == null) {
	    		throw new FactoryException("Target configuration and target class must not be null when creating a writer.");
	    	}
        ModelFormat format = config.getFormat();
        DynamicWriter writer = writers.get(format);
        if (writer == null) {
	        	logger.error("No writer found for format: {}", format);
	            throw new NoWriterFoundException("No writer found for format: " + format);
        }
          try {
            return writer.getWriter(config, clazz);
          } catch (EtlException e) {
            throw e;
          } catch (Exception e) {
            throw new FactoryException(
				"Failed to create writer for target '" + defaultName(config.getTargetName())
                    + "' using format '" + format + "'.",
                e
            );
          }
    }

  private String defaultName(String value) {
    return value == null || value.isBlank() ? "unnamed" : value.trim();
  }
}
