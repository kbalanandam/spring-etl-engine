package com.etl.generation.xml.build;
import java.nio.file.Path;
/**
 * Build-time entry point for generating job-scoped source/target classes for one explicit job.
 */
public final class XmlJobGenerationMain {
    private XmlJobGenerationMain() {
    }
    public static void main(String[] args) throws Exception {
        String jobConfig = requiredArg(args, 0, "etl.xml.generation.jobConfig");
        String outputRoot = optionalArg(args, 1, "etl.xml.generation.outputRoot", "target/generated-sources/etl");
        XmlJobScopedGenerationResult result = new XmlJobScopedGenerationService()
                .generate(Path.of(jobConfig), Path.of(outputRoot));
        System.out.println("Generated job-scoped models for job '" + result.jobName() + "'.");
        System.out.println("Source model groups: " + result.sourceResults().size());
        System.out.println("Target model groups: " + result.targetResults().size());
        System.out.println("Generated files: " + result.allGeneratedFiles().size());
    }
    private static String requiredArg(String[] args, int index, String propertyName) {
        if (args != null && args.length > index && args[index] != null && !args[index].isBlank()) {
            return args[index];
        }
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        throw new IllegalArgumentException("Missing required argument or system property '" + propertyName + "'.");
    }
    private static String optionalArg(String[] args, int index, String propertyName, String defaultValue) {
        if (args != null && args.length > index && args[index] != null && !args[index].isBlank()) {
            return args[index];
        }
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        return defaultValue;
    }
}
