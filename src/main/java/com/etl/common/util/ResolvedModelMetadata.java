package com.etl.common.util;

/**
 * Immutable runtime metadata derived from source and target configuration.
 * <p>
 * This normalizes the class-resolution contract used by readers, processors,
 * and writers so downstream components do not repeatedly interpret format-
 * specific config details.
 * </p>
 */
public final class ResolvedModelMetadata {

    private final String sourceClassName;
    private final String targetProcessingClassName;
    private final String targetWriteClassName;
    private final boolean wrapperRequired;
    private final String wrapperFieldName;

    public ResolvedModelMetadata(String sourceClassName,
                                 String targetProcessingClassName,
                                 String targetWriteClassName,
                                 boolean wrapperRequired,
                                 String wrapperFieldName) {
        this.sourceClassName = sourceClassName;
        this.targetProcessingClassName = targetProcessingClassName;
        this.targetWriteClassName = targetWriteClassName;
        this.wrapperRequired = wrapperRequired;
        this.wrapperFieldName = wrapperFieldName;
    }

    public String getSourceClassName() {
        return sourceClassName;
    }

    public String getTargetProcessingClassName() {
        return targetProcessingClassName;
    }

    public String getTargetWriteClassName() {
        return targetWriteClassName;
    }

    public boolean isWrapperRequired() {
        return wrapperRequired;
    }

    public String getWrapperFieldName() {
        return wrapperFieldName;
    }
}

