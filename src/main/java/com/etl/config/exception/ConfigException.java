package com.etl.config.exception;

import com.etl.exception.EtlException;

public class ConfigException extends EtlException {
    public ConfigException(String message) {
        super(message);
    }
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
