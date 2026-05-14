package com.etl.mapping;

import com.etl.common.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Legacy reflection helper for simple field get/set access against maps and POJOs.
 *
 * <p><strong>Transition status:</strong> LEGACY.</p>
 *
 * <p>The active processor runtime now flows through {@link MappedFieldValueResolver}; this helper
 * remains only as a small utility for older code paths and tests. Keep behavior stable, but avoid
 * expanding it into the main mapping contract.</p>
 */
@SuppressWarnings("unused")
public class MappingEngine {

  /**
   * Reads one field value from either a map-backed record or a JavaBean-style object.
   */
    public <T> Object getValue(T source, String field) throws Exception {
        if (source instanceof Map)
            return ((Map<?, ?>) source).get(field);

        // POJO → use getter
        var getter = source.getClass()
                .getMethod("get" + StringUtils.capitalize(field));
        return getter.invoke(source);
    }

	/**
	 * Writes one field value to either a mutable map target or a JavaBean-style object.
	 */
    public void setValue(Object target, String field, Object value) throws Exception {
        if (target instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> targetMap = (Map<String, Object>) target;
            targetMap.put(field, value);
            return;
        }

        // POJO → use setter
        var setter = findSetter(target.getClass(), field);
        setter.invoke(target, value);
    }

    private Method findSetter(Class<?> cls, String field) {
        for (Method m : cls.getMethods()) {
            if (m.getName().equalsIgnoreCase("set" + field) &&
                    m.getParameterCount() == 1) {
                return m;
            }
        }
        throw new IllegalStateException("Setter not found: " + field);
    }
}
