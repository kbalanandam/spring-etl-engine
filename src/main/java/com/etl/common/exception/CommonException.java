package com.etl.common.exception;

import com.etl.exception.EtlException;

public class CommonException extends EtlException {
    public CommonException(String message) {
        super(message);
    }

    public CommonException(String message, Throwable cause) {
        super(message, cause);
    }
}
