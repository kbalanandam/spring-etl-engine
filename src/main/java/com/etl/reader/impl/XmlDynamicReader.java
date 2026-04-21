package com.etl.reader.impl;

import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.reader.DynamicReader;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

@Component("xml")
public class XmlDynamicReader<T> implements DynamicReader<T> {

	@Override
	public String getType() {
		return "xml";
	}

	@Override
	public ItemReader<T> getReader(SourceConfig config, Class<T> clazz) throws Exception {
		if (config == null || clazz == null) {
			throw new IllegalArgumentException("SourceConfig and target class must not be null.");
		}

		XmlSourceConfig xmlConfig = (XmlSourceConfig) config;

		Jaxb2Marshaller unmarshaller = new Jaxb2Marshaller();
		unmarshaller.setClassesToBeBound(clazz);
		unmarshaller.afterPropertiesSet();

		StaxEventItemReader<T> reader = new StaxEventItemReader<>();
		reader.setResource(new FileSystemResource(xmlConfig.getFilePath()));
		reader.setFragmentRootElementName(xmlConfig.getRecordElement());
		reader.setUnmarshaller(unmarshaller);
		reader.afterPropertiesSet();

		return reader;
	}
}
