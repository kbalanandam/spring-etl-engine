package com.etl.model.generator;

import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.XmlTargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.enums.ModelType;
import com.etl.model.exception.InvalidModelConfigException;
import com.etl.model.exception.ModelGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static com.etl.common.util.ValidationUtils.requireNonBlank;
import static com.etl.common.util.ValidationUtils.requireNonEmpty;

/**
 * Generates Java model classes for XML sources and targets using JAXB annotations.
 * Handles both {@link SourceConfig} and {@link TargetConfig} based on {@link ModelType}.
 * Only works with {@link XmlSourceConfig} and {@link XmlTargetConfig}.
 */
@Profile("dev")
@Component
public class XmlModelGenerator<T extends ModelConfig> implements ModelGenerator<T> {

	private static final Logger logger = LoggerFactory.getLogger(XmlModelGenerator.class);
	private static final ModelFormat MODEL_FORMAT = ModelFormat.XML;

	@Override
	public ModelFormat getType() {
		return MODEL_FORMAT;
	}
	/**
	 * Generates a Java model class file for the given configuration.
	 * The generated class will be annotated for JAXB XML binding.
	 *
	 * @param config the model configuration (must be {@link XmlSourceConfig} or {@link XmlTargetConfig})
	 * @throws Exception if file writing fails or the config type is unknown
	 */
	@Override
	public void generateModel(T config) throws Exception {

        String className;
		String packageName;
		String rootElement;
		List<? extends FieldDefinition> fields;

		if (config.getModelType() == ModelType.TARGET && config instanceof XmlTargetConfig xmlTarget) {

			className = xmlTarget.getTargetName();
			packageName = xmlTarget.getPackageName();
			rootElement = xmlTarget.getRootElement();
			fields = xmlTarget.getFields();

		} else if (config.getModelType() == ModelType.SOURCE && config instanceof XmlSourceConfig xmlSource) {

			className = xmlSource.getSourceName();
			packageName = xmlSource.getPackageName();
			rootElement = xmlSource.getRootElement();
			fields = xmlSource.getFields();

		} else {
			logger.error("XmlModelGenerator supports only XmlSourceConfig and XmlTargetConfig");
			throw new InvalidModelConfigException(
					"XmlModelGenerator supports only XmlSourceConfig and XmlTargetConfig"
			);
		}

		validate(className, packageName, rootElement, fields);

		String dirPath = "src/main/java/" + packageName.replace(".", "/");
		createDirectory(dirPath);

		String javaSource = generateSource(packageName, className, rootElement, fields);
		writeFile(dirPath, className, javaSource);

		logger.info("XML model generated: {}.{}", packageName, className);
	}

	/* -------------------- helpers -------------------- */

	private void validate(String className, String packageName, String rootElement, Collection<? extends FieldDefinition> fields) {

		requireNonBlank(
				"Invalid XML model configuration",
				className,
				packageName,
				rootElement
		);

		requireNonEmpty(
				fields,
				"XML model must contain at least one field"
		);
	}

	private void createDirectory(String dirPath) throws IOException {
		File dir = new File(dirPath);
		if (!dir.exists() && !dir.mkdirs()) {
			throw new IOException("Failed to create directory: " + dirPath);
		}
	}

	private String generateSource(
			String packageName,
			String className,
			String rootElement,
			List<? extends FieldDefinition> fields
	) {
		StringBuilder sb = new StringBuilder();

		sb.append("package ").append(packageName).append(";\n\n");
		sb.append("import jakarta.xml.bind.annotation.*;\n\n");
		sb.append("@XmlRootElement(name = \"").append(rootElement).append("\")\n");
		sb.append("@XmlAccessorType(XmlAccessType.FIELD)\n");
		sb.append("public class ").append(className).append(" {\n\n");
		sb.append("    public ").append(className).append("() {}\n\n");

		for (FieldDefinition field : fields) {
			sb.append("    @XmlElement(name = \"").append(field.getName()).append("\")\n");
			sb.append("    private ").append(field.getType()).append(" ").append(field.getName()).append(";\n\n");
		}

		for (FieldDefinition field : fields) {
			String name = field.getName();
			String camel = Character.toUpperCase(name.charAt(0)) + name.substring(1);

			sb.append("    public ").append(field.getType()).append(" get").append(camel).append("() {\n");
			sb.append("        return ").append(name).append(";\n");
			sb.append("    }\n\n");

			sb.append("    public void set").append(camel).append("(")
					.append(field.getType()).append(" ").append(name).append(") {\n");
			sb.append("        this.").append(name).append(" = ").append(name).append(";\n");
			sb.append("    }\n\n");
		}

		sb.append("}\n");
		return sb.toString();
	}

	private void writeFile(String dirPath, String className, String content) {
		try (FileWriter fw = new FileWriter(dirPath + "/" + className + ".java")) {
			fw.write(content);
		} catch (IOException e) {
			logger.error("Failed to write model class file: {}", e.getMessage());
			throw new ModelGenerationException("Failed to write model class file");
		}
	}
}
