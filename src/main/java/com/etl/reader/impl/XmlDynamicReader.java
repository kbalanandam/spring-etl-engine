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
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
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
		if (XmlFlatteningStrategyNames.DIRECT_XML.equalsIgnoreCase(xmlConfig.getFlatteningStrategy())) {
			return runtimeReader(createDirectRecordReader(xmlConfig, clazz), xmlConfig);
		}

		return runtimeReader(createFlatteningPathReader(xmlConfig, clazz), xmlConfig);
	}

	private ItemReader<T> runtimeReader(ItemReader<T> reader, XmlSourceConfig xmlConfig) {
		return new RuntimeCategorizingItemStreamReader<>(reader, xmlConfig.getSourceName());
	}

	private ItemStreamReader<T> createDirectRecordReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		return createRecordFragmentReader(xmlConfig, clazz);
	}

	@SuppressWarnings("unchecked")
	private ItemReader<T> createFlatteningPathReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		if (XmlFlatteningStrategyNames.NESTED_XML.equalsIgnoreCase(xmlConfig.getFlatteningStrategy())) {
			return createNestedStreamingReader(xmlConfig, clazz);
		}

		return createRootFlatteningReader(xmlConfig, clazz);
	}

	@SuppressWarnings("unchecked")
	private ItemReader<T> createRootFlatteningReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		ClassLoader classLoader = clazz.getClassLoader();
		Class<?> rootClass = GeneratedModelClassResolver.resolveSourceRootClass(xmlConfig, classLoader);
		Object xmlRoot = unmarshalRoot(xmlConfig, rootClass);

		XmlSourceRuntimeContext context = createRuntimeContext(xmlConfig, rootClass, clazz);

		XmlSourceStrategy strategy = strategySelector.select(context);
		XmlFlatteningResult result = strategy.flatten(context, xmlRoot);
		return new PreFlattenedRowReader<>((List<T>) result.getRows());
	}

	@SuppressWarnings("unchecked")
	private ItemReader<T> createNestedStreamingReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		XmlSourceRuntimeContext context = createRuntimeContext(xmlConfig, null, clazz);

		XmlSourceStrategy strategy = strategySelector.select(context);
		return new FragmentBufferingFlattenedXmlReader<>(createRecordFragmentReader(xmlConfig, clazz), context, strategy);
	}

	private XmlSourceRuntimeContext createRuntimeContext(XmlSourceConfig xmlConfig, Class<?> rootClass, Class<T> recordClass) {
		return XmlSourceRuntimeContext.builder()
				.sourceName(xmlConfig.getSourceName())
				.flatteningStrategy(xmlConfig.getFlatteningStrategy())
				.jobSpecificStrategyBean(xmlConfig.getJobSpecificStrategyBean())
				.xmlSourceConfig(xmlConfig)
				.rootClass(rootClass)
				.recordClass(recordClass)
				.build();
	}

	private StaxEventItemReader<T> createRecordFragmentReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		Jaxb2Marshaller unmarshaller = new Jaxb2Marshaller();
		unmarshaller.setClassesToBeBound(clazz);
		unmarshaller.afterPropertiesSet();

		StaxEventItemReader<T> fragmentReader = new StaxEventItemReader<>();
		fragmentReader.setResource(new FileSystemResource(xmlConfig.getFilePath()));
		fragmentReader.setFragmentRootElementName(xmlConfig.getRecordElement());
		fragmentReader.setUnmarshaller(unmarshaller);
		fragmentReader.afterPropertiesSet();
		return fragmentReader;
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

	private static final class PreFlattenedRowReader<T> implements ItemReader<T> {

		private final Iterator<T> iterator;

		private PreFlattenedRowReader(List<T> items) {
			this.iterator = items.iterator();
		}

		@Override
		public T read() {
			return iterator.hasNext() ? iterator.next() : null;
		}
	}

	private static final class FragmentBufferingFlattenedXmlReader<T> implements ItemStreamReader<T> {

		private final ItemStreamReader<?> fragmentReader;
		private final XmlSourceRuntimeContext context;
		private final XmlSourceStrategy strategy;
		private final Deque<T> bufferedRows = new ArrayDeque<>();

		private FragmentBufferingFlattenedXmlReader(ItemStreamReader<?> fragmentReader,
										   XmlSourceRuntimeContext context,
										   XmlSourceStrategy strategy) {
			this.fragmentReader = fragmentReader;
			this.context = context;
			this.strategy = strategy;
		}

		@Override
		public T read() throws Exception {
			while (bufferedRows.isEmpty()) {
				Object fragment = fragmentReader.read();
				if (fragment == null) {
					return null;
				}
				bufferFlattenedRows(fragment);
			}
			return bufferedRows.removeFirst();
		}

		@Override
		public void open(ExecutionContext executionContext) {
			bufferedRows.clear();
			fragmentReader.open(executionContext);
		}

		@Override
		public void update(ExecutionContext executionContext) {
			fragmentReader.update(executionContext);
		}

		@Override
		public void close() {
			bufferedRows.clear();
			fragmentReader.close();
		}

		@SuppressWarnings("unchecked")
		private void bufferFlattenedRows(Object fragment) {
			for (Object row : strategy.flatten(context, fragment).getRows()) {
				bufferedRows.addLast((T) row);
			}
		}
	}
}

