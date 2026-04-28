package com.etl.validation.exception;

/**
 * @deprecated Legacy validation framework exception. Active runtime validation uses
 * source/config exceptions and processor validation issue reporting instead.
 */
@Deprecated(since = "1.4.0")
public class ValidationException extends Exception {
    public ValidationException(String message) {
        super(message);
    }
}

