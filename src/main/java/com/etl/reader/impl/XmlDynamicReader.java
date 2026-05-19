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
import com.etl.runtime.FileSourceArtifactSupport;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.lang.NonNull;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Runtime XML reader builder for direct and flattened XML source flows.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This reader translates the selected XML source config into one of the active XML
 * runtime read paths:</p>
 * <ul>
 *   <li><strong>Direct XML</strong> - stream record fragments directly into the generated
 *   source record class</li>
 *   <li><strong>Nested/flattened XML</strong> - unmarshal or stream XML fragments first and
 *   then flatten them into generated row objects through the selected XML strategy</li>
 * </ul>
 *
 * <p>The reader owns only the XML-source dispatch contract: it resolves generated source classes,
 * chooses the direct-vs-flattened read path, applies the shared reader failure wrapper, and passes
 * flattening work to the active XML strategy layer. That keeps bundle config, generated classes,
 * and flattening behavior aligned without spreading XML-specific conditionals across the runtime.</p>
 */
@Component("xml")
public class XmlDynamicReader<T> implements DynamicReader<T> {

	private static final FileSourceArtifactSupport FILE_SOURCE_ARTIFACT_SUPPORT = new FileSourceArtifactSupport();
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

	/*
	 * Builds the Spring Batch reader for the selected XML source config.
	 *
	 * <p>{@code DIRECT_XML} streams configured record fragments straight into the generated source
	 * record class. All other shipped XML modes first choose a flattening strategy and then expose the
	 * resulting rows through a normal {@link ItemReader} contract so the rest of the runtime can stay
	 * format-agnostic.</p>
	 */
	@Override
	public ItemReader<T> getReader(SourceConfig config, Class<T> clazz) throws Exception {
		if (config == null || clazz == null) {
			throw new IllegalArgumentException("SourceConfig and target class must not be null.");
		}

		XmlSourceConfig xmlConfig = (XmlSourceConfig) config;
		// DIRECT_XML means the generated record class already matches the fragment
		// shape the runtime should stream from the source document.
		if (XmlFlatteningStrategyNames.DIRECT_XML.equalsIgnoreCase(xmlConfig.getFlatteningStrategy())) {
			return runtimeReader(createDirectRecordReader(xmlConfig, clazz), xmlConfig);
		}

		// All other shipped XML source modes flow through the flattening path where
		// a strategy converts XML structures into generated source rows.
		return runtimeReader(createFlatteningPathReader(xmlConfig, clazz), xmlConfig);
	}

	/**
	 * Wraps the concrete XML reader in the shared categorized-failure adapter used by other source
	 * formats.
	 */
	private ItemReader<T> runtimeReader(ItemReader<T> reader, XmlSourceConfig xmlConfig) {
		// Give XML the same categorized runtime error surface as CSV and relational
		// readers even though the concrete parsing path may be very different.
		return new RuntimeCategorizingItemStreamReader<>(reader, xmlConfig.getSourceName());
	}

