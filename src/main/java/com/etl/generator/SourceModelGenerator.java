package com.etl.generator;

import java.io.File;
import java.nio.file.Paths;

import javax.lang.model.element.Modifier;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.etl.config.source.ColumnConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

@Component
@Profile("dev")
public class SourceModelGenerator {
	
	private boolean alreadyGenerated = false;

	@EventListener(ApplicationReadyEvent.class)
	public void generate(ApplicationReadyEvent event) throws Exception {
		// Step 1: Read YAML
		
		if (alreadyGenerated) return;
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		File yamlFile = new File("src/main/resources/source-config.yaml"); // Adjust path
		SourceWrapper wrapper = mapper.readValue(yamlFile, SourceWrapper.class);
		
	    // DEBUG: Print loaded sources
	    System.out.println("Parsed sources: " + wrapper.getSources());

		for (SourceConfig source : wrapper.getSources()) {
			System.out.println("Generating model for: " + source.getType());
			generateModelClass(source);
			

		}
		alreadyGenerated = true;
        System.out.println(">>> Model generation completed.");
	}

	private  void generateModelClass(SourceConfig config) throws Exception {
		String className = getClassName(config.getFilePath());

		TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC);

		for (ColumnConfig column : config.getColumns()) {
			String type = mapToJavaType(column.getType());
			ClassName classType = ClassName.bestGuess(type);

			// private field
			classBuilder.addField(FieldSpec.builder(classType, column.getName(), Modifier.PRIVATE).build());

			// getter
			classBuilder.addMethod(
					MethodSpec.methodBuilder("get" + capitalize(column.getName())).addModifiers(Modifier.PUBLIC)
							.returns(classType).addStatement("return this.$N", column.getName()).build());

			// setter
			classBuilder.addMethod(MethodSpec.methodBuilder("set" + capitalize(column.getName()))
					.addModifiers(Modifier.PUBLIC).addParameter(classType, column.getName())
					.addStatement("this.$N = $N", column.getName(), column.getName()).build());
		}

		JavaFile javaFile = JavaFile.builder("com.etl.models.source", classBuilder.build()).build();
		javaFile.writeTo(Paths.get("src/main/java"));
	}

	private static String capitalize(String name) {
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}

	private static String mapToJavaType(String type) {
		return switch (type.toLowerCase()) {
		case "int", "integer" -> "Integer";
		case "long" -> "Long";
		case "double" -> "Double";
		case "float" -> "Float";
		case "boolean" -> "Boolean";
		case "string" -> "String";
		default -> "String"; // fallback
		};
	}

	private static String getClassName(String filePath) {
		String fileName = new File(filePath).getName();
		return capitalize(fileName.substring(0, fileName.lastIndexOf('.')));
	}
}
