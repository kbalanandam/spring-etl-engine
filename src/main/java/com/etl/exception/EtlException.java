package com.etl.exception;

public abstract class EtlException extends RuntimeException {
  private final EtlErrorCategory category;

    public EtlException(String message) {
    this(EtlErrorCategory.UNCLASSIFIED, message);
    }

    public EtlException(String message, Throwable cause) {
    this(EtlErrorCategory.UNCLASSIFIED, message, cause);
    }

  protected EtlException(EtlErrorCategory category, String message) {
    super(message);
    this.category = category == null ? EtlErrorCategory.UNCLASSIFIED : category;
  }

  protected EtlException(EtlErrorCategory category, String message, Throwable cause) {
    super(message, cause);
    this.category = category == null ? EtlErrorCategory.UNCLASSIFIED : category;
  }

  public EtlErrorCategory category() {
    return category;
  }

  public String categoryValue() {
    return category.logValue();
  }
}
