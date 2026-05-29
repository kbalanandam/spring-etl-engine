package com.etl.writer;

import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.extension.ExtensionConflictPolicy;
import com.etl.exception.EtlException;
import com.etl.exception.FactoryException;
import com.etl.exception.writer.NoWriterFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

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

    public static DynamicWriterFactory withDiscoveredWriters() {
        return new DynamicWriterFactory(DynamicWriterDefaults.defaultWriters());
    }

    public DynamicWriterFactory(List<DynamicWriter> writerList) {
        List<ExtensionConflictPolicy.Candidate<ModelFormat, DynamicWriter>> candidates = new ArrayList<>();
        for (DynamicWriter writer : writerList == null ? List.<DynamicWriter>of() : writerList) {
            if (writer == null) {
                continue;
            }
            candidates.add(new ExtensionConflictPolicy.Candidate<>(
                    writer.getFormat(),
                    writer,
                    new ExtensionConflictPolicy.ProviderMetadata(writer.extensionId(), writer.isOverride())
            ));
        }

        this.writers = ExtensionConflictPolicy.resolve(candidates, new ExtensionConflictPolicy.ConflictReporter<>() {
            @Override
            public void onOverride(ModelFormat key,
                                   ExtensionConflictPolicy.ProviderMetadata winner,
                                   ExtensionConflictPolicy.ProviderMetadata replaced) {
                logger.info("Writer format '{}' overridden by extension '{}' replacing extension '{}'.",
                        key, winner.providerId(), replaced.providerId());
            }

            @Override
            public void onIgnored(ModelFormat key,
                                  ExtensionConflictPolicy.ProviderMetadata ignored,
                                  ExtensionConflictPolicy.ProviderMetadata winner) {
                logger.debug("Ignoring non-override writer extension '{}' for format '{}' because override extension '{}' already registered.",
                        ignored.providerId(), key, winner.providerId());
            }

            @Override
            public RuntimeException duplicateFailure(ModelFormat key,
                                                     ExtensionConflictPolicy.ProviderMetadata existing,
                                                     ExtensionConflictPolicy.ProviderMetadata candidate) {
                return new FactoryException("Multiple writers registered for format: " + key
                        + " (extensions: " + existing.providerId() + ", " + candidate.providerId() + ")"
                        + ". Set exactly one extension with isOverride=true to replace an existing writer.");
            }
        });
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
