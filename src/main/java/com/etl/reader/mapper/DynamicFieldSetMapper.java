package com.etl.reader.mapper;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.List;

import com.etl.common.util.TypeConversionUtils;
import com.etl.config.FieldDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import com.etl.config.source.ColumnConfig;

public class DynamicFieldSetMapper<T> implements FieldSetMapper<T> {
	
	private static final Logger logger = LoggerFactory.getLogger(DynamicFieldSetMapper.class);
	
	private final List<FieldDefinition> columns;
	private final Class<T> clazz;

	public DynamicFieldSetMapper(List<FieldDefinition> columns, Class<T> clazz) {
		this.columns = columns;
		this.clazz = clazz;
	}

	@Override
	public T mapFieldSet(FieldSet fieldSet) throws BindException {
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
				//	logger.info("Setting field: " + name + " | Type: " + type + " | Value: " + value); -- only enable for debugging
					setter.invoke(instance, value);
				}
			}
			return instance;
		} catch (Exception e) {
			logger.error("Error mapping fieldSet for class: {}", clazz.getName(), e);
			throw new RuntimeException("Error mapping fieldSet for class: " + clazz.getName(), e);
		}
	}

}
