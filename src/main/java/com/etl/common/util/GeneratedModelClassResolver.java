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

  private static final String EXPLICIT_JOB_PACKAGE_HINT = " In explicit job mode this package should be derived during ConfigLoader package defaulting before model resolution.";

    private GeneratedModelClassResolver() {
    }

    public static String resolveSourceClassName(SourceConfig sourceConfig) {
		if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig) {
      return qualifyClassName(
          validatedPackageName(xmlSourceConfig.getPackageName(), "packageName", "XML source", xmlSourceConfig.getSourceName()),
          GeneratedModelNamingPolicy.resolveSourceSimpleClassName(xmlSourceConfig)
      );
		}
    return qualifyClassName(
        validatedPackageName(sourceConfig.getPackageName(), "packageName", "source", sourceConfig.getSourceName()),
        GeneratedModelNamingPolicy.resolveSourceSimpleClassName(sourceConfig)
    );
    }

    public static String resolveSourceRootClassName(SourceConfig sourceConfig) {
        if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig) {
      return qualifyClassName(
          validatedPackageName(xmlSourceConfig.getPackageName(), "packageName", "XML source", xmlSourceConfig.getSourceName()),
          GeneratedModelNamingPolicy.resolveSourceRootSimpleClassName(xmlSourceConfig)
      );
        }
        return resolveSourceClassName(sourceConfig);
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
      return qualifyClassName(
          validatedPackageName(xmlTargetConfig.getPackageName(), "packageName", "XML target", xmlTargetConfig.getTargetName()),
          GeneratedModelNamingPolicy.resolveTargetWriteSimpleClassName(xmlTargetConfig)
      );
        }
    return qualifyClassName(
        validatedPackageName(targetConfig.getPackageName(), "packageName", "target", targetConfig.getTargetName()),
        GeneratedModelNamingPolicy.resolveTargetWriteSimpleClassName(targetConfig)
    );
    }

    public static String resolveTargetProcessingClassName(TargetConfig targetConfig) {
        if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
      return qualifyClassName(
          validatedPackageName(xmlTargetConfig.getPackageName(), "packageName", "XML target", xmlTargetConfig.getTargetName()),
          GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(xmlTargetConfig)
      );
        }
    return qualifyClassName(
        validatedPackageName(targetConfig.getPackageName(), "packageName", "target", targetConfig.getTargetName()),
        GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(targetConfig)
    );
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveSourceClass(SourceConfig sourceConfig) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveSourceClassName(sourceConfig));
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveSourceRootClass(SourceConfig sourceConfig) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveSourceRootClassName(sourceConfig));
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveSourceRootClass(SourceConfig sourceConfig, ClassLoader classLoader) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveSourceRootClassName(sourceConfig), true, classLoader);
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
		String packageName = validatedPackageName(targetConfig.getPackageName(), "packageName", "XML target", targetConfig.getTargetName());
        return GeneratedModelNamingPolicy.resolveWrapperFieldName(
                packageName,
                requireNonBlank(targetConfig.getRecordElement(), "recordElement", "XML target", targetConfig.getTargetName()),
                GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(targetConfig)
        );
    }

    public static void requireSourceModelClassesAvailable(SourceConfig sourceConfig) {
        if (!(sourceConfig instanceof XmlSourceConfig xmlSourceConfig)) {
            requireClassPresent(
                    resolveSourceClassName(sourceConfig),
                    "Source model class",
                    sourceConfig.getSourceName()
            );
            return;
        }

        if (requiresSourceRootClass(xmlSourceConfig)) {
            requireClassPresent(
                    resolveSourceRootClassName(xmlSourceConfig),
                    "XML source root class",
                    xmlSourceConfig.getSourceName()
            );
        }

        requireClassPresent(
                resolveSourceClassName(xmlSourceConfig),
                "XML source record class",
                xmlSourceConfig.getSourceName()
        );
    }

    public static void requireTargetModelClassesAvailable(TargetConfig targetConfig) {

        if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
            requireClassPresent(
                    resolveTargetWriteClassName(xmlTargetConfig),
                    "XML target root class",
                    xmlTargetConfig.getTargetName()
            );
            requireClassPresent(
                    resolveTargetProcessingClassName(xmlTargetConfig),
                    "XML target record class",
                    xmlTargetConfig.getTargetName()
            );
            return;
        }

        requireClassPresent(
                resolveTargetWriteClassName(targetConfig),
                "Target model class",
                targetConfig.getTargetName()
        );
    }

    private static void requireClassPresent(String className, String role, String configName) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    role + " not found for config '" + configName + "': " + className
                            + ". Ensure the model classes are generated into the package defined by the config before runtime.",
                    e
            );
        }
    }

    private static boolean requiresSourceRootClass(XmlSourceConfig xmlSourceConfig) {
        return !"NestedXml".equalsIgnoreCase(xmlSourceConfig.getFlatteningStrategy());
    }

  private static String qualifyClassName(String packageName, String simpleName) {
    return packageName + "." + simpleName;
  }

  private static String validatedPackageName(String value, String fieldName, String configType, String configName) {
    String trimmed = requireNonBlank(value, fieldName, configType, configName);
    if (!isQualifiedIdentifier(trimmed)) {
      throw new IllegalArgumentException(configType + " config '" + defaultConfigName(configName) + "' has invalid "
          + fieldName + "='" + trimmed + "'. Expected a dot-separated Java package name." + EXPLICIT_JOB_PACKAGE_HINT);
    }
    return trimmed;
  }

  private static String requireNonBlank(String value, String fieldName, String configType, String configName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(configType + " config '" + defaultConfigName(configName)
          + "' must define a non-blank " + fieldName + " before generated model class resolution."
          + ("packageName".equals(fieldName) ? EXPLICIT_JOB_PACKAGE_HINT : ""));
    }
    return value.trim();
  }

  private static String defaultConfigName(String configName) {
    return configName == null || configName.isBlank() ? "unnamed" : configName.trim();
  }

  private static boolean isQualifiedIdentifier(String value) {
    String[] segments = value.split("\\.");
    if (segments.length == 0) {
      return false;
    }
    for (String segment : segments) {
      if (!isJavaIdentifier(segment)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isJavaIdentifier(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    if (!Character.isJavaIdentifierStart(value.charAt(0))) {
      return false;
    }
    for (int i = 1; i < value.length(); i++) {
      if (!Character.isJavaIdentifierPart(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}

