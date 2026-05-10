package com.etl.exception;

public class ListenerException extends EtlException {

	public ListenerException(String message) {
		super(EtlErrorCategory.LISTENER, message);
	}

	public ListenerException(String message, Throwable cause) {
		super(EtlErrorCategory.LISTENER, message, cause);
	}
}

