package com.etl.common.util;

import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.XmlTargetConfig;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Resolves runtime classes and wrapper metadata for dynamically generated models.
 * <p>
 * This centralizes the naming contract between model generators and downstream
 * batch components so XML wrapper/record handling is defined in one place.
 * </p>
 */
public final class GeneratedModelClassResolver {

    private GeneratedModelClassResolver() {
    }

    public static String resolveSourceClassName(SourceConfig sourceConfig) {
		if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig) {
			return xmlSourceConfig.getPackageName() + "." + xmlSourceConfig.getRecordElement();
		}
        return sourceConfig.getPackageName() + "." + sourceConfig.getSourceName();
    }

    public static ResolvedModelMetadata resolveMetadata(SourceConfig sourceConfig, TargetConfig targetConfig) {
        String sourceClassName = resolveSourceClassName(sourceConfig);
        String targetWriteClassName = resolveTargetWriteClassName(targetConfig);
        String targetProcessingClassName = resolveTargetProcessingClassName(targetConfig);

        if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
            return new ResolvedModelMetadata(
                    sourceClassName,
                    targetProcessingClassName,
                    targetWriteClassName,
                    true,
                    resolveXmlWrapperFieldName(xmlTargetConfig)
            );
        }

        return new ResolvedModelMetadata(
                sourceClassName,
                targetProcessingClassName,
                targetWriteClassName,
                false,
                null
        );
    }

    public static String resolveTargetWriteClassName(TargetConfig targetConfig) {
        if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
            return xmlTargetConfig.getPackageName() + "." + xmlTargetConfig.getRootElement();
        }
        return targetConfig.getPackageName() + "." + targetConfig.getTargetName();
    }

    public static String resolveTargetProcessingClassName(TargetConfig targetConfig) {
        if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
            return xmlTargetConfig.getPackageName() + "." + xmlTargetConfig.getRecordElement();
        }
        return targetConfig.getPackageName() + "." + targetConfig.getTargetName();
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveSourceClass(SourceConfig sourceConfig) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveSourceClassName(sourceConfig));
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveSourceClass(ResolvedModelMetadata metadata) throws ClassNotFoundException {
        return (Class<T>) Class.forName(metadata.getSourceClassName());
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveTargetWriteClass(TargetConfig targetConfig) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveTargetWriteClassName(targetConfig));
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveTargetWriteClass(ResolvedModelMetadata metadata) throws ClassNotFoundException {
        return (Class<T>) Class.forName(metadata.getTargetWriteClassName());
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveTargetProcessingClass(TargetConfig targetConfig) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveTargetProcessingClassName(targetConfig));
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveTargetProcessingClass(ResolvedModelMetadata metadata) throws ClassNotFoundException {
        return (Class<T>) Class.forName(metadata.getTargetProcessingClassName());
    }

    public static Object createXmlWrapper(XmlTargetConfig targetConfig, List<?> records) throws ReflectiveOperationException {
        Class<?> wrapperClass = resolveTargetWriteClass(targetConfig);
        Object wrapper = wrapperClass.getDeclaredConstructor().newInstance();
        Field field = wrapperClass.getDeclaredField(resolveXmlWrapperFieldName(targetConfig));
        field.setAccessible(true);
        field.set(wrapper, records);
        return wrapper;
    }

    public static Object createWrapper(ResolvedModelMetadata metadata, List<?> records) throws ReflectiveOperationException {
        Class<?> wrapperClass = resolveTargetWriteClass(metadata);
        Object wrapper = wrapperClass.getDeclaredConstructor().newInstance();
        Field field = wrapperClass.getDeclaredField(metadata.getWrapperFieldName());
        field.setAccessible(true);
        field.set(wrapper, records);
        return wrapper;
    }

    public static String resolveXmlWrapperFieldName(XmlTargetConfig targetConfig) {
        String recordElement = targetConfig.getRecordElement();
        return Character.toLowerCase(recordElement.charAt(0)) + recordElement.substring(1);
    }
}

