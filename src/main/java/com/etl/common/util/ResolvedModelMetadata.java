package com.etl.common.util;

import java.util.Objects;

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
        this.sourceClassName = requireQualifiedClassName(sourceClassName, "sourceClassName");
        this.targetProcessingClassName = requireQualifiedClassName(targetProcessingClassName, "targetProcessingClassName");
        this.targetWriteClassName = requireQualifiedClassName(targetWriteClassName, "targetWriteClassName");
        this.wrapperRequired = wrapperRequired;
        if (wrapperRequired) {
            this.wrapperFieldName = requireJavaIdentifier(wrapperFieldName, "wrapperFieldName");
        } else {
            if (wrapperFieldName != null && !wrapperFieldName.isBlank()) {
                throw new IllegalArgumentException("wrapperFieldName must be blank when wrapperRequired is false.");
            }
            this.wrapperFieldName = null;
        }
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

  private static String requireQualifiedClassName(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null.");
    String trimmed = value.trim();
    if (trimmed.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    String[] segments = trimmed.split("\\.");
    if (segments.length < 2) {
      throw new IllegalArgumentException(fieldName + " must be a fully qualified class name.");
    }
    for (String segment : segments) {
      if (!isJavaIdentifier(segment)) {
        throw new IllegalArgumentException(fieldName + " must be a valid fully qualified class name.");
      }
    }
    return trimmed;
  }

  private static String requireJavaIdentifier(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null.");
    String trimmed = value.trim();
    if (trimmed.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    if (!isJavaIdentifier(trimmed)) {
      throw new IllegalArgumentException(fieldName + " must be a valid Java identifier.");
    }
    return trimmed;
  }

  private static boolean isJavaIdentifier(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    if (!Character.isJavaIdentifierStart(value.charAt(0))) {
      return false;
    }
    for (int i = 1; i < value.length(); i++) {
      if (!Character.isJavaIdentifierPart(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}

