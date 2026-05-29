package com.etl.exception;

public class ValidationException extends EtlException {

    public ValidationException(String message) {
        super(EtlErrorCategory.VALIDATION, message);
    }

    public ValidationException(String message, Throwable cause) {
        super(EtlErrorCategory.VALIDATION, message, cause);
    }
}

