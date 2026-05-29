package com.etl.config.exception;

/**
 * @deprecated Use {@link com.etl.exception.config.ProcessorExtensionBindingConfigException}.
 */
@Deprecated(forRemoval = false, since = "1.7.9")
public class ProcessorExtensionBindingConfigException
        extends com.etl.exception.config.ProcessorExtensionBindingConfigException {

    public ProcessorExtensionBindingConfigException(String message) {
        super(message);
    }

    public ProcessorExtensionBindingConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}

