package com.etl.generation.xml.build;

import com.etl.generation.xml.XmlModelGenerationResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Build-time generation summary for one job-scoped XML generation run.
 */
public record XmlJobScopedGenerationResult(
        String jobName,
        List<XmlModelGenerationResult> sourceResults,
        List<XmlModelGenerationResult> targetResults
) {

    public XmlJobScopedGenerationResult {
        sourceResults = sourceResults == null ? List.of() : List.copyOf(sourceResults);
        targetResults = targetResults == null ? List.of() : List.copyOf(targetResults);
    }

    public List<Path> allGeneratedFiles() {
        List<Path> files = new ArrayList<>();
        sourceResults.forEach(result -> files.addAll(result.generatedFiles()));
        targetResults.forEach(result -> files.addAll(result.generatedFiles()));
        return List.copyOf(files);
    }
}

