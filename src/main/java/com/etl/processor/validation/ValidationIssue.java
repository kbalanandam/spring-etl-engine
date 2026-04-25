package com.etl.processor.validation;

public record ValidationIssue(String field, String rule, String message) {
}
