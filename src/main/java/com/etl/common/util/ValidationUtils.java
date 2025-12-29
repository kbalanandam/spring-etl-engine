package com.etl.common.util;

import java.util.Collection;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    /** Validates that all provided strings are non-null and non-blank */
    public static void requireNonBlank(String message, String... values) {
        if (values == null) {
            throw new IllegalArgumentException(message + " - values array is null");
        }

        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(message + " - value is null or blank");
            }
        }
    }

    /** Validates object is not null */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    /** Validates collection is non-null and not empty */
    public static <T extends Collection<?>> T requireNonEmpty(
            T collection,
            String message
    ) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }
}
