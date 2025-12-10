package com.etl.model.generator;

import com.etl.config.ModelConfig;

public interface ModelGenerator<T extends ModelConfig> {

	/**
	 * Generates a model based on the provided configuration and type.
	 * 
	 * @param config
	 * @param Type
	 * @return
	 * @return
	 */
	String getType();

	void generateModel(Object object) throws Exception;

}