	private ItemStreamReader<T> createDirectRecordReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		return createRecordFragmentReader(xmlConfig, clazz);
	}

	/**
	 * Chooses the active flattening path for non-direct XML sources.
	 *
	 * <p>Nested XML stays fragment-oriented so each record fragment can emit zero, one, or many rows.
	 * Root-level strategies unmarshal the configured root wrapper first and then flatten the full
	 * object graph into rows.</p>
	 */
	private ItemReader<T> createFlatteningPathReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		if (XmlFlatteningStrategyNames.NESTED_XML.equalsIgnoreCase(xmlConfig.getFlatteningStrategy())) {
			// Nested XML is handled fragment-by-fragment so each buffered fragment can
			// emit zero, one, or many flattened output rows.
			return createNestedStreamingReader(xmlConfig, clazz);
		}

		// Root-level flattening unmarshals the configured XML root first and then asks
		// the selected strategy to emit all flattened rows from that object graph.
		return createRootFlatteningReader(xmlConfig, clazz);
	}

	/*
	 * Unmarshals the full generated XML root wrapper and precomputes the flattened rows in memory.
	 *
	 * <p>This path is used for root-oriented strategies where the strategy needs the whole object
	 * graph instead of one record fragment at a time.</p>
	 */
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

	/**
	 * Streams record fragments and buffers the flattened rows produced from each fragment before they
	 * are returned one-by-one through Spring Batch's {@code read()} contract.
	 */
	private ItemReader<T> createNestedStreamingReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		XmlSourceRuntimeContext context = createRuntimeContext(xmlConfig, null, clazz);

		XmlSourceStrategy strategy = strategySelector.select(context);
		return new FragmentBufferingFlattenedXmlReader<>(createRecordFragmentReader(xmlConfig, clazz), context, strategy);
	}

	/**
	 * Creates the shared XML strategy context that ties together the selected config, generated root
	 * and record classes, and any job-specific strategy bean reference.
	 */
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

	/**
	 * Builds the fragment reader that binds each configured record element to the generated source
	 * record class.
	 */
	private StaxEventItemReader<T> createRecordFragmentReader(XmlSourceConfig xmlConfig, Class<T> clazz) throws Exception {
		// Fragment readers bind each configured record element to the generated record
		// class so Spring Batch can stream XML items without loading the full document.
		Jaxb2Marshaller unmarshaller = new Jaxb2Marshaller();
		unmarshaller.setClassesToBeBound(clazz);
		unmarshaller.afterPropertiesSet();

		StaxEventItemReader<T> fragmentReader = new StaxEventItemReader<>();
		fragmentReader.setResource(new FileSystemResource(FILE_SOURCE_ARTIFACT_SUPPORT.resolveReadablePath(xmlConfig)));
		fragmentReader.setFragmentRootElementName(xmlConfig.getRecordElement());
		fragmentReader.setUnmarshaller(unmarshaller);
		fragmentReader.afterPropertiesSet();
		return fragmentReader;
	}

	/**
	 * Unmarshals the full XML document into the generated root wrapper class.
	 *
	 * <p>The temporary thread context class loader swap keeps JAXB aligned with generated classes that
	 * may live in job-scoped packages outside the application's default class loader view.</p>
	 */
	private Object unmarshalRoot(XmlSourceConfig xmlConfig, Class<?> rootClass) throws Exception {
		// Root-object unmarshalling uses the generated root wrapper class and temporarily
		// switches the thread context class loader so JAXB sees the generated types.
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(rootClass.getClassLoader());
		try {
			JAXBContext context = JAXBContext.newInstance(rootClass);
			Unmarshaller unmarshaller = context.createUnmarshaller();
			return unmarshaller.unmarshal(new FileSystemResource(FILE_SOURCE_ARTIFACT_SUPPORT.resolveReadablePath(xmlConfig)).getFile());
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	/**
	 * Simple in-memory reader over rows that were fully flattened before step execution begins.
	 */
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

	/**
	 * Adapter that lets fragment-based XML flattening participate in the normal Spring Batch reader
	 * contract.
	 *
	 * <p>One XML fragment can expand into multiple flattened rows, but Spring Batch still expects each
	 * {@link #read()} call to return at most one item. This reader buffers the rows emitted from the
	 * current fragment and drains them one at a time before reading the next fragment.</p>
	 */
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
			// Drain all rows derived from the current fragment before advancing to the next one so the
			// downstream chunk/tasklet path still sees a normal one-item-per-read contract.
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
		public void open(@NonNull ExecutionContext executionContext) {
			bufferedRows.clear();
			fragmentReader.open(executionContext);
		}

		@Override
		public void update(@NonNull ExecutionContext executionContext) {
			fragmentReader.update(executionContext);
		}

		@Override
		public void close() {
			bufferedRows.clear();
			fragmentReader.close();
		}

		@SuppressWarnings("unchecked")
		private void bufferFlattenedRows(Object fragment) {
			// The selected XML strategy owns the fragment-to-row mapping contract; this adapter only
			// buffers those rows so they can be surfaced incrementally through Spring Batch.
			for (Object row : strategy.flatten(context, fragment).getRows()) {
				bufferedRows.addLast((T) row);
			}
		}
	}
}

