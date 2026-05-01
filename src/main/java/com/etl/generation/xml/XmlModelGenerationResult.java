package com.etl.generation.xml;
import java.nio.file.Path;
import java.util.List;
/**
 * Result of generating XML model source files from a structural definition.
 */
public record XmlModelGenerationResult(
        String packageName,
        String rootClassName,
        String recordClassName,
        List<Path> generatedFiles
) {
}
