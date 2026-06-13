package com.etl.config;

import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.exception.config.ConfigException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeStepPolicyResolverTest {

    private final RuntimeStepPolicyResolver resolver = new RuntimeStepPolicyResolver();

    @Test
    void resolveExplicitStepsNormalizesEnabledSkipPolicyForCsvSources() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(stepWithSkipPolicy(
                "customers-step",
                "Customers",
                "CustomersOut",
                3,
                List.of("RUNTIME", "source_read"),
                List.of("java.lang.IllegalArgumentException")
        )));

        List<JobConfig.JobStepConfig> resolvedSteps = resolver.resolveExplicitSteps(
                jobConfig,
                sourceWrapper(csvSource("Customers")),
                targetWrapper(csvTarget("CustomersOut")),
                processorConfig(mapping("Customers", "CustomersOut"))
        );

        assertEquals(1, resolvedSteps.size());
        JobConfig.JobStepConfig resolved = resolvedSteps.get(0);
        assertNotNull(resolved.getSkipPolicy());
        assertNull(resolved.getRetryPolicy());
        assertEquals(3, resolved.getSkipPolicy().getSkipLimit());
        assertEquals(List.of("runtime", "source-read"), resolved.getSkipPolicy().getSkippableCategories());
        assertEquals(List.of("java.lang.IllegalArgumentException"), resolved.getSkipPolicy().getSkippableExceptions());
    }

    @Test
    void resolveExplicitStepsNormalizesEnabledRetryPolicy() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(stepWithRetryPolicy(
                "customers-step",
                "Customers",
                "CustomersOut",
                4,
                250L,
                List.of("TARGET_WRITE"),
                List.of("java.lang.IllegalStateException")
        )));

        List<JobConfig.JobStepConfig> resolvedSteps = resolver.resolveExplicitSteps(
                jobConfig,
                sourceWrapper(csvSource("Customers")),
                targetWrapper(csvTarget("CustomersOut")),
                processorConfig(mapping("Customers", "CustomersOut"))
        );

        assertEquals(1, resolvedSteps.size());
        JobConfig.JobStepConfig resolved = resolvedSteps.get(0);
        assertNull(resolved.getSkipPolicy());
        assertNotNull(resolved.getRetryPolicy());
        assertEquals(4, resolved.getRetryPolicy().getMaxAttempts());
        assertEquals(250L, resolved.getRetryPolicy().getBackoffMs());
        assertEquals(List.of("target-write"), resolved.getRetryPolicy().getRetryableCategories());
        assertEquals(List.of("java.lang.IllegalStateException"), resolved.getRetryPolicy().getRetryableExceptions());
    }

    @Test
    void resolveExplicitStepsFailsFastWhenStepsAreMissing() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of());

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "CustomersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("non-empty 'steps'"));
    }

    @Test
    void resolveExplicitStepsFailsFastWhenStepNameIsDuplicated() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(
                step("customers-step", "Customers", "CustomersOut"),
                step("customers-step", "Customers", "CustomersOut")
        ));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "CustomersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("duplicate step name 'customers-step'"));
    }

    @Test
    void resolveExplicitStepsFailsFastWhenProcessorMappingIsMissing() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(step("customers-step", "Customers", "CustomersOut")));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "OtherTarget"))
                )
        );

        assertTrue(exception.getMessage().contains("requires a processor mapping"));
    }

    @Test
    void resolveExplicitStepsFailsFastWhenSkipPolicyIsUsedOnNonCsvSource() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(stepWithSkipPolicy(
                "xml-step",
                "OrdersXml",
                "OrdersOut",
                2,
                List.of("runtime"),
                List.of()
        )));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(xmlSource("OrdersXml")),
                        targetWrapper(csvTarget("OrdersOut")),
                        processorConfig(mapping("OrdersXml", "OrdersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("supported only for CSV sources"));
    }

    @Test
    void resolveExplicitStepsFailsFastWhenStepConfiguresSkipAndRetryPolicyTogether() {
        JobConfig.JobStepConfig step = step("customers-step", "Customers", "CustomersOut");
        step.setSkipPolicy(enabledSkipPolicy(2, List.of("runtime"), List.of()));
        step.setRetryPolicy(enabledRetryPolicy(3, 100L, List.of("runtime"), List.of()));

        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(step));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "CustomersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("configures both skipPolicy and retryPolicy"));
    }

    @Test
    void resolveExplicitStepsFailsFastWhenStepKindIsUnsupported() {
        JobConfig.JobStepConfig step = step("customers-step", "Customers", "CustomersOut");
        step.setKind("scripted");
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(step));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "CustomersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("unsupported kind"));
    }

    @Test
    void resolveExplicitStepsAcceptsCustomStepWithTypeAndPreservesKind() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(customStep("header-start", "headerStart")));

        List<JobConfig.JobStepConfig> resolvedSteps = resolver.resolveExplicitSteps(
                jobConfig,
                sourceWrapper(csvSource("Customers")),
                targetWrapper(csvTarget("CustomersOut")),
                processorConfig(mapping("Customers", "CustomersOut"))
        );

        assertEquals(1, resolvedSteps.size());
        JobConfig.JobStepConfig resolved = resolvedSteps.get(0);
        assertTrue(resolved.isCustomStep());
        assertEquals("custom", resolved.getKind());
        assertNotNull(resolved.getCustom());
        assertEquals("headerStart", resolved.getCustom().getType());
        assertNull(resolved.getSource());
        assertNull(resolved.getTarget());
    }

    @Test
    void resolveExplicitStepsFailsFastWhenCustomStepOmitsCustomType() {
        JobConfig.JobStepConfig step = customStep("header-start", " ");
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(step));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "CustomersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("custom.type"));
    }

    @Test
    void resolveExplicitStepsFailsFastWhenCustomStepDefinesSourceOrTarget() {
        JobConfig.JobStepConfig step = customStep("header-start", "headerStart");
        step.setSource("Customers");
        step.setTarget("CustomersOut");
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(step));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "CustomersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("must not define source/target"));
    }

    @Test
    void resolveExplicitStepsFailsFastWhenStandardStepDefinesCustomBlock() {
        JobConfig.JobStepConfig step = step("customers-step", "Customers", "CustomersOut");
        step.setKind("standard");
        JobConfig.CustomStepConfig custom = new JobConfig.CustomStepConfig();
        custom.setType("auditNoop");
        step.setCustom(custom);
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(step));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "CustomersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("must not define steps[].custom"));
    }

    @Test
    void resolveExplicitStepsFailsFastWhenCustomStepEnablesSkipPolicy() {
        JobConfig.JobStepConfig step = customStep("header-start", "headerStart");
        step.setSkipPolicy(enabledSkipPolicy(2, List.of("runtime"), List.of()));
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(step));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "CustomersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("cannot enable skipPolicy"));
    }

    @Test
    void resolveExplicitStepsFailsFastWhenCustomStepEnablesRetryPolicy() {
        JobConfig.JobStepConfig step = customStep("header-start", "headerStart");
        step.setRetryPolicy(enabledRetryPolicy(3, 100L, List.of("runtime"), List.of()));
        JobConfig jobConfig = new JobConfig();
        jobConfig.setSteps(List.of(step));

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> resolver.resolveExplicitSteps(
                        jobConfig,
                        sourceWrapper(csvSource("Customers")),
                        targetWrapper(csvTarget("CustomersOut")),
                        processorConfig(mapping("Customers", "CustomersOut"))
                )
        );

        assertTrue(exception.getMessage().contains("cannot enable retryPolicy"));
    }

    private JobConfig.JobStepConfig step(String name, String source, String target) {
        JobConfig.JobStepConfig step = new JobConfig.JobStepConfig();
        step.setName(name);
        step.setSource(source);
        step.setTarget(target);
        return step;
    }

    private JobConfig.JobStepConfig customStep(String name, String customType) {
        JobConfig.JobStepConfig step = new JobConfig.JobStepConfig();
        step.setName(name);
        step.setKind("custom");
        JobConfig.CustomStepConfig custom = new JobConfig.CustomStepConfig();
        custom.setType(customType);
        step.setCustom(custom);
        return step;
    }

    private JobConfig.JobStepConfig stepWithSkipPolicy(String name,
                                                       String source,
                                                       String target,
                                                       int skipLimit,
                                                       List<String> categories,
                                                       List<String> exceptions) {
        JobConfig.JobStepConfig step = step(name, source, target);
        step.setSkipPolicy(enabledSkipPolicy(skipLimit, categories, exceptions));
        return step;
    }

    private JobConfig.JobStepConfig stepWithRetryPolicy(String name,
                                                        String source,
                                                        String target,
                                                        int maxAttempts,
                                                        long backoffMs,
                                                        List<String> categories,
                                                        List<String> exceptions) {
        JobConfig.JobStepConfig step = step(name, source, target);
        step.setRetryPolicy(enabledRetryPolicy(maxAttempts, backoffMs, categories, exceptions));
        return step;
    }

    private JobConfig.SkipPolicyConfig enabledSkipPolicy(int skipLimit,
                                                         List<String> categories,
                                                         List<String> exceptions) {
        JobConfig.SkipPolicyConfig skipPolicy = new JobConfig.SkipPolicyConfig();
        skipPolicy.setEnabled(true);
        skipPolicy.setSkipLimit(skipLimit);
        skipPolicy.setSkippableCategories(categories);
        skipPolicy.setSkippableExceptions(exceptions);
        return skipPolicy;
    }

    private JobConfig.RetryPolicyConfig enabledRetryPolicy(int maxAttempts,
                                                           long backoffMs,
                                                           List<String> categories,
                                                           List<String> exceptions) {
        JobConfig.RetryPolicyConfig retryPolicy = new JobConfig.RetryPolicyConfig();
        retryPolicy.setEnabled(true);
        retryPolicy.setMaxAttempts(maxAttempts);
        retryPolicy.setBackoffMs(backoffMs);
        retryPolicy.setRetryableCategories(categories);
        retryPolicy.setRetryableExceptions(exceptions);
        return retryPolicy;
    }

    private SourceWrapper sourceWrapper(SourceConfig... sources) {
        SourceWrapper wrapper = new SourceWrapper();
        wrapper.setSources(List.of(sources));
        return wrapper;
    }

    private TargetWrapper targetWrapper(TargetConfig... targets) {
        TargetWrapper wrapper = new TargetWrapper();
        wrapper.setTargets(List.of(targets));
        return wrapper;
    }

    private ProcessorConfig processorConfig(ProcessorConfig.EntityMapping... mappings) {
        ProcessorConfig config = new ProcessorConfig();
        config.setType("default");
        config.setMappings(List.of(mappings));
        return config;
    }

    private ProcessorConfig.EntityMapping mapping(String source, String target) {
        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource(source);
        mapping.setTarget(target);
        return mapping;
    }

    private CsvSourceConfig csvSource(String sourceName) {
        CsvSourceConfig sourceConfig = new CsvSourceConfig();
        sourceConfig.setSourceName(sourceName);
        sourceConfig.setFilePath("input.csv");
        sourceConfig.setDelimiter(",");
        return sourceConfig;
    }

    private XmlSourceConfig xmlSource(String sourceName) {
        XmlSourceConfig sourceConfig = new XmlSourceConfig();
        sourceConfig.setSourceName(sourceName);
        sourceConfig.setFilePath("input.xml");
        sourceConfig.setRootElement("Root");
        sourceConfig.setRecordElement("Record");
        return sourceConfig;
    }

    private CsvTargetConfig csvTarget(String targetName) {
        return new CsvTargetConfig(targetName, null, List.of(), "output.csv", ",");
    }
}

