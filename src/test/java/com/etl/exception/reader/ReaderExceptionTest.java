package com.etl.exception.reader;

import com.etl.exception.EtlErrorCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReaderExceptionTest {

    @Test
    void setsFactoryCategoryForMessageOnlyConstructor() {
        ReaderException exception = new ReaderException("No reader registered for format.");

        assertEquals(EtlErrorCategory.FACTORY, exception.category());
        assertEquals("factory", exception.categoryValue());
        assertEquals("No reader registered for format.", exception.getMessage());
    }

    @Test
    void keepsCauseAndFactoryCategoryForMessageAndCauseConstructor() {
        IllegalStateException cause = new IllegalStateException("registry missing");

        ReaderException exception = new ReaderException("Reader selection failed.", cause);

        assertEquals(EtlErrorCategory.FACTORY, exception.category());
        assertEquals("factory", exception.categoryValue());
        assertEquals("Reader selection failed.", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}

