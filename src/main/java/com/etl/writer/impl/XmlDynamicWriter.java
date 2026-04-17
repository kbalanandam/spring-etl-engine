package com.etl.writer.impl;

import com.etl.config.target.TargetConfig;
import com.etl.config.target.XmlTargetConfig;
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

		XmlTargetConfig xmlConfig = (XmlTargetConfig) config;

		String path = xmlConfig.getFilePath();
		if (path.endsWith("/") || new File(path).isDirectory()) {
			path += config.getTargetName().toLowerCase() + ".xml";
		}
		Jaxb2Marshaller marshaller = jaxbMarshaller(clazz);
		// Use record count threshold to select writer
		int recordCount = xmlConfig.getFields() != null ? xmlConfig.getFields().size() : 0;
		int chunkThreshold = 100000; // You can make this configurable
		if (recordCount > chunkThreshold) {
			// Use StaxEventItemWriter for large files (chunked streaming)
			StaxEventItemWriter<Object> writer = new StaxEventItemWriter<>();
			writer.setResource(new FileSystemResource(path));
			writer.setRootTagName(xmlConfig.getRootElement());
			writer.setMarshaller(marshaller);
			writer.afterPropertiesSet();
			return writer;
		} else {
			// Use SingleObjectXmlWriter for small/medium files
			return new SingleObjectXmlWriter(marshaller, path);
		}
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
