package com.etl.writer.impl;

import com.etl.config.target.TargetConfig;
import com.etl.writer.DynamicWriter;
import com.etl.writer.exception.MarshallerException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.xml.StaxEventItemWriter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

import java.io.File;

@Component("xmlWriter")
public class XmlDynamicWriter implements DynamicWriter {

	@Override
	public String getType() {
		return "xml";
	}

	@Override
	public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) throws Exception {
		StaxEventItemWriter<Object> writer = new StaxEventItemWriter<>();

		String path = config.getFilePath();
		if (path.endsWith("/") || new File(path).isDirectory()) {
			path += config.getTargetName().toLowerCase() + ".xml";
		}
		writer.setResource(new FileSystemResource(path));
		writer.setRootTagName(config.getTargetName());
		writer.setMarshaller(jaxbMarshaller(clazz));
		writer.afterPropertiesSet();
		return writer;
	}

	private Jaxb2Marshaller jaxbMarshaller(Class<?> clazz) {
		Jaxb2Marshaller marshaller = new Jaxb2Marshaller();

		marshaller.setClassesToBeBound(clazz);
		try {
			marshaller.afterPropertiesSet();
		} catch (Exception e) {
			throw new MarshallerException("Failed to initialize JAXB Marshaller", e);
		}
		return marshaller;
	}

}
