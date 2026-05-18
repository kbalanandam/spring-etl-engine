package com.etl.writer.impl;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.GeneratedModelNamingPolicy;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.XmlTargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.writer.DynamicWriter;
import com.etl.writer.exception.MarshallerException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

/**
 * XML writer adapter for the active runtime writer factory.
 *
 * <p>This class chooses between the two supported XML write modes used by the
 * product today:</p>
 * <ul>
 *   <li><strong>Chunk-oriented record streaming</strong> via {@link StagedStaxEventItemWriter}
 *   when the runtime is writing individual XML record objects.</li>
 *   <li><strong>Single-object wrapper writing</strong> via {@link SingleObjectXmlWriter}
 *   when the runtime is writing a wrapper/root object that already contains the
 *   collection of XML records.</li>
 * </ul>
 *
 * <p>The selection must remain compatible with the generated-model naming bridge.
 * Runtime code prefers the resolver-derived processing class name, while also
 * tolerating the expected simple class name for compatibility with tests and
 * bridge-era flows that provide an equivalent record type from a different package.</p>
 */
@Component("xmlWriter")
public class XmlDynamicWriter implements DynamicWriter {

	@Override
	public ModelFormat getFormat() {
		return ModelFormat.XML;
	}

	@Override
	public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) throws Exception {

		XmlTargetConfig xmlConfig = (XmlTargetConfig) config;

		String path = resolveOutputPath(xmlConfig, config);
		Jaxb2Marshaller marshaller = jaxbMarshaller(clazz);
		// The resolver gives the canonical processing-record class expected by the
		// active generated-model contract. The naming-policy simple name check keeps
		// the writer compatible with equivalent bridge/test record classes whose
		// package differs but whose logical XML-processing role is the same.
		String expectedProcessingClassName = GeneratedModelClassResolver.resolveTargetProcessingClassName(xmlConfig);
		String expectedProcessingSimpleName = GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(xmlConfig);
		if (clazz.getName().equals(expectedProcessingClassName)
				|| clazz.getSimpleName().equals(expectedProcessingSimpleName)) {
			// Record-class writes are chunk-oriented: Spring Batch streams one XML
			// record at a time under the configured root element and stages the file
			// until step completion confirms that the output should be promoted.
			StagedStaxEventItemWriter<Object> writer = new StagedStaxEventItemWriter<>(path, xmlConfig.isPackageAsZip());
			writer.setRootTagName(xmlConfig.getRootElement());
			writer.setMarshaller(marshaller);
			writer.afterPropertiesSet();
			return writer;
		} else {
			// Wrapper/root-class writes are tasklet-style: the caller supplies one
			// already-assembled object graph representing the final XML document.
			return new SingleObjectXmlWriter(marshaller, path, xmlConfig.isPackageAsZip());
		}
	}

	/**
	 * Builds a JAXB marshaller for the concrete runtime class that will be written.
	 *
	 * <p>The same marshaller setup is used for both streaming record writes and
	 * single-object wrapper writes so the caller only needs to supply the class that
	 * should be bound for the active writer mode.</p>
	 */
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

	/**
	 * Resolves the final XML output path from the target config.
	 *
	 * <p>If the configured path is blank, it is passed through unchanged so existing
	 * validation or downstream failures remain explicit. If the configured path looks
	 * like a directory, the writer appends a deterministic file name based on
	 * {@code targetName}. Otherwise the path is treated as the final XML file path.</p>
	 */
	private String resolveOutputPath(XmlTargetConfig xmlConfig, TargetConfig config) {
		String configuredPath = xmlConfig.getFilePath();
		if (configuredPath == null || configuredPath.isBlank()) {
			return configuredPath;
		}

		String trimmedPath = configuredPath.trim();
		if (trimmedPath.endsWith("/") || trimmedPath.endsWith("\\") || new File(trimmedPath).isDirectory()) {
			return Path.of(trimmedPath, config.getTargetName().toLowerCase() + ".xml")
					.toString();
		}
		return trimmedPath;
	}

}
