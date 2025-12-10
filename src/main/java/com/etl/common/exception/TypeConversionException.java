package com.etl.common.exception;

public class TypeConversionException extends RuntimeException {

    public TypeConversionException(String message) {
        super(message);
    }

    public TypeConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}