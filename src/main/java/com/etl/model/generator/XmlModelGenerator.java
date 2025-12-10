package com.etl.model.generator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.etl.config.ModelConfig;
import com.etl.config.target.TargetConfig;

@Profile("dev")
@Component
public class XmlModelGenerator<T extends ModelConfig> implements ModelGenerator<T> {

	private final String type = "xml";

	/**
	 * Generates a model based on the provided configuration and type.
	 * 
	 * @param config
	 * @param Type
	 * @return
	 * @return
	 */

	@Override
	public String getType() {
		return type;
	}

	@Override
	public void generateModel(Object object) throws Exception {
		Logger logger = LoggerFactory.getLogger(CsvModelGenerator.class);

		String className = null;
		String packageName = null;
		Object config = null;
		List<com.etl.config.target.ColumnConfig> fields = null;

		if (object instanceof TargetConfig) {
			config = object;
			logger.info("Generating model for XML target: " + ((TargetConfig) config).getTargetName());
			className = ((TargetConfig) config).getTargetName();
			packageName = ((TargetConfig) config).getPackageName();
			fields = ((TargetConfig) config).getFields();
		} else {
			throw new IllegalArgumentException("Unsupported object type for XML model generation");
		}

		String dirPath = "src/main/java/" + packageName.replace(".", "/");
		new File(dirPath).mkdirs();

		StringBuilder sb = new StringBuilder();
		sb.append("package ").append(packageName).append(";\n\n");
		sb.append("import jakarta.xml.bind.annotation.*;\n\n");
		sb.append("@XmlRootElement(name = \"").append(className.toLowerCase()).append("\")\n");
		sb.append("@XmlAccessorType(XmlAccessType.FIELD)\n");
		sb.append("public class ").append(className).append(" {\n\n");

		// Public no-argument constructor (CRITICAL for JAXB)
		sb.append("    public ").append(className).append("() {\n");
		sb.append("        // Default constructor required by JAXB\n");
		sb.append("    }\n\n");

		for (com.etl.config.target.ColumnConfig field : fields) {
			sb.append("    @XmlElement(name = \"").append(field.getName()).append("\")\n");
			sb.append("    private ").append(field.getType()).append(" ").append(field.getName()).append(";\n\n");
		}

		for (com.etl.config.target.ColumnConfig field : fields) {
			String camel = field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
			sb.append("    public ").append(field.getType()).append(" get").append(camel).append("() {\n");
			sb.append("        return ").append(field.getName()).append(";\n    }\n\n");
			sb.append("    public void set").append(camel).append("(").append(field.getType()).append(" ")
					.append(field.getName()).append(") {\n");
			sb.append("        this.").append(field.getName()).append(" = ").append(field.getName())
					.append(";\n    }\n\n");
		}

		sb.append("}\n");

		FileWriter fw;
		try {
			fw = new FileWriter(dirPath + "/" + className + ".java");
			fw.write(sb.toString());
			fw.close();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write target model class file", e);
		}

		logger.info("✅ XML Model class generated: " + className);
	}
}