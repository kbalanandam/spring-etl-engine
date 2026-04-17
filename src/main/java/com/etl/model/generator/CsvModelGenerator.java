package com.etl.model.generator;

import com.etl.common.util.StringUtils;
import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.etl.config.ModelPathConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.enums.ModelType;
import com.etl.model.exception.InvalidModelConfigException;
import com.etl.model.exception.ModelGenerationException;
import com.etl.model.generator.support.GeneratedSourcePathResolver;
import com.etl.model.generator.support.JavaTypeNameResolver;
import com.squareup.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Path;
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
    private final ModelPathConfig modelPathConfig;

    public CsvModelGenerator(ModelPathConfig modelPathConfig) {
        this.modelPathConfig = modelPathConfig;
    }

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
	public void generateModel(T object) {

        String className;
        String packageName;
        List<? extends FieldDefinition> fields;
        ModelType modelType;

        if (object instanceof SourceConfig sourceCfg) {

            className = StringUtils.capitalize(sourceCfg.getSourceName());
            packageName = sourceCfg.getPackageName();
            fields = sourceCfg.getFields();
            modelType = ModelType.SOURCE;

            logger.info("Generating CSV model for source: {}", sourceCfg.getSourceName());

        } else if (object instanceof TargetConfig targetCfg) {

            className = StringUtils.capitalize(targetCfg.getTargetName());
            packageName = targetCfg.getPackageName();
            fields = targetCfg.getFields();
            modelType = ModelType.TARGET;

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
              Path outputDirectory = GeneratedSourcePathResolver.resolveSourceRoot(modelPathConfig, modelType, packageName);
            try {
              javaFile.writeTo(outputDirectory);
            } catch (IOException e) {
              throw new ModelGenerationException("Failed to write CSV model class: " + packageName + "." + className, e);
            }

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

        requireNonEmpty(
            fields,
            "CSV model must contain at least one field"
        );

        for (FieldDefinition field : fields) {
          TypeName javaType = JavaTypeNameResolver.resolvePoetType(field.getType());
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
