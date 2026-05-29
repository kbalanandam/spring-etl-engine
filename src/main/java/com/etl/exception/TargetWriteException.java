package com.etl.exception;

public class TargetWriteException extends EtlException {

    public TargetWriteException(String message) {
        super(EtlErrorCategory.TARGET_WRITE, message);
    }

    public TargetWriteException(String message, Throwable cause) {
        super(EtlErrorCategory.TARGET_WRITE, message, cause);
    }
}

