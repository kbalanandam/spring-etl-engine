package com.etl.model.generator;

import com.etl.common.util.StringUtils;
import com.etl.common.util.TypeConversionUtils;
import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.model.exception.InvalidModelConfigException;
import com.squareup.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.lang.model.element.Modifier;
import java.nio.file.Paths;
import java.util.List;

import static com.etl.common.util.ValidationUtils.requireNonBlank;
import static com.etl.common.util.ValidationUtils.requireNonEmpty;

/**
 * Generates Java POJO model classes for CSV sources and targets.
 *
 * <p>
 * Supports {@link SourceConfig} and {@link TargetConfig}.
 * Generates simple Java classes with private fields, getters, and setters
 * based on {@link FieldDefinition}.
 * </p>
 */
@Profile("dev")
@Component
public class CsvModelGenerator<T extends ModelConfig> implements ModelGenerator<T> {

    private static final Logger logger = LoggerFactory.getLogger(CsvModelGenerator.class);
    private static final ModelFormat MODEL_FORMAT = ModelFormat.CSV;

    @Override
    public ModelFormat getType() {
        return MODEL_FORMAT;
    }

    /**
     * Generates a Java model class for CSV source or target configuration.
     *
     * @param object configuration object
     */
    @Override
    public void generateModel(T object) throws Exception {

        String className;
        String packageName;
        List<? extends FieldDefinition> fields;

        if (object instanceof SourceConfig sourceCfg) {

            className = StringUtils.capitalize(sourceCfg.getSourceName());
            packageName = sourceCfg.getPackageName();
            fields = sourceCfg.getFields();

            logger.info("Generating CSV model for source: {}", sourceCfg.getSourceName());

        } else if (object instanceof TargetConfig targetCfg) {

            className = StringUtils.capitalize(targetCfg.getTargetName());
            packageName = targetCfg.getPackageName();
            fields = targetCfg.getFields();

            logger.info("Generating CSV model for target: {}", targetCfg.getTargetName());

        } else {
            throw new InvalidModelConfigException(
                    "CsvModelGenerator supports only SourceConfig or TargetConfig"
            );
        }

        validate(className, packageName, fields);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC);

        buildFields(classBuilder, fields);

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        javaFile.writeTo(Paths.get("src/main/java"));

        logger.info("CSV model class generated: {}.{}", packageName, className);
    }

    /* -------------------- helpers -------------------- */

    private void validate(String className,
                          String packageName,
                          List<? extends FieldDefinition> fields) {

        requireNonBlank(
                "Invalid CSV model configuration",
                className,
                packageName
        );
        requireNonEmpty(
                fields,
                "CSV model must contain at least one field"
        );
    }

    /**
     * Builds private fields with getters and setters.
     */
    private void buildFields(TypeSpec.Builder typeBuilder,
                             List<? extends FieldDefinition> fields) {

        for (FieldDefinition field : fields) {

            requireNonEmpty(
                    fields,
                    "CSV model must contain at least one field"
            );

            String javaTypeName = TypeConversionUtils.mapToJavaType(field.getType());
            ClassName javaType = ClassName.bestGuess(javaTypeName);
            String fieldName = field.getName();

            // private field
            typeBuilder.addField(
                    FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE).build()
            );

            // getter
            typeBuilder.addMethod(
                    MethodSpec.methodBuilder("get" + StringUtils.capitalize(fieldName))
                            .addModifiers(Modifier.PUBLIC)
                            .returns(javaType)
                            .addStatement("return this.$N", fieldName)
                            .build()
            );

            // setter
            typeBuilder.addMethod(
                    MethodSpec.methodBuilder("set" + StringUtils.capitalize(fieldName))
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(javaType, fieldName)
                            .addStatement("this.$N = $N", fieldName, fieldName)
                            .build()
            );
        }
    }
}
