package com.etl.common.exception;

public class ReflectionAccessException extends CommonException {

    public ReflectionAccessException(String message) {
        super(message);
    }

    public ReflectionAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
