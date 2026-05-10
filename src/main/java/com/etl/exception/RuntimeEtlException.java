package com.etl.exception;

public class RuntimeEtlException extends EtlException {

	public RuntimeEtlException(String message) {
		super(EtlErrorCategory.RUNTIME, message);
	}

	public RuntimeEtlException(String message, Throwable cause) {
		super(EtlErrorCategory.RUNTIME, message, cause);
	}
}

