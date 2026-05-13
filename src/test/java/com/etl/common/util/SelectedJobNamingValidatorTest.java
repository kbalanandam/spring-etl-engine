package com.etl.common.util;

import com.etl.config.ColumnConfig;
import com.etl.config.job.JobConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.TargetWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedJobNamingValidatorTest {

    @Test
    void allowsSameLogicalNameWithinOneStepForCurrentSingleStepCompatibility() {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", "com.etl.generated.job.customerload.source")));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(csvTarget("Customers", "com.etl.generated.job.customerload.target")));

        assertDoesNotThrow(() -> SelectedJobNamingValidator.validate(
                "customer-load",
                sourceWrapper,
                targetWrapper,
                List.of(step("customers-step", "Customers", "Customers"))
        ));
    }

    @Test
    void allowsSelectedIntermediateReuseWhenTargetIsProducedBeforeLaterSourceConsumption() {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(
                csvSource("IngressFeed", "com.etl.generated.job.tvljob.source"),
                csvSource("TagValidationCsvIntermediate", "com.etl.generated.job.tvljob.source")
        ));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(
                csvTarget("TagValidationCsvIntermediate", "com.etl.generated.job.tvljob.target"),
                csvTarget("TagValidationDb", "com.etl.generated.job.tvljob.target")
        ));

        assertDoesNotThrow(() -> SelectedJobNamingValidator.validate(
                "tvl-job",
                sourceWrapper,
                targetWrapper,
                List.of(
                        step("xml-to-csv", "IngressFeed", "TagValidationCsvIntermediate"),
                        step("csv-to-db", "TagValidationCsvIntermediate", "TagValidationDb")
                )
        ));
    }

    @Test
    void failsFastWhenLogicalNameIsConsumedBeforeItIsProduced() {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(
                csvSource("TagValidationCsvIntermediate", "com.etl.generated.job.tvljob.source"),
                csvSource("IngressFeed", "com.etl.generated.job.tvljob.source")
        ));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(
                csvTarget("TagValidationDb", "com.etl.generated.job.tvljob.target"),
                csvTarget("TagValidationCsvIntermediate", "com.etl.generated.job.tvljob.target")
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SelectedJobNamingValidator.validate(
                        "tvl-job",
                        sourceWrapper,
                        targetWrapper,
                        List.of(
                                step("csv-to-db", "TagValidationCsvIntermediate", "TagValidationDb"),
                                step("xml-to-csv", "IngressFeed", "TagValidationCsvIntermediate")
                        )
                )
        );

        assertTrue(exception.getMessage().contains("before it is produced"));
        assertTrue(exception.getMessage().contains("TagValidationCsvIntermediate"));
    }

    @Test
    void failsFastWhenSelectedSourceNamesNormalizeToTheSameGeneratedClass() {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(
                csvSource("Customer Feed", "com.etl.generated.job.namingjob.source"),
                csvSource("Customer-Feed", "com.etl.generated.job.namingjob.source")
        ));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(
                csvTarget("FirstTarget", "com.etl.generated.job.namingjob.target"),
                csvTarget("SecondTarget", "com.etl.generated.job.namingjob.target")
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SelectedJobNamingValidator.validate(
                        "naming-job",
                        sourceWrapper,
                        targetWrapper,
                        List.of(
                                step("first-step", "Customer Feed", "FirstTarget"),
                                step("second-step", "Customer-Feed", "SecondTarget")
                        )
                )
        );

        assertTrue(exception.getMessage().contains("naming collision"));
        assertTrue(exception.getMessage().contains("Customer Feed"));
        assertTrue(exception.getMessage().contains("Customer-Feed"));
        assertTrue(exception.getMessage().contains("CustomerFeedModel"));
    }

    @Test
    void failsFastWhenSelectedTargetNamesNormalizeToTheSameGeneratedClass() {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(
                csvSource("FirstSource", "com.etl.generated.job.namingjob.source"),
                csvSource("SecondSource", "com.etl.generated.job.namingjob.source")
        ));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(
                csvTarget("Customer Export", "com.etl.generated.job.namingjob.target"),
                csvTarget("Customer-Export", "com.etl.generated.job.namingjob.target")
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SelectedJobNamingValidator.validate(
                        "naming-job",
                        sourceWrapper,
                        targetWrapper,
                        List.of(
                                step("first-step", "FirstSource", "Customer Export"),
                                step("second-step", "SecondSource", "Customer-Export")
                        )
                )
        );

        assertTrue(exception.getMessage().contains("naming collision"));
        assertTrue(exception.getMessage().contains("Customer Export"));
        assertTrue(exception.getMessage().contains("Customer-Export"));
        assertTrue(exception.getMessage().contains("CustomerExportModel"));
    }

    private static CsvSourceConfig csvSource(String sourceName, String packageName) {
        return new CsvSourceConfig(sourceName, packageName, List.of(column()), "input/data.csv", ",");
    }

    private static CsvTargetConfig csvTarget(String targetName, String packageName) {
        return new CsvTargetConfig(targetName, packageName, List.of(column()), "output/data.csv", ",");
    }

    private static JobConfig.JobStepConfig step(String stepName, String sourceName, String targetName) {
        JobConfig.JobStepConfig step = new JobConfig.JobStepConfig();
        step.setName(stepName);
        step.setSource(sourceName);
        step.setTarget(targetName);
        return step;
    }

    private static ColumnConfig column() {
        ColumnConfig column = new ColumnConfig();
        column.setName("id");
        column.setType("String");
        return column;
    }
}



