package com.etl.processor.transform;

/**
 * Signals runtime evaluation failures for processor transforms.
 * Extends IllegalStateException to preserve existing failure semantics while
 * providing a specific type for diagnostics and targeted handling.
 */
public class ProcessorTransformEvaluationException extends IllegalStateException {

    public ProcessorTransformEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}


