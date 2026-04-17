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
		if (clazz.getSimpleName().equals(xmlConfig.getRecordElement())) {
			// Stream individual record elements for chunk-oriented XML writes.
			StaxEventItemWriter<Object> writer = new StaxEventItemWriter<>();
			writer.setResource(new FileSystemResource(path));
			writer.setRootTagName(xmlConfig.getRootElement());
			writer.setMarshaller(marshaller);
			writer.afterPropertiesSet();
			return writer;
		} else {
			// Use wrapper-based writing for tasklet/single-object XML output.
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
