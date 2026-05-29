package com.etl.exception;

/**
 * Runtime read failure for an already selected source (for example parse/IO issues while consuming records).
 *
 * <p>This is intentionally categorized as {@link EtlErrorCategory#SOURCE_READ}.</p>
 */
public class SourceReadException extends EtlException {

    public SourceReadException(String message) {
        super(EtlErrorCategory.SOURCE_READ, message);
    }

    public SourceReadException(String message, Throwable cause) {
        super(EtlErrorCategory.SOURCE_READ, message, cause);
    }
}

