package com.etl.reader.mapper;

import com.etl.common.util.TypeConversionUtils;
import com.etl.config.FieldDefinition;
import com.etl.exception.RuntimeEtlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.List;

public class DynamicFieldSetMapper<T> implements FieldSetMapper<T> {
	
	private static final Logger logger = LoggerFactory.getLogger(DynamicFieldSetMapper.class);
	
	private final List<? extends FieldDefinition> columns;
	private final Class<T> clazz;

	public DynamicFieldSetMapper(List<? extends FieldDefinition> columns, Class<T> clazz) {
		this.columns = columns;
		this.clazz = clazz;
	}

	@Override
	public T mapFieldSet(FieldSet fieldSet) {
		try {
			T instance = clazz.getDeclaredConstructor().newInstance();
			for (FieldDefinition col : columns) {
				String name = col.getName();
				String type = col.getType();

				PropertyDescriptor pd = new PropertyDescriptor(name, clazz);
				Method setter = pd.getWriteMethod();

				if (setter != null) {
					Object value = TypeConversionUtils.convertValue(
							fieldSet.readString(name),
							pd.getPropertyType());
                    logger.debug("Setting field: {} | Type: {} | Value: {}", name, type, value); // only enable for debugging
					setter.invoke(instance, value);
				}
			}
			return instance;
		} catch (Exception e) {
			logger.error("READER_MAPPING_FAILURE category=runtime targetClass={} message={}", clazz.getName(), e.getMessage(), e);
			throw new RuntimeEtlException("Failed to map input record to class '" + clazz.getName() + "'.", e);
		}
	}

}
