package com.etl.common.util;

import com.etl.common.exception.TypeConversionException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class TypeConversionUtils {

    private TypeConversionUtils() {}

    /**
     * Convert a raw value (string/number/object) into a specific Java type.
     */
    public static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;

        String strValue = value.toString().trim();
        if (strValue.isEmpty()) return null;

        // Already compatible
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        try {
            return switch (targetType.getName()) {
                case "java.lang.Integer", "int" -> Integer.parseInt(strValue);
                case "java.lang.Long", "long" -> Long.parseLong(strValue);
                case "java.lang.Double", "double" -> Double.parseDouble(strValue);
                case "java.lang.Float", "float" -> Float.parseFloat(strValue);
                case "java.lang.Boolean", "boolean" -> Boolean.parseBoolean(strValue);
                case "java.lang.String" -> strValue;
                case "java.time.LocalDate" -> LocalDate.parse(strValue, DateTimeFormatter.ISO_DATE);
                default -> value; // fallback (future custom types)
            };
        } catch (Exception e) {
            throw new TypeConversionException(
                    "Failed converting '" + value + "' to " + targetType, e
            );
        }
    }

    /**
     * Map CSV/XML config type → Java type (for model generation, not runtime conversion)
     */
    public static String mapToJavaType(String type) {
        return switch (type.toLowerCase()) {
            case "int", "integer" -> "Integer";
            case "long" -> "Long";
            case "double" -> "Double";
            case "float" -> "Float";
            case "boolean" -> "Boolean";
            case "string" -> "String";
            default -> "String";
        };
    }
}
