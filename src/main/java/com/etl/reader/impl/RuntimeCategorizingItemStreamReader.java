package com.etl.reader.impl;

import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlException;
import com.etl.exception.EtlExceptionDetails;
import com.etl.exception.RuntimeEtlException;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.lang.NonNull;

/**
 * Shared cross-format adapter that gives concrete readers one consistent runtime-failure surface.
 *
 * <p>The shipped CSV, XML, and relational readers all build a format-specific Spring Batch
 * reader first and then pass that reader through this wrapper before the runtime uses it.
 * That keeps format-specific parsing/streaming logic inside the concrete reader while
 * centralizing one operator-facing rule here: uncategorized read and stream lifecycle
 * failures should surface as runtime failures with the active source name attached.</p>
 *
 * <p>This wrapper intentionally handles two related but different contracts:</p>
 * <ul>
 *   <li>{@link ItemReader} for normal {@link #read()} delegation</li>
 *   <li>{@link ItemStream} for optional Spring Batch lifecycle delegation during
 *   {@link #open(ExecutionContext)}, {@link #update(ExecutionContext)}, and {@link #close()}</li>
 * </ul>
 *
 * <p>Some delegates implement both contracts, while others are read-only. When a delegate does
 * not implement {@link ItemStream}, lifecycle methods become safe no-ops and the runtime still
 * gets the same categorized {@code read()} behavior.</p>
 *
 * <p>Already categorized {@link EtlException} instances are preserved as-is. This class only wraps
 * uncategorized failures so downstream diagnostics and log evidence stay consistent across source
 * formats.</p>
 */
public class RuntimeCategorizingItemStreamReader<T> implements ItemStreamReader<T> {

    private final ItemReader<T> delegate;
    private final ItemStream itemStreamDelegate;
    private final String sourceName;

    public RuntimeCategorizingItemStreamReader(ItemReader<T> delegate, String sourceName) {
        this.delegate = delegate;
        this.itemStreamDelegate = delegate instanceof ItemStream itemStream ? itemStream : null;
        this.sourceName = sourceName == null || sourceName.isBlank() ? "unnamed" : sourceName.trim();
    }

    /**
     * Delegate one record read while preserving any existing ETL-specific categorization.
     *
     * <p>If the underlying reader already raised a typed {@link EtlException}, this method does not
     * alter it. Any other exception is promoted to a {@link RuntimeEtlException} so cross-format
     * reader failures land on the runtime error path with source-aware context.</p>
     */
    @Override
    public T read() throws Exception {
        try {
            return delegate.read();
        } catch (EtlException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeEtlException("Failed while reading source '" + sourceName + "'.", e);
        }
    }

    /**
     * Open the delegate stream when the underlying reader participates in Spring Batch item-stream
     * lifecycle callbacks.
     *
     * <p>Delegates that do not implement {@link ItemStream} are allowed; in that case opening is a
     * no-op because there is no stream state to initialize.</p>
     */
    @Override
    public void open(@NonNull ExecutionContext executionContext) throws ItemStreamException {
        if (itemStreamDelegate == null) {
            return;
        }
        try {
            itemStreamDelegate.open(executionContext);
        } catch (ItemStreamException e) {
            throw wrapItemStreamFailure("open", e);
        }
    }

    /**
     * Forward state updates to the underlying stream-aware delegate when one exists.
     */
    @Override
    public void update(@NonNull ExecutionContext executionContext) throws ItemStreamException {
        if (itemStreamDelegate == null) {
            return;
        }
        try {
            itemStreamDelegate.update(executionContext);
        } catch (ItemStreamException e) {
            throw wrapItemStreamFailure("update", e);
        }
    }

    /**
     * Close the underlying stream-aware delegate and preserve any existing failure categorization.
     */
    @Override
    public void close() throws ItemStreamException {
        if (itemStreamDelegate == null) {
            return;
        }
        try {
            itemStreamDelegate.close();
        } catch (ItemStreamException e) {
            throw wrapItemStreamFailure("close", e);
        }
    }

    /**
     * Preserve already categorized stream failures and only wrap uncategorized ones.
     *
     * <p>This keeps purpose-built ETL exceptions intact while ensuring plain Spring Batch
     * {@link ItemStreamException} failures still become source-aware runtime diagnostics.</p>
     */
    private ItemStreamException wrapItemStreamFailure(String phase, ItemStreamException failure) {
        if (EtlExceptionDetails.categoryOf(failure) != EtlErrorCategory.UNCLASSIFIED) {
            return failure;
        }
        String message = "Failed to " + phase + " reader for source '" + sourceName + "'.";
        return new ItemStreamException(message, new RuntimeEtlException(message, failure));
    }
}

