package com.etl.writer.spi;

import com.etl.writer.DynamicWriter;

import java.util.List;

/**
 * Supplies runtime writer registrations for manual/non-Spring bootstrap paths.
 */
public interface WriterExtensionProvider {

    /**
     * Stable provider id used for deterministic ordering and diagnostics.
     */
    String providerId();

    /**
     * Lower values load first when multiple providers are discovered.
     */
    default int order() {
        return 0;
    }

    /**
     * Explicitly marks this provider as an override source for conflicting keys.
     */
    default boolean isOverride() {
        return false;
    }

    /**
     * Writer registrations contributed by this provider.
     */
    List<DynamicWriter> writers();
}

