package com.etl.exception;

public abstract class EtlException extends RuntimeException {
    public EtlException(String message) {
        super(message);
    }

    public EtlException(String message, Throwable cause) {
        super(message, cause);
    }
}
