package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

abstract class AbstractXmlSourceStrategy implements XmlSourceStrategy {

    protected List<?> extractRecords(XmlSourceRuntimeContext context, Object xmlRoot) {
        if (xmlRoot == null) {
            return List.of();
        }
        if (xmlRoot instanceof Collection<?> collection) {
            return List.copyOf(collection);
        }
        if (context.getRecordClass() != null && context.getRecordClass().isInstance(xmlRoot)) {
            return List.of(xmlRoot);
        }

        List<Field> readableFields = readableFields(xmlRoot.getClass());
        String configuredRecordElement = context.getXmlSourceConfig() != null
                ? context.getXmlSourceConfig().getRecordElement()
                : null;

        for (Field field : readableFields) {
            Object value = readField(xmlRoot, field);
            if (value == null) {
                continue;
            }
            if (value instanceof Collection<?> collection) {
                if (configuredRecordElement == null
                        || configuredRecordElement.equalsIgnoreCase(resolveFieldName(field))) {
                    return List.copyOf(collection);
                }
            }
            if (context.getRecordClass() != null && context.getRecordClass().isInstance(value)) {
                return List.of(value);
            }
        }

        return List.of(xmlRoot);
    }

    protected Map<String, Object> extractMappedValues(Object record, Map<String, String> fieldMappings) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            row.put(entry.getKey(), extractValueByPath(record, entry.getValue()));
        }
        return row;
    }

    protected void flattenRecursively(Map<String, Object> row, String prefix, Object value) {
        if (value == null) {
            return;
        }
        if (isSimpleValueType(value.getClass())) {
            row.put(prefix, value);
            return;
        }
        if (value instanceof Collection<?> collection) {
            flattenCollection(row, prefix, collection);
            return;
        }
        for (Field field : readableFields(value.getClass())) {
            Object fieldValue = readField(value, field);
            if (fieldValue == null) {
                continue;
            }
            String key = prefix == null || prefix.isBlank()
                    ? resolveFieldName(field)
                    : prefix + "." + resolveFieldName(field);
            if (isSimpleValueType(fieldValue.getClass())) {
                row.put(key, fieldValue);
            } else if (fieldValue instanceof Collection<?> collection) {
                flattenCollection(row, key, collection);
            } else {
                flattenRecursively(row, key, fieldValue);
            }
        }
    }

    protected Object extractValueByPath(Object source, String path) {
        if (source == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = source;
        for (String rawSegment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = resolveSegment(current, PathSegment.parse(rawSegment));
        }
        return current;
    }

    private Object resolveSegment(Object current, PathSegment segment) {
        if (current instanceof Collection<?> collection) {
            List<Object> values = new ArrayList<>();
            if (segment.index() != null) {
                Object indexed = collection.stream().skip(segment.index()).findFirst().orElse(null);
                return indexed == null ? null : resolveSegment(indexed, new PathSegment(segment.name(), null));
            }
            for (Object item : collection) {
                Object value = resolveSegment(item, segment);
                if (value == null) {
                    continue;
                }
                if (value instanceof Collection<?> nestedCollection) {
                    values.addAll(nestedCollection);
                } else {
                    values.add(value);
                }
            }
            return values;
        }

        Field field = findMatchingField(current.getClass(), segment.name());
        if (field == null) {
            return null;
        }
        Object value = readField(current, field);
        if (segment.index() == null || value == null) {
            return value;
        }
        if (!(value instanceof List<?> listValue)) {
            return null;
        }
        return segment.index() < listValue.size() ? listValue.get(segment.index()) : null;
    }

    private void flattenCollection(Map<String, Object> row, String key, Collection<?> collection) {
        if (collection.isEmpty()) {
            row.put(key, List.of());
            return;
        }
        if (collection.stream().allMatch(this::isSimpleValue)) {
            row.put(key, List.copyOf(collection));
            return;
        }
        int index = 0;
        for (Object item : collection) {
            flattenRecursively(row, key + "[" + index + "]", item);
            index++;
        }
    }

    protected boolean isSimpleValue(Object value) {
        return value == null || isSimpleValueType(value.getClass());
    }

    protected boolean isSimpleValueType(Class<?> type) {
        return type.isPrimitive()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class.equals(type)
                || Character.class.equals(type)
                || BigDecimal.class.isAssignableFrom(type)
                || BigInteger.class.isAssignableFrom(type)
                || java.util.Date.class.isAssignableFrom(type)
                || Temporal.class.isAssignableFrom(type)
                || Enum.class.isAssignableFrom(type);
    }

    protected String resolveFieldName(Field field) {
        XmlElement xmlElement = field.getAnnotation(XmlElement.class);
        if (xmlElement != null && xmlElement.name() != null && !xmlElement.name().isBlank() && !"##default".equals(xmlElement.name())) {
            return xmlElement.name();
        }
        XmlAttribute xmlAttribute = field.getAnnotation(XmlAttribute.class);
        if (xmlAttribute != null && xmlAttribute.name() != null && !xmlAttribute.name().isBlank() && !"##default".equals(xmlAttribute.name())) {
            return xmlAttribute.name();
        }
        return field.getName();
    }

    protected List<Field> readableFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    protected Object readField(Object instance, Field field) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read XML field '" + field.getName() + "'.", e);
        }
    }

    private Field findMatchingField(Class<?> type, String segment) {
        String normalizedSegment = segment.toLowerCase(Locale.ROOT);
        for (Field field : readableFields(type)) {
            String javaName = field.getName().toLowerCase(Locale.ROOT);
            String xmlName = resolveFieldName(field).toLowerCase(Locale.ROOT);
            if (javaName.equals(normalizedSegment) || xmlName.equals(normalizedSegment)) {
                return field;
            }
        }
        return null;
    }

    private record PathSegment(String name, Integer index) {
        private static PathSegment parse(String rawSegment) {
            int bracketIndex = rawSegment.indexOf('[');
            if (bracketIndex < 0 || !rawSegment.endsWith("]")) {
                return new PathSegment(rawSegment, null);
            }
            String name = rawSegment.substring(0, bracketIndex);
            String indexToken = rawSegment.substring(bracketIndex + 1, rawSegment.length() - 1);
            return new PathSegment(name, Integer.parseInt(indexToken));
        }
    }
}

