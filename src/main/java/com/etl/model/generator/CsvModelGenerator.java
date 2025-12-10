package com.etl.model.generator;

import java.nio.file.Paths;

import javax.lang.model.element.Modifier;

import com.etl.common.util.TypeConversionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.etl.common.util.StringUtils;
import com.etl.config.ModelConfig;
import com.etl.config.source.ColumnConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

@Profile("dev")
@Component
public class CsvModelGenerator<T extends ModelConfig> implements ModelGenerator<T> {

	private final String type;

    public CsvModelGenerator() {
        type = "csv";
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
		Object config = null;

		if (object instanceof SourceConfig) {
			config = object;
			logger.info("Generating model for {} source: {} ", ((SourceConfig) config).getType(),
					((SourceConfig) config).getSourceName());
			className = getClassName(((SourceConfig) config).getSourceName());
			packageName = ((SourceConfig) config).getPackageName();

		} else if (object instanceof TargetConfig) {
			config = object;
			logger.info("Generating model for {} target: {} ", ((TargetConfig) config).getType(),
					((TargetConfig) config).getTargetName());
			className = getClassName(((TargetConfig) config).getTargetName());
			packageName = ((TargetConfig) config).getPackageName();
		}


        TypeSpec.Builder classBuilder = null;
        if (className != null) {
            classBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC);
        }

        if (config instanceof SourceConfig) {
            for (ColumnConfig column : ((SourceConfig) config).getColumns()) {
                String type = TypeConversionUtils.mapToJavaType(column.getType());
                ClassName classType = ClassName.bestGuess(type);

                // private field
                if (classBuilder != null) {
                    classBuilder.addField(FieldSpec.builder(classType, column.getName(), Modifier.PRIVATE).build());
                }

                // getter
                assert classBuilder != null;
                classBuilder.addMethod(MethodSpec.methodBuilder("get" + StringUtils.capitalize(column.getName()))
                        .addModifiers(Modifier.PUBLIC).returns(classType).addStatement("return this.$N", column.getName())
                        .build());

                // setter
                classBuilder.addMethod(MethodSpec.methodBuilder("set" + StringUtils.capitalize(column.getName()))
                        .addModifiers(Modifier.PUBLIC).addParameter(classType, column.getName())
                        .addStatement("this.$N = $N", column.getName(), column.getName()).build());
            }
        }

        JavaFile javaFile = null;
        if (packageName != null) {
            assert classBuilder != null;
            javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        }
        if (javaFile != null) {
            javaFile.writeTo(Paths.get("src/main/java"));
        }
    }

	private static String getClassName(String className) {
		return StringUtils.capitalize(className);
	}

}
