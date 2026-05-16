package com.etl.generation.xml;

import com.etl.common.util.JobScopedPackageNameResolver;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loads standalone XML model definitions from YAML.
 *
 * <p>If {@code packageName} is omitted, the loader derives a deterministic fallback from the
 * definition file path so legacy structural-only model definitions can stay package-free while
 * preserving the generated package names expected by older spike tests.</p>
 */
public class XmlModelDefinitionLoader {
  private static final String GENERATED_JOB_BASE_PACKAGE = "com.etl.generated.job";
  private static final String GENERATED_MODEL_BASE_PACKAGE = "com.etl.generated.model";

    private final ObjectMapper mapper;

    public XmlModelDefinitionLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndRegisterModules();
    }

    public XmlModelDefinition load(Path path) throws IOException {
    XmlModelDefinition definition = mapper.readValue(path.toFile(), XmlModelDefinition.class);
    if (definition.getPackageName() == null || definition.getPackageName().isBlank()) {
      definition.setPackageName(derivePackageName(path));
    }
    return definition;
    }

  private String derivePackageName(Path path) {
    Path normalizedPath = path == null ? Path.of("xml-model-definition.yaml") : path.normalize();
    String fileName = normalizedPath.getFileName() == null ? "xml-model-definition" : normalizedPath.getFileName().toString();
    String fileStem = stripExtension(fileName);

    if (fileStem.endsWith("-source-model") || fileStem.endsWith("-target-model")) {
      String role = fileStem.endsWith("-source-model") ? "source" : "target";
      String variant = fileStem.substring(0, fileStem.length() - (role.length() + "-model".length() + 1));
      String bundle = resolveBundleName(normalizedPath);
      return GENERATED_JOB_BASE_PACKAGE
          + "." + JobScopedPackageNameResolver.normalizeJobPackageSegment(bundle)
          + "." + JobScopedPackageNameResolver.normalizeJobPackageSegment(variant)
          + "." + role;
    }

    String parentSegment = normalizedPath.getParent() == null || normalizedPath.getParent().getFileName() == null
        ? "definitions"
        : normalizedPath.getParent().getFileName().toString();
    return GENERATED_MODEL_BASE_PACKAGE
        + "." + JobScopedPackageNameResolver.normalizeJobPackageSegment(parentSegment)
        + "." + JobScopedPackageNameResolver.normalizeJobPackageSegment(fileStem);
  }

  private String resolveBundleName(Path normalizedPath) {
    if (normalizedPath.getParent() == null || normalizedPath.getParent().getFileName() == null) {
      return "xml-model";
    }

    String immediateParent = normalizedPath.getParent().getFileName().toString();
    if ("definitions".equalsIgnoreCase(immediateParent)
        && normalizedPath.getParent().getParent() != null
        && normalizedPath.getParent().getParent().getFileName() != null) {
      return normalizedPath.getParent().getParent().getFileName().toString();
    }

    return immediateParent;
  }

  private String stripExtension(String fileName) {
    int extensionIndex = fileName.lastIndexOf('.');
    return extensionIndex < 0 ? fileName : fileName.substring(0, extensionIndex);
  }
}
