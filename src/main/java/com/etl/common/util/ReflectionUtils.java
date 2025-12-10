package com.etl.common.util;

import com.etl.common.exception.ReflectionAccessException;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class ReflectionUtils {

    private ReflectionUtils() {}

    /**
     * Get a field value using reflection.
     * Supports private + inherited fields.
     */
   /* public static Object getField(Object obj, String fieldName) {
        if (obj == null) {
            throw new IllegalArgumentException("Object is null while trying to read field: " + fieldName);
        }

        Field field = findField(obj.getClass(), fieldName);

        if (field == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in class: " + obj.getClass());
        }

        try {
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("Unable to read field '" + fieldName + "' from " + obj.getClass(), e);
        }
    } *

    /**
     * Set a field value using reflection.
     * Supports private + inherited fields.
     */
  /*  public static void setField(Object obj, String fieldName, Object value) {
        if (obj == null) {
            throw new IllegalArgumentException("Object is null while trying to write field: " + fieldName);
        }

        Field field = findField(obj.getClass(), fieldName);

        if (field == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found in class: " + obj.getClass());
        }

        try {
            field.setAccessible(true);
            field.set(obj, TypeConversionUtils.convertValue(value, field.getType()));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to write field '" + fieldName + "' on " + obj.getClass() + " with value: " + value, e);
        }
    }
*/
    /**
     * Recursively find a field in the class + its superclasses.
     */
    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Convert raw values (strings, numbers) to the appropriate field type.
     * Extend this method as needed.
     */
  /*  private static Object castValue(Object value, Class<?> targetType) {

        if (value == null) {
            return null;
        }

        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // Basic conversions
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(value.toString());
        }

        if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(value.toString());
        }

        if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(value.toString());
        }

        if (targetType == Float.class || targetType == float.class) {
            return Float.parseFloat(value.toString());
        }

        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }

        if (targetType == String.class) {
            return value.toString();
        }

        // Unknown type → let Spring or custom convertor handle it later
        return value;
    } */

    /**
     * Create an instance of the given class.
     * If it's a Map, returns a HashMap.
     */
    @SuppressWarnings("unchecked")
    public static <T> T createInstance(Class<T> clazz) {
        try {
            if (clazz.equals(Map.class)) {
                return (T) new HashMap<String, Object>();
            }
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new ReflectionAccessException("Failed to create instance of " + clazz, e);
        }
    }

    /**
     * Get a field value using reflection.
     * Supports nested fields separated by dot: "address.city"
     */
    public static Object getFieldValue(Object obj, String fieldPath) {
        try {
            String[] parts = fieldPath.split("\\.");
            Object current = obj;
            for (String part : parts) {
                Field field = current.getClass().getDeclaredField(part);
                field.setAccessible(true);
                current = field.get(current);
                if (current == null) break;
            }
            return current;
        } catch (Exception e) {
            throw new ReflectionAccessException("Failed to get field value: " + fieldPath, e);
        }
    }

    /**
     * Set a field value using reflection.
     * Supports nested fields separated by dot: "address.city"
     */
    public static void setFieldValue(Object obj, String fieldPath, Object value) {
        try {
            String[] parts = fieldPath.split("\\.");
            Object current = obj;
            for (int i = 0; i < parts.length - 1; i++) {
                Field field = current.getClass().getDeclaredField(parts[i]);
                field.setAccessible(true);
                Object next = field.get(current);
                if (next == null) {
                    next = field.getType().getDeclaredConstructor().newInstance();
                    field.set(current, next);
                }
                current = next;
            }
            Field field = current.getClass().getDeclaredField(parts[parts.length - 1]);
            field.setAccessible(true);
            field.set(current, value);
        } catch (Exception e) {
            throw new ReflectionAccessException("Failed to set field value: " + fieldPath, e);
        }
    }
}
