package com.etl.config.target;

public enum WriteMode {
    INSERT;

    public static WriteMode fromString(String value) {
        for (WriteMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported write mode: " + value);
    }
}

