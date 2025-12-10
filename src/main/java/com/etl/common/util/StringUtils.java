package com.etl.common.util;

public class StringUtils {

    private StringUtils() {
        // prevent instantiation
    }

    public static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
