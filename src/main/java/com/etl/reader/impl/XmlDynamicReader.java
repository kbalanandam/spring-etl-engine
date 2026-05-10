package com.etl.reader.impl;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.enums.ModelFormat;
import com.etl.reader.DynamicReader;
import com.etl.source.xml.runtime.XmlFlatteningResult;
import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import com.etl.source.xml.strategy.DirectXmlSourceStrategy;
import com.etl.source.xml.strategy.JobSpecificXmlStrategyResolver;
import com.etl.source.xml.strategy.NestedXmlSourceStrategy;
import com.etl.source.xml.strategy.XmlFlatteningStrategyNames;
import com.etl.source.xml.strategy.XmlSourceStrategy;
import com.etl.source.xml.strategy.XmlSourceStrategyRegistry;
import com.etl.source.xml.strategy.XmlSourceStrategySelector;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component("xml")
public class XmlDynamicReader<T> implements DynamicReader<T> {

	private final XmlSourceStrategySelector strategySelector;

	public XmlDynamicReader() {
		this(new XmlSourceStrategySelector(
				new XmlSourceStrategyRegistry(List.of(new DirectXmlSourceStrategy(), new NestedXmlSourceStrategy())),
				new JobSpecificXmlStrategyResolver(new StaticApplicationContext())
		));
	}

	@Autowired
	public XmlDynamicReader(XmlSourceStrategySelector strategySelector) {
		this.strategySelector = strategySelector;
	}

	@Override
	public ModelFormat getFormat() {
		return ModelFormat.XML;
	}

	@Override
	public ItemReader<T> getReader(SourceConfig config, Class<T> clazz) throws Exception {
		if (config == null || clazz == null) {
			throw new IllegalArgumentException("SourceConfig and target class must not be null.");
		}

		XmlSourceConfig xmlConfig = (XmlSourceConfig) config;
		if (!XmlFlatteningStrategyNames.DIRECT_XML.equalsIgnoreCase(xmlConfig.getFlatteningStrategy())) {
			return createFlatteningReader(xmlConfig, clazz);
		}

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

	@SuppressWarnings("unchecked")
	private ItemReader<T> createFlatteningReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		if (XmlFlatteningStrategyNames.NESTED_XML.equalsIgnoreCase(xmlConfig.getFlatteningStrategy())) {
			return createNestedFragmentReader(xmlConfig, clazz);
		}

		ClassLoader classLoader = clazz.getClassLoader();
		Class<?> rootClass = GeneratedModelClassResolver.resolveSourceRootClass(xmlConfig, classLoader);
		Object xmlRoot = unmarshalRoot(xmlConfig, rootClass);

		XmlSourceRuntimeContext context = XmlSourceRuntimeContext.builder()
				.sourceName(xmlConfig.getSourceName())
				.flatteningStrategy(xmlConfig.getFlatteningStrategy())
				.jobSpecificStrategyBean(xmlConfig.getJobSpecificStrategyBean())
				.xmlSourceConfig(xmlConfig)
				.rootClass(rootClass)
				.recordClass(clazz)
				.build();

		XmlSourceStrategy strategy = strategySelector.select(context);
		XmlFlatteningResult result = strategy.flatten(context, xmlRoot);
		return new FlattenedXmlItemReader<>((List<T>) result.getRows());
	}

	@SuppressWarnings("unchecked")
	private ItemReader<T> createNestedFragmentReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		Jaxb2Marshaller unmarshaller = new Jaxb2Marshaller();
		unmarshaller.setClassesToBeBound(clazz);
		unmarshaller.afterPropertiesSet();

		StaxEventItemReader<T> fragmentReader = new StaxEventItemReader<>();
		fragmentReader.setResource(new FileSystemResource(xmlConfig.getFilePath()));
		fragmentReader.setFragmentRootElementName(xmlConfig.getRecordElement());
		fragmentReader.setUnmarshaller(unmarshaller);
		fragmentReader.afterPropertiesSet();

		List<Object> flattenedRows = new ArrayList<>();
		fragmentReader.open(new ExecutionContext());
		try {
			XmlSourceRuntimeContext context = XmlSourceRuntimeContext.builder()
					.sourceName(xmlConfig.getSourceName())
					.flatteningStrategy(xmlConfig.getFlatteningStrategy())
					.jobSpecificStrategyBean(xmlConfig.getJobSpecificStrategyBean())
					.xmlSourceConfig(xmlConfig)
					.recordClass(clazz)
					.build();

			XmlSourceStrategy strategy = strategySelector.select(context);
			T fragment;
			while ((fragment = fragmentReader.read()) != null) {
				flattenedRows.addAll(strategy.flatten(context, fragment).getRows());
			}
		} finally {
			fragmentReader.close();
		}

		return new FlattenedXmlItemReader<>((List<T>) flattenedRows);
	}

	private Object unmarshalRoot(XmlSourceConfig xmlConfig, Class<?> rootClass) throws Exception {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(rootClass.getClassLoader());
		try {
			JAXBContext context = JAXBContext.newInstance(rootClass);
			Unmarshaller unmarshaller = context.createUnmarshaller();
			return unmarshaller.unmarshal(new FileSystemResource(xmlConfig.getFilePath()).getFile());
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	private static final class FlattenedXmlItemReader<T> implements ItemReader<T> {

		private final Iterator<T> iterator;

		private FlattenedXmlItemReader(List<T> items) {
			this.iterator = items.iterator();
		}

		@Override
		public T read() {
			return iterator.hasNext() ? iterator.next() : null;
		}
	}
}

