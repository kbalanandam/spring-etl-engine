package com.etl.reader.mapper;

import com.etl.common.util.TypeConversionUtils;
import com.etl.config.FieldDefinition;
import com.etl.exception.RuntimeEtlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.lang.NonNull;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class DynamicFieldSetMapper<T> implements FieldSetMapper<T> {
	
	private static final Logger logger = LoggerFactory.getLogger(DynamicFieldSetMapper.class);

	private final List<FieldBinding> fieldBindings;
	private final Class<T> clazz;

	public DynamicFieldSetMapper(List<? extends FieldDefinition> columns, Class<T> clazz) {
		this.clazz = clazz;
		this.fieldBindings = resolveFieldBindings(columns, clazz);
	}

	@Override
	public @NonNull T mapFieldSet(@NonNull FieldSet fieldSet) {
		try {
			T instance = clazz.getDeclaredConstructor().newInstance();
			for (FieldBinding fieldBinding : fieldBindings) {
				Object value = TypeConversionUtils.convertValue(
						fieldSet.readString(fieldBinding.name()),
						fieldBinding.propertyType());
				logger.debug("Setting field: {} | Type: {} | Value: {}", fieldBinding.name(), fieldBinding.type(), value);
				fieldBinding.setter().invoke(instance, value);
			}
			return instance;
		} catch (Exception e) {
			logger.error("READER_MAPPING_FAILURE category=runtime targetClass={} message={}", clazz.getName(), e.getMessage(), e);
			throw new RuntimeEtlException("Failed to map input record to class '" + clazz.getName() + "'.", e);
		}
	}

	private static List<FieldBinding> resolveFieldBindings(List<? extends FieldDefinition> columns, Class<?> clazz) {
		try {
			List<FieldBinding> bindings = new ArrayList<>();
			for (FieldDefinition column : columns) {
				PropertyDescriptor propertyDescriptor = findPropertyDescriptor(column.getName(), clazz);
				if (propertyDescriptor == null) {
					throw new RuntimeEtlException(
							"Configured field '" + column.getName() + "' does not match a readable/writable property on class '"
									+ clazz.getName() + "'."
					);
				}
				Method setter = propertyDescriptor.getWriteMethod();
				if (setter == null) {
					throw new RuntimeEtlException(
							"Configured field '" + column.getName() + "' is not writable on class '" + clazz.getName() + "'."
					);
				}
				bindings.add(new FieldBinding(column.getName(), column.getType(), propertyDescriptor.getPropertyType(), setter));
			}
			return List.copyOf(bindings);
		} catch (RuntimeEtlException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeEtlException("Failed to initialize field mappings for class '" + clazz.getName() + "'.", e);
		}
	}

	private static PropertyDescriptor findPropertyDescriptor(String propertyName, Class<?> clazz) throws Exception {
		for (PropertyDescriptor descriptor : Introspector.getBeanInfo(clazz).getPropertyDescriptors()) {
			if (descriptor.getName().equals(propertyName)) {
				return descriptor;
			}
		}
		return null;
	}

	private record FieldBinding(String name, String type, Class<?> propertyType, Method setter) {
	}

}
