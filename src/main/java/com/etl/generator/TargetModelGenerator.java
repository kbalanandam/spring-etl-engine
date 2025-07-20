package com.etl.generator;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetConfig.Field;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@Component
public class TargetModelGenerator {

	@EventListener(ApplicationReadyEvent.class)
	public void generate(ApplicationReadyEvent event) throws Exception {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		File yamlFile = new ClassPathResource("target-config.yaml").getFile();

		TargetConfig config = mapper.readValue(yamlFile, TargetConfig.class);

		if (!"xml".equalsIgnoreCase(config.getTarget().getType()))
			return;

		String pkg = config.getTarget().getPackageName();
		String className = config.getTarget().getClassName();
		List<Field> fields = config.getTarget().getFields();

		String dirPath = "src/main/java/" + pkg.replace(".", "/");
		new File(dirPath).mkdirs();

		StringBuilder sb = new StringBuilder();
		sb.append("package ").append(pkg).append(";\n\n");
		sb.append("import jakarta.xml.bind.annotation.*;\n\n");
		sb.append("@XmlRootElement(name = \"").append(className.toLowerCase()).append("\")\n");
		sb.append("@XmlAccessorType(XmlAccessType.FIELD)\n");
		sb.append("public class ").append(className).append(" {\n\n");

		for (Field field : fields) {
			sb.append("    @XmlElement(name = \"").append(field.getName()).append("\")\n");
			sb.append("    private ").append(field.getType()).append(" ").append(field.getName()).append(";\n\n");
		}

		for (Field field : fields) {
			String camel = field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
			sb.append("    public ").append(field.getType()).append(" get").append(camel).append("() {\n");
			sb.append("        return ").append(field.getName()).append(";\n    }\n\n");
			sb.append("    public void set").append(camel).append("(").append(field.getType()).append(" ")
					.append(field.getName()).append(") {\n");
			sb.append("        this.").append(field.getName()).append(" = ").append(field.getName())
					.append(";\n    }\n\n");
		}

		sb.append("}\n");

		FileWriter fw = new FileWriter(dirPath + "/" + className + ".java");
		fw.write(sb.toString());
		fw.close();

		System.out.println("✅ XML Model class generated: " + className);
	}
}