package com.etl.model.generator;

import com.etl.config.ModelConfig;
import com.etl.enums.ModelFormat;

/**
 * Interface for generating model classes based on a specific configuration.
 *
 * @param <T> the type of {@link ModelConfig} this generator supports
 */
public interface ModelGenerator<T extends ModelConfig> {

	/**
	 * Returns the model format type supported by this generator.
	 *
	 * @return the {@link ModelFormat} type
	 */
	ModelFormat getType();

	/**
	 * Generates a model class based on the provided configuration object.
	 *
	 * @param object the configuration object used for model generation
	 * @throws Exception if model generation fails
	 */
	void generateModel(Object object) throws Exception;
}