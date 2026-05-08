package com.etl.exception;

public class FactoryException extends EtlException {

	public FactoryException(String message) {
		super(EtlErrorCategory.FACTORY, message);
	}

	public FactoryException(String message, Throwable cause) {
		super(EtlErrorCategory.FACTORY, message, cause);
	}
}

