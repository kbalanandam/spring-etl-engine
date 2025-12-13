package com.etl.model.generator;

import java.nio.file.Paths;
import java.util.List;

import javax.lang.model.element.Modifier;

import com.etl.common.util.TypeConversionUtils;
import com.etl.common.util.StringUtils;
import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("dev")
@Component
public class CsvModelGenerator<T extends ModelConfig> implements ModelGenerator<T> {

    private final String type;

    public CsvModelGenerator() {
        this.type = "csv";
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void generateModel(Object object) throws Exception {

        final Logger logger = LoggerFactory.getLogger(CsvModelGenerator.class);

        String className = null;
        String packageName = null;
        List<? extends FieldDefinition> fields = null;

        if (object instanceof SourceConfig sourceCfg) {
            className = StringUtils.capitalize(sourceCfg.getSourceName());
            packageName = sourceCfg.getPackageName();
            fields = sourceCfg.getFields();
            logger.info("Generating model for CSV source: {}", sourceCfg.getSourceName());
        } else if (object instanceof TargetConfig targetCfg) {
            className = StringUtils.capitalize(targetCfg.getTargetName());
            packageName = targetCfg.getPackageName();
            fields = targetCfg.getFields();
            logger.info("Generating model for CSV target: {}", targetCfg.getTargetName());
        }

        if (className != null && packageName != null && fields != null) {
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                    .addModifiers(Modifier.PUBLIC);

            buildFields(classBuilder, fields);

            JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
            javaFile.writeTo(Paths.get("src/main/java"));
        }
    }

    /**
     * Generic column/field builder that accepts any FieldDefinition subtype
     */
    private void buildFields(TypeSpec.Builder typeBuilder, List<? extends FieldDefinition> fields) {
        for (FieldDefinition col : fields) {
            String javaTypeName = TypeConversionUtils.mapToJavaType(col.getType());
            ClassName javaType = ClassName.bestGuess(javaTypeName);
            String fieldName = col.getName();

            // Add private field
            typeBuilder.addField(FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE).build());

            // Getter
            typeBuilder.addMethod(MethodSpec.methodBuilder("get" + StringUtils.capitalize(fieldName))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(javaType)
                    .addStatement("return this.$N", fieldName)
                    .build());

            // Setter
            typeBuilder.addMethod(MethodSpec.methodBuilder("set" + StringUtils.capitalize(fieldName))
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(javaType, fieldName)
                    .addStatement("this.$N = $N", fieldName, fieldName)
                    .build());
        }
    }
}
