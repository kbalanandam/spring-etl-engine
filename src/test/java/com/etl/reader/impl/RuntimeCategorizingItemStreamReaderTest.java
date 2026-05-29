package com.etl.reader.impl;

import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlException;
import com.etl.exception.EtlExceptionDetails;
import com.etl.exception.RuntimeEtlException;
import com.etl.exception.SourceReadException;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.lang.NonNull;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeCategorizingItemStreamReaderTest {

    @Test
    void preservesCategorizedReadFailures() {
        StubEtlException expected = new StubEtlException(EtlErrorCategory.RUNTIME, "typed failure");
        RuntimeCategorizingItemStreamReader<String> reader = new RuntimeCategorizingItemStreamReader<>(
                () -> {
                    throw expected;
                },
                "Customers"
        );

        StubEtlException failure = assertThrows(StubEtlException.class, reader::read);

        assertSame(expected, failure);
        assertEquals(EtlErrorCategory.RUNTIME, EtlExceptionDetails.categoryOf(failure));
    }

    @Test
      void wrapsUncategorizedReadFailuresAsSourceRead() {
        RuntimeCategorizingItemStreamReader<String> reader = new RuntimeCategorizingItemStreamReader<>(
                () -> {
                    throw new IllegalStateException("boom");
                },
                "Customers"
        );

            SourceReadException failure = assertThrows(SourceReadException.class, reader::read);

        assertEquals("Failed while reading source 'Customers'.", failure.getMessage());
            assertEquals(EtlErrorCategory.SOURCE_READ, EtlExceptionDetails.categoryOf(failure));
        assertEquals(IllegalStateException.class, failure.getCause().getClass());
    }

    @Test
    void preservesCategorizedOpenFailures() {
        ItemStreamException expected = categorizedStreamException("categorized open");
        RuntimeCategorizingItemStreamReader<String> reader = new RuntimeCategorizingItemStreamReader<>(
                new ThrowingItemStreamReader(expected, null, null),
                "Customers"
        );

        ItemStreamException failure = assertThrows(ItemStreamException.class, () -> reader.open(new ExecutionContext()));

        assertSame(expected, failure);
        assertEquals(EtlErrorCategory.RUNTIME, EtlExceptionDetails.categoryOf(failure));
    }

    @Test
      void wrapsUncategorizedOpenFailuresAsSourceRead() {
        ItemStreamException expected = new ItemStreamException("plain open");
        RuntimeCategorizingItemStreamReader<String> reader = new RuntimeCategorizingItemStreamReader<>(
                new ThrowingItemStreamReader(expected, null, null),
                "Customers"
        );

        ItemStreamException failure = assertThrows(ItemStreamException.class, () -> reader.open(new ExecutionContext()));

        assertEquals("Failed to open reader for source 'Customers'.", failure.getMessage());
            assertEquals(EtlErrorCategory.SOURCE_READ, EtlExceptionDetails.categoryOf(failure));
            assertEquals(SourceReadException.class, failure.getCause().getClass());
    }

    @Test
    void preservesCategorizedUpdateFailures() {
        ItemStreamException expected = categorizedStreamException("categorized update");
        RuntimeCategorizingItemStreamReader<String> reader = new RuntimeCategorizingItemStreamReader<>(
                new ThrowingItemStreamReader(null, expected, null),
                "Customers"
        );

        ItemStreamException failure = assertThrows(ItemStreamException.class, () -> reader.update(new ExecutionContext()));

        assertSame(expected, failure);
        assertEquals(EtlErrorCategory.RUNTIME, EtlExceptionDetails.categoryOf(failure));
    }

    @Test
      void wrapsUncategorizedUpdateFailuresAsSourceRead() {
        ItemStreamException expected = new ItemStreamException("plain update");
        RuntimeCategorizingItemStreamReader<String> reader = new RuntimeCategorizingItemStreamReader<>(
                new ThrowingItemStreamReader(null, expected, null),
                "Customers"
        );

        ItemStreamException failure = assertThrows(ItemStreamException.class, () -> reader.update(new ExecutionContext()));

        assertEquals("Failed to update reader for source 'Customers'.", failure.getMessage());
            assertEquals(EtlErrorCategory.SOURCE_READ, EtlExceptionDetails.categoryOf(failure));
            assertEquals(SourceReadException.class, failure.getCause().getClass());
    }

    @Test
    void preservesCategorizedCloseFailures() {
        ItemStreamException expected = categorizedStreamException("categorized close");
        RuntimeCategorizingItemStreamReader<String> reader = new RuntimeCategorizingItemStreamReader<>(
                new ThrowingItemStreamReader(null, null, expected),
                "Customers"
        );

        ItemStreamException failure = assertThrows(ItemStreamException.class, reader::close);

        assertSame(expected, failure);
        assertEquals(EtlErrorCategory.RUNTIME, EtlExceptionDetails.categoryOf(failure));
    }

    @Test
      void wrapsUncategorizedCloseFailuresAsSourceRead() {
        ItemStreamException expected = new ItemStreamException("plain close");
        RuntimeCategorizingItemStreamReader<String> reader = new RuntimeCategorizingItemStreamReader<>(
                new ThrowingItemStreamReader(null, null, expected),
                "Customers"
        );

        ItemStreamException failure = assertThrows(ItemStreamException.class, reader::close);

        assertEquals("Failed to close reader for source 'Customers'.", failure.getMessage());
            assertEquals(EtlErrorCategory.SOURCE_READ, EtlExceptionDetails.categoryOf(failure));
            assertEquals(SourceReadException.class, failure.getCause().getClass());
    }

    @Test
    void lifecycleMethodsAreNoOpsForNonItemStreamDelegates() {
        AtomicInteger readCalls = new AtomicInteger();
        ItemReader<String> delegate = () -> {
            readCalls.incrementAndGet();
            return null;
        };
        RuntimeCategorizingItemStreamReader<String> reader = new RuntimeCategorizingItemStreamReader<>(delegate, "Customers");

        assertDoesNotThrow(() -> reader.open(new ExecutionContext()));
        assertDoesNotThrow(() -> reader.update(new ExecutionContext()));
        assertDoesNotThrow(reader::close);
        assertDoesNotThrow(reader::read);
        assertEquals(1, readCalls.get());
    }

    private static ItemStreamException categorizedStreamException(String message) {
        return new ItemStreamException(message, new RuntimeEtlException(message));
    }

    private static final class ThrowingItemStreamReader implements ItemStreamReader<String> {
        private final ItemStreamException openFailure;
        private final ItemStreamException updateFailure;
        private final ItemStreamException closeFailure;

        private ThrowingItemStreamReader(ItemStreamException openFailure,
                                         ItemStreamException updateFailure,
                                         ItemStreamException closeFailure) {
            this.openFailure = openFailure;
            this.updateFailure = updateFailure;
            this.closeFailure = closeFailure;
        }

        @Override
        public String read() {
            return null;
        }

        @Override
        public void open(@NonNull ExecutionContext executionContext) {
            if (openFailure != null) {
                throw openFailure;
            }
        }

        @Override
        public void update(@NonNull ExecutionContext executionContext) {
            if (updateFailure != null) {
                throw updateFailure;
            }
        }

        @Override
        public void close() {
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static final class StubEtlException extends EtlException {
        private StubEtlException(EtlErrorCategory category, String message) {
            super(category, message);
        }
    }
}


