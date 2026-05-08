package com.etl.common.exception;

import com.etl.exception.EtlException;
import com.etl.exception.EtlErrorCategory;

public class CommonException extends EtlException {
    public CommonException(String message) {
		super(EtlErrorCategory.RUNTIME, message);
    }

    public CommonException(String message, Throwable cause) {
		super(EtlErrorCategory.RUNTIME, message, cause);
    }
}
