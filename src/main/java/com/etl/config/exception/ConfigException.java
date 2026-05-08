package com.etl.config.exception;

import com.etl.exception.EtlException;
import com.etl.exception.EtlErrorCategory;

public class ConfigException extends EtlException {
    public ConfigException(String message) {
		super(EtlErrorCategory.CONFIG, message);
    }
    public ConfigException(String message, Throwable cause) {
		super(EtlErrorCategory.CONFIG, message, cause);
    }
}
