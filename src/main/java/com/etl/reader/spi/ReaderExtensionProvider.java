package com.etl.reader.spi;

import com.etl.reader.DynamicReader;

import java.util.List;

/**
 * Supplies runtime reader registrations for manual/non-Spring bootstrap paths.
 */
public interface ReaderExtensionProvider {

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
     * Reader registrations contributed by this provider.
     */
    List<DynamicReader<?>> readers();
}

