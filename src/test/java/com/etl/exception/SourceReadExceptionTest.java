package com.etl.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SourceReadExceptionTest {

    @Test
    void setsSourceReadCategoryForMessageOnlyConstructor() {
        SourceReadException exception = new SourceReadException("Unable to read source file.");

        assertEquals(EtlErrorCategory.SOURCE_READ, exception.category());
        assertEquals("source-read", exception.categoryValue());
        assertEquals("Unable to read source file.", exception.getMessage());
    }

    @Test
    void keepsCauseAndSourceReadCategoryForMessageAndCauseConstructor() {
        RuntimeException cause = new RuntimeException("io failure");

        SourceReadException exception = new SourceReadException("Failed while reading source.", cause);

        assertEquals(EtlErrorCategory.SOURCE_READ, exception.category());
        assertEquals("source-read", exception.categoryValue());
        assertEquals("Failed while reading source.", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}

