package com.etl.generation.xml.build;

import com.etl.common.util.StringUtils;
import com.etl.common.util.ValidationUtils;
import com.etl.config.FieldDefinition;
import com.etl.generation.xml.XmlModelGenerationResult;
import com.etl.model.exception.ModelGenerationException;
import com.etl.model.generator.support.JavaTypeNameResolver;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates a flat Java bean for non-XML targets such as CSV using the configured target fields.
 */
public class FlatTargetModelClassGenerator {

    public XmlModelGenerationResult generate(String packageName,
                                             String className,
                                             List<? extends FieldDefinition> fields,
                                             Path generatedSourceRoot) {
        ValidationUtils.requireNonBlank("Invalid flat target generation contract", packageName, className);
        ValidationUtils.requireNonEmpty(fields, "Flat target model must contain at least one field.");

        try {
            Path packageDirectory = generatedSourceRoot.resolve(packageName.replace('.', '/'));
            Files.createDirectories(packageDirectory);

            TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(className)
                    .addModifiers(Modifier.PUBLIC);
            for (FieldDefinition field : fields) {
                addField(typeBuilder, field);
            }

            JavaFile javaFile = JavaFile.builder(packageName, typeBuilder.build()).build();
            Path javaFilePath = packageDirectory.resolve(className + ".java");
            javaFile.writeTo(generatedSourceRoot);

            return new XmlModelGenerationResult(packageName, className, className, List.of(javaFilePath));
        } catch (IOException e) {
            throw new ModelGenerationException("Failed to generate flat target model source for '" + packageName + "." + className + "'.", e);
        }
    }

    private void addField(TypeSpec.Builder typeBuilder, FieldDefinition field) {
        TypeName javaType = JavaTypeNameResolver.resolvePoetType(field.getType());
        String fieldName = field.getName();
        String accessorSuffix = StringUtils.capitalize(fieldName);

        typeBuilder.addField(FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE).build());
        typeBuilder.addMethod(MethodSpec.methodBuilder("get" + accessorSuffix)
                .addModifiers(Modifier.PUBLIC)
                .returns(javaType)
                .addStatement("return this.$N", fieldName)
                .build());
        typeBuilder.addMethod(MethodSpec.methodBuilder("set" + accessorSuffix)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(javaType, fieldName)
                .addStatement("this.$N = $N", fieldName, fieldName)
                .build());
    }
}

