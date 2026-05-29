package com.etl.exception.config;

import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlException;

public class ConfigException extends EtlException {
    public ConfigException(String message) {
        super(EtlErrorCategory.CONFIG, message);
    }

    public ConfigException(String message, Throwable cause) {
        super(EtlErrorCategory.CONFIG, message, cause);
    }
}

