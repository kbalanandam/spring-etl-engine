package com.etl.model.generator.support;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.time.LocalDate;

/**
 * Resolves configured field type names to Java source and JavaPoet types.
 */
public final class JavaTypeNameResolver {

	private JavaTypeNameResolver() {
	}

	public static TypeName resolvePoetType(String configuredType) {
		return switch (normalize(configuredType)) {
			case "int", "integer" -> ClassName.get(Integer.class);
			case "long" -> ClassName.get(Long.class);
			case "double" -> ClassName.get(Double.class);
			case "float" -> ClassName.get(Float.class);
			case "boolean" -> ClassName.get(Boolean.class);
			case "string" -> ClassName.get(String.class);
			case "localdate" -> ClassName.get(LocalDate.class);
			default -> ClassName.bestGuess(resolveJavaSourceType(configuredType));
		};
	}

	public static String resolveJavaSourceType(String configuredType) {
		return switch (normalize(configuredType)) {
			case "int", "integer" -> "Integer";
			case "long" -> "Long";
			case "double" -> "Double";
			case "float" -> "Float";
			case "boolean" -> "Boolean";
			case "string" -> "String";
			case "localdate" -> "java.time.LocalDate";
			default -> configuredType;
		};
	}

	private static String normalize(String configuredType) {
		return configuredType == null ? "" : configuredType.trim().toLowerCase();
	}
}
