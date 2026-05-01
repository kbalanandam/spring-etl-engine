package com.etl.generation.xml;

import com.etl.common.util.StringUtils;
import com.etl.common.util.ValidationUtils;
import com.etl.model.exception.ModelGenerationException;
import com.etl.model.generator.support.JavaTypeNameResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
/**
 * Generates JAXB-ready Java model classes from standalone XML model definitions.
 *
 * <p>This utility is intentionally structural only. It generates source or target model
 * classes from config, including one level or many levels of nested object structure,
 * but it does not implement flattening or business mapping behavior.</p>
 */
public class XmlStructureClassGenerator {
    private static final Pattern NON_IDENTIFIER_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    public XmlModelGenerationResult generate(XmlModelDefinition definition, Path generatedSourceRoot) {
        validate(definition);
        try {
            Path packageDirectory = generatedSourceRoot.resolve(definition.getPackageName().replace('.', '/'));
            Files.createDirectories(packageDirectory);
            Path recordFile = packageDirectory.resolve(definition.getRecordElement() + ".java");
            Files.writeString(recordFile, buildRecordSource(definition));
            Path wrapperFile = packageDirectory.resolve(definition.getRootElement() + ".java");
            Files.writeString(wrapperFile, buildWrapperSource(definition));
            return new XmlModelGenerationResult(
                    definition.getPackageName(),
                    definition.getRootElement(),
                    definition.getRecordElement(),
                    List.of(recordFile, wrapperFile)
            );
        } catch (IOException e) {
            throw new ModelGenerationException("Failed to generate XML model sources for package '" + definition.getPackageName() + "'.", e);
        }
    }
    private void validate(XmlModelDefinition definition) {
        Objects.requireNonNull(definition, "XML model definition must not be null.");
        ValidationUtils.requireNonBlank(
                "Invalid XML model definition",
                definition.getPackageName(),
                definition.getRootElement(),
                definition.getRecordElement()
        );
        ValidationUtils.requireNonEmpty(definition.getFields(), "XML model definition must contain at least one field.");
    }
    private String buildRecordSource(XmlModelDefinition definition) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(definition.getPackageName()).append(";\n\n");
        sb.append("import jakarta.xml.bind.annotation.*;\n");
        if (containsCollection(definition.getFields())) {
            sb.append("import java.util.List;\n");
        }
        sb.append("\n@XmlRootElement(name = \"").append(definition.getRecordElement()).append("\")\n");
        sb.append("@XmlAccessorType(XmlAccessType.FIELD)\n");
        sb.append("public class ").append(definition.getRecordElement()).append(" {\n\n");
        sb.append("    public ").append(definition.getRecordElement()).append("() {}\n\n");
        appendFieldsAndAccessors(sb, definition.getFields(), "    ");
        appendNestedTypes(sb, definition.getFields(), "    ");
        sb.append("}\n");
        return sb.toString();
    }
    private String buildWrapperSource(XmlModelDefinition definition) {
        StringBuilder sb = new StringBuilder();
        String wrapperFieldName = javaFieldName(definition.getRecordElement());
        sb.append("package ").append(definition.getPackageName()).append(";\n\n");
        sb.append("import jakarta.xml.bind.annotation.*;\n");
        sb.append("import java.util.List;\n\n");
        sb.append("@XmlRootElement(name = \"").append(definition.getRootElement()).append("\")\n");
        sb.append("@XmlAccessorType(XmlAccessType.FIELD)\n");
        sb.append("public class ").append(definition.getRootElement()).append(" {\n\n");
        sb.append("    @XmlElement(name = \"").append(definition.getRecordElement()).append("\")\n");
        sb.append("    private List<").append(definition.getRecordElement()).append("> ").append(wrapperFieldName).append(";\n\n");
        sb.append("    public ").append(definition.getRootElement()).append("() {}\n\n");
        sb.append("    public List<").append(definition.getRecordElement()).append("> get")
                .append(definition.getRecordElement()).append("() {\n")
                .append("        return ").append(wrapperFieldName).append(";\n")
                .append("    }\n\n");
        sb.append("    public void set").append(definition.getRecordElement()).append("(List<")
                .append(definition.getRecordElement()).append("> ").append(wrapperFieldName).append(") {\n")
                .append("        this.").append(wrapperFieldName).append(" = ").append(wrapperFieldName).append(";\n")
                .append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }
    private void appendFieldsAndAccessors(StringBuilder sb, List<XmlFieldDefinition> fields, String indent) {
        List<ResolvedField> resolvedFields = fields.stream()
                .map(this::resolveField)
                .toList();
        for (ResolvedField field : resolvedFields) {
            sb.append(indent).append("@XmlElement(name = \"").append(field.xmlName()).append("\")\n");
            sb.append(indent).append("private ").append(field.javaType()).append(' ').append(field.javaName()).append(";\n\n");
        }
        for (ResolvedField field : resolvedFields) {
            sb.append(indent).append("public ").append(field.javaType()).append(" get").append(field.accessorSuffix()).append("() {\n");
            sb.append(indent).append("    return ").append(field.javaName()).append(";\n");
            sb.append(indent).append("}\n\n");
            sb.append(indent).append("public void set").append(field.accessorSuffix()).append('(').append(field.javaType()).append(' ').append(field.javaName()).append(") {\n");
            sb.append(indent).append("    this.").append(field.javaName()).append(" = ").append(field.javaName()).append(";\n");
            sb.append(indent).append("}\n\n");
        }
    }
    private void appendNestedTypes(StringBuilder sb, List<XmlFieldDefinition> fields, String indent) {
        for (XmlFieldDefinition field : fields) {
            if (!field.isNested()) {
                continue;
            }
            String className = nestedClassName(field);
            sb.append(indent).append("@XmlAccessorType(XmlAccessType.FIELD)\n");
            sb.append(indent).append("public static class ").append(className).append(" {\n\n");
            sb.append(indent).append("    public ").append(className).append("() {}\n\n");
            appendFieldsAndAccessors(sb, field.getFields(), indent + "    ");
            appendNestedTypes(sb, field.getFields(), indent + "    ");
            sb.append(indent).append("}\n\n");
        }
    }
    private boolean containsCollection(List<XmlFieldDefinition> fields) {
        List<XmlFieldDefinition> allFields = new ArrayList<>(fields);
        while (!allFields.isEmpty()) {
            XmlFieldDefinition field = allFields.remove(0);
            if (field.isCollection()) {
                return true;
            }
            if (field.getFields() != null) {
                allFields.addAll(field.getFields());
            }
        }
        return false;
    }
    private String resolveJavaType(XmlFieldDefinition field) {
        if (field.isNested()) {
            String nestedType = nestedClassName(field);
            return field.isCollection() ? "List<" + nestedType + ">" : nestedType;
        }
        String baseType = (field.getType() == null || field.getType().isBlank())
                ? "String"
                : JavaTypeNameResolver.resolveJavaSourceType(field.getType().trim());
        return field.isCollection() ? "List<" + baseType + ">" : baseType;
    }
    private String nestedClassName(XmlFieldDefinition field) {
        if (field.getClassName() != null && !field.getClassName().isBlank()) {
            return sanitizeJavaIdentifier(field.getClassName(), true);
        }
        return sanitizeJavaIdentifier(field.getName(), true);
    }
    private String javaFieldName(String xmlName) {
        String sanitized = sanitizeJavaIdentifier(xmlName, false);
        if (sanitized.isBlank()) {
            return "value";
        }
        return Character.toLowerCase(sanitized.charAt(0)) + sanitized.substring(1);
    }

    private ResolvedField resolveField(XmlFieldDefinition field) {
        String javaName = javaFieldName(field.getName());
        return new ResolvedField(
                field.getName(),
                javaName,
                resolveJavaType(field),
                javaName.isBlank() ? "Value" : StringUtils.capitalize(javaName)
        );
    }

    private String sanitizeJavaIdentifier(String value, boolean upperCamel) {
        String cleaned = NON_IDENTIFIER_CHARS.matcher(value == null ? "" : value.trim()).replaceAll(" ");
        String[] parts = cleaned.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String normalized = part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1);
            result.append(normalized);
        }
        if (result.isEmpty()) {
            return "Value";
        }
        if (!upperCamel) {
            result.setCharAt(0, Character.toLowerCase(result.charAt(0)));
        }
        if (Character.isDigit(result.charAt(0))) {
            result.insert(0, upperCamel ? 'X' : 'x');
        }
        return result.toString();
    }

    private record ResolvedField(
            String xmlName,
            String javaName,
            String javaType,
            String accessorSuffix
    ) {
    }
}
