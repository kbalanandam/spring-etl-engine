package com.etl.reader;

import com.etl.config.source.SourceConfig;
import com.etl.enums.ModelFormat;
import org.springframework.batch.item.ItemReader;

/**
 * Contract for format-specific runtime reader implementations.
 *
 * <p>Each implementation owns one {@link ModelFormat} and knows how to translate a
 * typed {@link SourceConfig} plus generated model class into a Spring Batch
 * {@link ItemReader}. Implementations are responsible for format-specific parsing,
 * unmarshalling, or query setup, while cross-format dispatch remains in
 * {@link DynamicReaderFactory}.</p>
 *
 * <p>The returned reader may also implement Spring Batch stream lifecycle interfaces
 * when the underlying format needs open/update/close participation.</p>
 */
public interface DynamicReader<T> {
	/**
	 * Stable extension id used for diagnostics and conflict reporting.
	 */
	default String extensionId() {
		return getClass().getName();
	}

	/**
	 * Marks this reader as an explicit override candidate for its format key.
	 */
	default boolean isOverride() {
		return false;
	}

	/**
	 * Returns the source format handled by this reader implementation.
	 */
	ModelFormat getFormat();
	/**
	 * Builds the runtime reader for the supplied source config and generated model class.
	 */
	ItemReader<T> getReader(SourceConfig config, Class<T> clazz) throws Exception;
}