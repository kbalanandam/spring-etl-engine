package com.etl.writer;

import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import org.springframework.batch.item.ItemWriter;

/**
 * Contract for format-specific runtime writer implementations.
 *
 * <p>Each implementation owns one target {@link ModelFormat} and converts a typed
 * {@link TargetConfig} plus generated model class into the Spring Batch
 * {@link ItemWriter} used for the active step. Concrete writers are responsible for
 * format-specific serialization and publication behavior, while runtime dispatch stays
 * centralized in {@link DynamicWriterFactory}.</p>
 */
public interface DynamicWriter {
    /**
     * Stable extension id used for diagnostics and conflict reporting.
     */
    default String extensionId() {
        return getClass().getName();
    }

    /**
     * Marks this writer as an explicit override candidate for its format key.
     */
    default boolean isOverride() {
        return false;
    }

    /**
     * Returns the target format handled by this writer implementation.
     */
    ModelFormat getFormat();

    /**
     * Builds the runtime writer for the supplied target config and generated model class.
     */
    ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) throws Exception;
}
