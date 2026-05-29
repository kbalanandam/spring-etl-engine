package com.etl.exception.config;

/**
 * Raised when processor rule/transform declarations cannot be bound to the selected source format.
 */
public class ProcessorExtensionBindingConfigException extends ConfigException {

	public ProcessorExtensionBindingConfigException(String message) {
		super(message);
	}

	public ProcessorExtensionBindingConfigException(String message, Throwable cause) {
		super(message, cause);
	}
}

