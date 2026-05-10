package com.etl.exception;

public class RelationalException extends EtlException {

	public RelationalException(String message) {
		super(EtlErrorCategory.RELATIONAL, message);
	}

	public RelationalException(String message, Throwable cause) {
		super(EtlErrorCategory.RELATIONAL, message, cause);
	}
}

