package com.etl.exception;

public class TransformationException extends EtlException {

    public TransformationException(String message) {
        super(EtlErrorCategory.TRANSFORMATION, message);
    }

    public TransformationException(String message, Throwable cause) {
        super(EtlErrorCategory.TRANSFORMATION, message, cause);
    }
}

