package com.etl.reader;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.etl.config.source.SourceConfig;
import com.etl.enums.ModelFormat;
import com.etl.exception.EtlException;
import com.etl.exception.FactoryException;
import com.etl.reader.exception.NoReaderFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

/**
 * Creates reader implementations for configured source formats.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This remains the active runtime dispatch seam for reader selection in 1.4.x.
 * Keep it stable during migration, but avoid growing it into the design center of
 * the next architecture while generator-first runtime paths are being introduced.</p>
 */
@Component
public class DynamicReaderFactory {

	private static final Logger logger = LoggerFactory.getLogger(DynamicReaderFactory.class);

	/**
	 * DynamicReaderFactory is responsible for creating instances of DynamicReader
	 * based on the format specified in the SourceConfig. It uses a map to store
	 * different DynamicReader implementations keyed by their format.
	 */

	private final Map<ModelFormat, DynamicReader<?>> readers;

	public DynamicReaderFactory(List<DynamicReader<?>> readerList) {
		this.readers = readerList.stream().collect(
				Collectors.toMap(
						DynamicReader::getFormat,
						Function.identity(),
						(existing, replacement) -> {
							throw new FactoryException("Multiple readers registered for format: " + existing.getFormat());
						}
				));
	}

	/**
	 * Returns the registered reader implementation for a source format.
	 *
	 * <p>This factory is the active runtime dispatch seam for reader selection. Missing
	 * registrations fail fast here so unsupported formats do not surface later as
	 * ambiguous runtime read errors.</p>
	 */
	public DynamicReader<?> getReaderByFormat(ModelFormat format) {
		DynamicReader<?> reader = readers.get(format);
		if (reader == null) {
			logger.error("No reader found for format: {}", format);
			throw new NoReaderFoundException("No reader found for format: " + format);
		}
		return reader;
	}

	/**
	 * Creates the concrete Spring Batch reader for the supplied source config.
	 *
	 * <p>The factory validates only the generic creation contract: non-null config,
	 * non-null generated class, registered reader presence, and consistent exception
	 * wrapping. Format-specific validation remains inside the chosen reader
	 * implementation.</p>
	 */
	@SuppressWarnings("unchecked")
	public <T> ItemReader<T> createReader(SourceConfig config, Class<T> clazz) throws Exception {
		if (config == null || clazz == null) {
			throw new FactoryException("Source configuration and target class must not be null when creating a reader.");
		}
		ModelFormat format = config.getFormat();
		DynamicReader<T> reader = (DynamicReader<T>) getReaderByFormat(format);
		try {
			return reader.getReader(config, clazz);
		} catch (EtlException e) {
			throw e;
		} catch (Exception e) {
			throw new FactoryException(
					"Failed to create reader for source '" + defaultName(config.getSourceName())
							+ "' using format '" + format + "'.",
					e
			);
		}
	}

	private String defaultName(String value) {
		return value == null || value.isBlank() ? "unnamed" : value.trim();
	}
}