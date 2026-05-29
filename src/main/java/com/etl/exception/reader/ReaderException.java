package com.etl.exception.reader;

import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlException;

/**
 * Reader factory/selection exception (for example unsupported format or no registered reader).
 *
 * <p>This remains categorized as {@link EtlErrorCategory#FACTORY} to distinguish it from
 * {@code SourceReadException}, which represents runtime read failures after a reader is selected.</p>
 */
public class ReaderException extends EtlException {

    public ReaderException(String message) {
        super(EtlErrorCategory.FACTORY, message);
    }

    public ReaderException(String message, Throwable cause) {
        super(EtlErrorCategory.FACTORY, message, cause);
    }
}

