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
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
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
 * Generates simple Java POJO model classes for relational sources and targets.
 *
 * <p>This generator intentionally mirrors the CSV generator contract because the
 * relational phase-1 flow still works with generated POJO models and field-by-field
 * mapping, but does not require XML wrapper classes.</p>
 */
@Profile("dev")
@Component
public class RelationalModelGenerator<T extends ModelConfig> implements ModelGenerator<T> {

    private static final Logger logger = LoggerFactory.getLogger(RelationalModelGenerator.class);
    private static final ModelFormat MODEL_FORMAT = ModelFormat.RELATIONAL;
    private final ModelPathConfig modelPathConfig;

    public RelationalModelGenerator(ModelPathConfig modelPathConfig) {
        this.modelPathConfig = modelPathConfig;
    }

    @Override
    public ModelFormat getType() {
        return MODEL_FORMAT;
    }

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
            logger.info("Generating relational model for source: {}", sourceCfg.getSourceName());
        } else if (object instanceof TargetConfig targetCfg) {
            className = StringUtils.capitalize(targetCfg.getTargetName());
            packageName = targetCfg.getPackageName();
            fields = targetCfg.getFields();
            modelType = ModelType.TARGET;
            logger.info("Generating relational model for target: {}", targetCfg.getTargetName());
        } else {
            throw new InvalidModelConfigException(
                    "RelationalModelGenerator supports only SourceConfig or TargetConfig"
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
            throw new ModelGenerationException("Failed to write relational model class: " + packageName + "." + className, e);
        }

        logger.info("Relational model class generated: {}.{}", packageName, className);
    }

    private void validate(String className,
                          String packageName,
                          List<? extends FieldDefinition> fields) {
        requireNonBlank(
                "Invalid relational model configuration",
                className,
                packageName
        );
        requireNonEmpty(
                fields,
                "Relational model must contain at least one field"
        );
    }

    private void buildFields(TypeSpec.Builder typeBuilder,
                             List<? extends FieldDefinition> fields) {
        requireNonEmpty(
                fields,
                "Relational model must contain at least one field"
        );

        for (FieldDefinition field : fields) {
            TypeName javaType = JavaTypeNameResolver.resolvePoetType(field.getType());
            String fieldName = field.getName();

            typeBuilder.addField(
                    FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE).build()
            );

            typeBuilder.addMethod(
                    MethodSpec.methodBuilder("get" + StringUtils.capitalize(fieldName))
                            .addModifiers(Modifier.PUBLIC)
                            .returns(javaType)
                            .addStatement("return this.$N", fieldName)
                            .build()
            );

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

