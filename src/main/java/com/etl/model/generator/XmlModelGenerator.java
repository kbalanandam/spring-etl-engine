package com.etl.model.generator;

import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.etl.config.ModelPathConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.XmlTargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.enums.ModelType;
import com.etl.model.exception.InvalidModelConfigException;
import com.etl.model.exception.ModelGenerationException;
import com.etl.model.generator.support.GeneratedSourcePathResolver;
import com.etl.model.generator.support.JavaTypeNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	private final ModelPathConfig modelPathConfig;

	public XmlModelGenerator(ModelPathConfig modelPathConfig) {
		this.modelPathConfig = modelPathConfig;
	}

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
        String wrapperClassName;
        String recordClassName;
        String packageName;
        String rootElement;
        String recordElement;
        List<? extends FieldDefinition> fields;
				ModelType modelType;

        // Always require both rootElement and recordElement for any XML type
        if (config.getModelType() == ModelType.TARGET && config instanceof XmlTargetConfig xmlTarget) {
            wrapperClassName = xmlTarget.getRootElement();
            recordClassName = xmlTarget.getRecordElement();
            packageName = xmlTarget.getPackageName();
            rootElement = xmlTarget.getRootElement();
            recordElement = xmlTarget.getRecordElement();
            fields = xmlTarget.getFields();
			modelType = ModelType.TARGET;
        } else if (config.getModelType() == ModelType.SOURCE && config instanceof XmlSourceConfig xmlSource) {
            wrapperClassName = xmlSource.getRootElement();
            recordClassName = xmlSource.getRecordElement();
            packageName = xmlSource.getPackageName();
            rootElement = xmlSource.getRootElement();
            recordElement = xmlSource.getRecordElement();
            fields = xmlSource.getFields();
			modelType = ModelType.SOURCE;
        } else {
            logger.error("XmlModelGenerator supports only XmlSourceConfig and XmlTargetConfig");
            throw new InvalidModelConfigException(
                    "XmlModelGenerator supports only XmlSourceConfig and XmlTargetConfig"
            );
        }

        // Enforce both rootElement and recordElement are present and not equal
        if (rootElement == null || rootElement.isBlank() || recordElement == null || recordElement.isBlank()) {
            logger.error("Both rootElement and recordElement must be provided in the config for XML model generation");
            throw new InvalidModelConfigException("Both rootElement and recordElement must be provided");
        }
        if (rootElement.equals(recordElement)) {
            logger.warn("rootElement and recordElement are the same ({}). This may cause invalid XML structure.", rootElement);
        }

        validate(wrapperClassName, packageName, rootElement, fields);

		Path outputDirectory = GeneratedSourcePathResolver.resolvePackageDirectory(modelPathConfig, modelType, packageName);
		createDirectory(outputDirectory);

        // Always generate record class and wrapper class for any XML type
		String recordSource = generateRecordSource(packageName, recordClassName, recordElement, fields);
		writeFile(outputDirectory, recordClassName, recordSource);

        String wrapperSource = generateWrapperSource(packageName, wrapperClassName, recordClassName, rootElement, recordElement);
		writeFile(outputDirectory, wrapperClassName, wrapperSource);

        logger.info("XML model generated: {}.{} and {}.{} (root: {}, record: {})", packageName, wrapperClassName, packageName, recordClassName, rootElement, recordElement);
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

	private void createDirectory(Path dirPath) throws IOException {
		Files.createDirectories(dirPath);
	}

	private String generateRecordSource(
			String packageName,
			String className,
			String recordElement,
			List<? extends FieldDefinition> fields
	) {
		StringBuilder sb = new StringBuilder();
		sb.append("package ").append(packageName).append(";\n\n");
		sb.append("import jakarta.xml.bind.annotation.*;\n\n");
		sb.append("@XmlRootElement(name = \"").append(recordElement).append("\")\n");
		sb.append("@XmlAccessorType(XmlAccessType.FIELD)\n");
		sb.append("public class ").append(className).append(" {\n\n");
		sb.append("    public ").append(className).append("() {}\n\n");
		for (FieldDefinition field : fields) {
			String javaType = JavaTypeNameResolver.resolveJavaSourceType(field.getType());
			sb.append("    @XmlElement(name = \"").append(field.getName()).append("\")\n");
			sb.append("    private ").append(javaType).append(" ").append(field.getName()).append(";\n\n");
		}
		for (FieldDefinition field : fields) {
			String name = field.getName();
			String camel = Character.toUpperCase(name.charAt(0)) + name.substring(1);
			String javaType = JavaTypeNameResolver.resolveJavaSourceType(field.getType());
			sb.append("    public ").append(javaType).append(" get").append(camel).append("() {\n");
			sb.append("        return ").append(name).append(";\n");
			sb.append("    }\n\n");
			sb.append("    public void set").append(camel).append("(")
					.append(javaType).append(" ").append(name).append(") {\n");
			sb.append("        this.").append(name).append(" = ").append(name).append(";\n");
			sb.append("    }\n\n");
		}
		sb.append("}\n");
		return sb.toString();
	}

	private String generateWrapperSource(
			String packageName,
			String wrapperClassName,
			String recordClassName,
			String rootElement,
			String recordElement
	) {
		StringBuilder sb = new StringBuilder();
		sb.append("package ").append(packageName).append(";\n\n");
		sb.append("import jakarta.xml.bind.annotation.*;\n");
		sb.append("import java.util.List;\n\n");
		sb.append("@XmlRootElement(name = \"").append(rootElement).append("\")\n");
		sb.append("@XmlAccessorType(XmlAccessType.FIELD)\n");
		sb.append("public class ").append(wrapperClassName).append(" {\n\n");
		// Use lowerCamelCase of recordElement for the field name
		String fieldName = Character.toLowerCase(recordElement.charAt(0)) + recordElement.substring(1);
		sb.append("    @XmlElement(name = \"").append(recordElement).append("\")\n");
		sb.append("    private List<").append(recordClassName).append("> ")
		  .append(fieldName).append(";\n\n");
		sb.append("    public ").append(wrapperClassName).append("() {}\n\n");
		// Getter
		sb.append("    public List<").append(recordClassName).append("> get")
		  .append(recordElement).append("() {\n");
		sb.append("        return ").append(fieldName).append(";\n");
		sb.append("    }\n\n");
		// Setter
		sb.append("    public void set").append(recordElement).append("(List<")
		  .append(recordClassName).append("> ")
		  .append(fieldName).append(") {\n");
		sb.append("        this.").append(fieldName).append(" = ")
		  .append(fieldName).append(";\n");
		sb.append("    }\n\n");
		sb.append("}\n");
		return sb.toString();
	}

	private void writeFile(Path outputDirectory, String className, String content) {
		Path javaFile = outputDirectory.resolve(className + ".java");
		try (FileWriter fw = new FileWriter(javaFile.toFile())) {
			fw.write(content);
		} catch (IOException e) {
			logger.error("Failed to write model class file: " + e.getMessage());
			throw new ModelGenerationException("Failed to write model class file: " + className, e);
		}
	}
}
