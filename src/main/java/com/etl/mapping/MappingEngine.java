package com.etl.mapping;

import com.etl.common.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

public class MappingEngine {

    public <T> Object getValue(T source, String field) throws Exception {
        if (source instanceof Map)
            return ((Map<?, ?>) source).get(field);

        // POJO → use getter
        var getter = source.getClass()
                .getMethod("get" + StringUtils.capitalize(field));
        return getter.invoke(source);
    }

    public void setValue(Object target, String field, Object value) throws Exception {
        if (target instanceof Map) {
            ((Map<String, Object>) target).put(field, value);
            return;
        }

        // POJO → use setter
        var setter = findSetter(target.getClass(), field, value);
        setter.invoke(target, value);
    }

    private Method findSetter(Class<?> cls, String field, Object value) throws Exception {
        for (Method m : cls.getMethods()) {
            if (m.getName().equalsIgnoreCase("set" + field) &&
                    m.getParameterCount() == 1) {
                return m;
            }
        }
        throw new IllegalStateException("Setter not found: " + field);
    }
}
