package com.etl.exception;

/**
 * High-level ETL failure categories used for operator-facing diagnostics and logs.
 */
public enum EtlErrorCategory {
	CONFIG("config"),
	RUNTIME("runtime"),
	FACTORY("factory"),
	LISTENER("listener"),
	RELATIONAL("relational"),
	UNCLASSIFIED("unclassified");

	private final String logValue;

	EtlErrorCategory(String logValue) {
		this.logValue = logValue;
	}

	public String logValue() {
		return logValue;
	}
}

