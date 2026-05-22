package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import com.etl.enums.ModelFormat;

import java.util.Set;

/**
 * SPI for one processor-side field cleanup or normalization transform.
 *
 * <p><strong>Transition status:</strong> REUSE.</p>
 *
 * <p>Transforms execute after source read and before processor validation rules. Implementations
 * should stay focused on shaping one mapped field value and avoid embedding validation semantics,
 * which belong in the processor validation SPI.</p>
 */
public interface ProcessorFieldTransform {

	/**
	 * Stable extension id used for diagnostics and conflict reporting.
	 */
	default String extensionId() {
		return getClass().getName();
	}

	/**
	 * Marks this transform as an explicit override candidate for its dispatch key.
	 */
	default boolean isOverride() {
		return false;
	}

	/**
	 * Returns the config {@code transforms[].type} value claimed by this implementation.
	 */
	String getTransformType();

	/**
	 * Optional source-format scope for this transform registration.
	 *
	 * <p>Empty means the transform applies to all source formats.</p>
	 */
	default Set<ModelFormat> supportedSourceFormats() {
		return Set.of();
	}

	/**
	 * Validates that this transform can run for the supplied mapping declaration.
	 */
	default void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldTransform transform) {
	}

	/**
	 * Applies the transform with access to the wider mapping context and already-resolved values.
	 * Implementations that do not need context can rely on the simpler two-argument overload.
	 */
	default Object apply(Object value,
	                  ProcessorConfig.FieldTransform transform,
	                  ProcessorTransformContext context) {
		return apply(value, transform);
	}

	/**
	 * Applies the transform to the current field value.
	 */
	Object apply(Object value, ProcessorConfig.FieldTransform transform);
}

