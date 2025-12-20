package com.etl.processor.exception;

import com.etl.exception.EtlException;

public class ProcessorException extends EtlException {
    public ProcessorException(String message) {
        super(message);
    }

    public ProcessorException(String message, Throwable cause) {
        super(message, cause);
    }
}
