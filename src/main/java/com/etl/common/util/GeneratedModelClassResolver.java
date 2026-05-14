package com.etl.common.util;

import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.XmlTargetConfig;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Resolves runtime classes and wrapper metadata for dynamically generated models.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This centralizes the naming contract between model generators and downstream batch
 * components so XML wrapper/record handling is defined in one place.</p>
 *
 * <p>In practice this class is the compatibility boundary between config loading,
 * generated source output, processors, readers, and writers. Callers should resolve
 * class names and wrapper metadata here instead of reconstructing package names or
 * XML class shapes locally. That keeps the runtime aligned with the active naming
 * bridge and the generated-model naming policy.</p>
 */
public final class GeneratedModelClassResolver {

  private static final String EXPLICIT_JOB_PACKAGE_HINT = " In explicit job mode this package should be derived during ConfigLoader package defaulting before model resolution.";

    private GeneratedModelClassResolver() {
    }

    /**
     * Resolves the generated source record class name for the supplied source config.
     *
     * <p>For XML sources this is the generated record class read fragment-by-fragment or emitted by
     * the flattening strategy. For flat formats it is the single generated source model class used
     * by downstream mapping and processing.</p>
     */
    public static String resolveSourceClassName(SourceConfig sourceConfig) {
		if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig) {
      return qualifyClassName(
          validatedPackageName(xmlSourceConfig.getPackageName(), "XML source", xmlSourceConfig.getSourceName()),
          GeneratedModelNamingPolicy.resolveSourceSimpleClassName(xmlSourceConfig)
      );
		}
    return qualifyClassName(
        validatedPackageName(sourceConfig.getPackageName(), "source", sourceConfig.getSourceName()),
        GeneratedModelNamingPolicy.resolveSourceSimpleClassName(sourceConfig)
    );
    }

    /**
     * Resolves the generated XML root/wrapper class name for the supplied source config.
     *
     * <p>Only XML sources with root-oriented processing paths need a distinct root class. Non-XML
     * sources and record-oriented XML paths fall back to the normal source record class name.</p>
     */
    public static String resolveSourceRootClassName(SourceConfig sourceConfig) {
        if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig) {
      return qualifyClassName(
          validatedPackageName(xmlSourceConfig.getPackageName(), "XML source", xmlSourceConfig.getSourceName()),
          GeneratedModelNamingPolicy.resolveSourceRootSimpleClassName(xmlSourceConfig)
      );
        }
        return resolveSourceClassName(sourceConfig);
    }

    /**
     * Resolves the shared source/target model contract for one selected step pairing.
     *
     * <p>This handoff object lets runtime components agree on the generated source record class,
     * target processing class, target write class, and any XML wrapper metadata without each caller
     * reconstructing naming logic independently.</p>
     */
    public static ResolvedModelMetadata resolveMetadata(SourceConfig sourceConfig, TargetConfig targetConfig) {
        // This is the main handoff object for runtime components that need to agree
        // on source class, target processing class, target write class, and any
        // XML-specific wrapper details for the same selected step pairing.
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

    /**
     * Resolves the XML/root write class or flat target model class name.
     *
     * <p>For XML targets this is the wrapper/root class used when writing the final
     * document. For non-XML targets this is the single generated target model class.</p>
     */
    public static String resolveTargetWriteClassName(TargetConfig targetConfig) {
        if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
      return qualifyClassName(
          validatedPackageName(xmlTargetConfig.getPackageName(), "XML target", xmlTargetConfig.getTargetName()),
          GeneratedModelNamingPolicy.resolveTargetWriteSimpleClassName(xmlTargetConfig)
      );
        }
    return qualifyClassName(
        validatedPackageName(targetConfig.getPackageName(), "target", targetConfig.getTargetName()),
        GeneratedModelNamingPolicy.resolveTargetWriteSimpleClassName(targetConfig)
    );
    }

    /**
     * Resolves the target processing class name used by mapper/processor/chunk flows.
     *
     * <p>For XML targets this is the record-level class that represents one logical
     * output item inside the final wrapper document. For non-XML targets it matches
     * the single generated target model class.</p>
     */
    public static String resolveTargetProcessingClassName(TargetConfig targetConfig) {
        if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
      return qualifyClassName(
          validatedPackageName(xmlTargetConfig.getPackageName(), "XML target", xmlTargetConfig.getTargetName()),
          GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(xmlTargetConfig)
      );
        }
    return qualifyClassName(
        validatedPackageName(targetConfig.getPackageName(), "target", targetConfig.getTargetName()),
        GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(targetConfig)
    );
    }

    /**
     * Loads the generated source record class using the application class loader.
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveSourceClass(SourceConfig sourceConfig) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveSourceClassName(sourceConfig));
    }

    /**
     * Loads the generated XML source root class using the application class loader.
     */
    @SuppressWarnings({"unchecked", "unused"})
    public static <T> Class<T> resolveSourceRootClass(SourceConfig sourceConfig) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveSourceRootClassName(sourceConfig));
    }

    /**
     * Loads the generated XML source root class using the supplied class loader.
     *
     * <p>This overload is used when XML root classes are generated in a job-scoped package that may
     * need an explicit loader choice at runtime.</p>
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveSourceRootClass(SourceConfig sourceConfig, ClassLoader classLoader) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveSourceRootClassName(sourceConfig), true, classLoader);
    }

    /**
     * Loads the source class using previously resolved metadata.
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveSourceClass(ResolvedModelMetadata metadata) throws ClassNotFoundException {
        return (Class<T>) Class.forName(metadata.getSourceClassName());
    }

    /**
     * Loads the generated target write class for the supplied target config.
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveTargetWriteClass(TargetConfig targetConfig) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveTargetWriteClassName(targetConfig));
    }

    /**
     * Loads the target write class using previously resolved metadata.
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveTargetWriteClass(ResolvedModelMetadata metadata) throws ClassNotFoundException {
        return (Class<T>) Class.forName(metadata.getTargetWriteClassName());
    }

    /**
     * Loads the generated target processing class for the supplied target config.
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveTargetProcessingClass(TargetConfig targetConfig) throws ClassNotFoundException {
        return (Class<T>) Class.forName(resolveTargetProcessingClassName(targetConfig));
    }

    /**
     * Loads the target processing class using previously resolved metadata.
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveTargetProcessingClass(ResolvedModelMetadata metadata) throws ClassNotFoundException {
        return (Class<T>) Class.forName(metadata.getTargetProcessingClassName());
    }

    /**
     * Creates the generated XML wrapper/root object for final publication and assigns the repeated
     * record collection to its generated wrapper field.
     */
    public static Object createXmlWrapper(XmlTargetConfig targetConfig, List<?> records) throws ReflectiveOperationException {
        Class<?> wrapperClass = resolveTargetWriteClass(targetConfig);
        Object wrapper = wrapperClass.getDeclaredConstructor().newInstance();
        Field field = wrapperClass.getDeclaredField(resolveXmlWrapperFieldName(targetConfig));
        field.setAccessible(true);
        field.set(wrapper, records);
        return wrapper;
    }

    /**
     * Creates a wrapper/root object for XML target publication using pre-resolved metadata.
     *
     * <p>This method is used when callers already resolved a step's model contract via
     * {@link #resolveMetadata(SourceConfig, TargetConfig)} and want to avoid repeating
     * XML-specific naming logic.</p>
     */
    public static Object createWrapper(ResolvedModelMetadata metadata, List<?> records) throws ReflectiveOperationException {
        Class<?> wrapperClass = resolveTargetWriteClass(metadata);
        Object wrapper = wrapperClass.getDeclaredConstructor().newInstance();
        Field field = wrapperClass.getDeclaredField(metadata.getWrapperFieldName());
        field.setAccessible(true);
        field.set(wrapper, records);
        return wrapper;
    }

    /**
     * Resolves the field name on the XML wrapper/root class that stores repeated records.
     *
     * <p>The field name is derived through the shared naming policy rather than by
     * assuming that XML element names map directly to Java field names. This keeps
     * wrapper instantiation aligned with generated source output.</p>
     */
    public static String resolveXmlWrapperFieldName(XmlTargetConfig targetConfig) {
		String packageName = validatedPackageName(targetConfig.getPackageName(), "XML target", targetConfig.getTargetName());
        return GeneratedModelNamingPolicy.resolveWrapperFieldName(
                packageName,
                requireNonBlank(targetConfig.getRecordElement(), "recordElement", "XML target", targetConfig.getTargetName()),
                GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(targetConfig)
        );
    }

    /**
     * Verifies that the generated source model classes required by the selected source config are
     * available on the runtime classpath.
     *
     * <p>In explicit job mode, package defaulting is expected to have happened before this method is
     * reached. Missing classes therefore usually indicate that generation did not run or produced
     * output into a package that no longer matches the selected config contract.</p>
     */
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

    /**
     * Verifies that the generated target model classes required by the selected target config are
     * available on the runtime classpath.
     *
     * <p>XML targets require both the wrapper/root write class and the record-level processing
     * class. Other target formats require only the single generated target model class.</p>
     */
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

    /**
     * Raises a consistent runtime error when a generated model class expected by the selected
     * config contract is missing from the classpath.
     */
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

  /**
   * Builds the fully qualified generated class name from a validated package and simple class name.
   */
  private static String qualifyClassName(String packageName, String simpleName) {
    return packageName + "." + simpleName;
  }

  /**
   * Validates that the configured generated-model package is non-blank and structurally valid.
   */
  private static String validatedPackageName(String value, String configType, String configName) {
    String trimmed = requirePackageName(value, configType, configName);
    if (!isQualifiedIdentifier(trimmed)) {
      throw new IllegalArgumentException(configType + " config '" + defaultConfigName(configName) + "' has invalid "
          + "packageName='" + trimmed + "'. Expected a dot-separated Java package name." + EXPLICIT_JOB_PACKAGE_HINT);
    }
    return trimmed;
  }

  /**
   * Requires a non-blank generated-model package name before class resolution continues.
   */
  private static String requirePackageName(String value, String configType, String configName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(configType + " config '" + defaultConfigName(configName)
          + "' must define a non-blank packageName before generated model class resolution."
          + EXPLICIT_JOB_PACKAGE_HINT);
    }
    return value.trim();
  }

  /**
   * Requires one non-blank configuration value before generated model resolution continues.
   */
  private static String requireNonBlank(String value, String fieldName, String configType, String configName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(configType + " config '" + defaultConfigName(configName)
          + "' must define a non-blank " + fieldName + " before generated model class resolution.");
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

