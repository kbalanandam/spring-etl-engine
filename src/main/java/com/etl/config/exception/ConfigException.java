package com.etl.config.exception;

/**
 * @deprecated Use {@link com.etl.exception.config.ConfigException}.
 */
@Deprecated(forRemoval = false, since = "1.7.9")
public class ConfigException extends com.etl.exception.config.ConfigException {
    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
