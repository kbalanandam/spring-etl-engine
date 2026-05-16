package com.etl.common.util;

import com.etl.config.job.JobConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobScopedPackageNameResolverTest {

    @Test
    void derivesDefaultSourceAndTargetPackagesFromConfiguredJobName() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.setName("csv-to-nested-xml");

        assertEquals("csv-to-nested-xml", JobScopedPackageNameResolver.deriveJobName(jobConfig, Path.of("config-jobs", "ignored")));
        assertEquals("com.etl.generated.job.csvtonestedxml.source", JobScopedPackageNameResolver.resolveSourcePackage(jobConfig.getName()));
        assertEquals("com.etl.generated.job.csvtonestedxml.target", JobScopedPackageNameResolver.resolveTargetPackage(jobConfig.getName()));
    }

    @Test
    void fallsBackToFolderNameWhenConfiguredJobNameIsBlank() {
        JobConfig jobConfig = new JobConfig();
        jobConfig.setName("   ");

        assertEquals("customer-load", JobScopedPackageNameResolver.deriveJobName(jobConfig, Path.of("config-jobs", "customer-load")));
        assertEquals("customerload", JobScopedPackageNameResolver.normalizeJobPackageSegment("customer-load"));
    }

    @Test
    void prefixesLeadingDigitsAndDropsNonAlphanumericCharacters() {
        assertEquals("job2026demojob", JobScopedPackageNameResolver.normalizeJobPackageSegment("2026 Demo Job!"));
        assertEquals("selectedjob", JobScopedPackageNameResolver.normalizeJobPackageSegment("!!!"));
    }

    @Test
    void failsFastWhenExplicitPackageNameIsProvidedOnSelectedJobPath() {
        String derivedPackage = JobScopedPackageNameResolver.resolveSourcePackage("events-job");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> JobScopedPackageNameResolver.requireNoExplicitSelectedJobPackageName(
                        "source config",
                        "Events",
                        "events-job",
                        Path.of("config-jobs", "events-job", "source-config.yaml"),
                        "com.etl.generated.job.events.source",
                        derivedPackage
                )
        );

        String message = exception.getMessage();

        assertTrue(message.contains("source config 'Events'"));
        assertTrue(message.contains("config-jobs"));
        assertTrue(message.contains("com.etl.generated.job.events.source"));
        assertTrue(message.contains(derivedPackage));
        assertTrue(message.contains("does not allow explicit packageName"));
        assertTrue(message.contains("Remove packageName"));
    }

    @Test
    void ignoresBlankPackageNameWhenSelectedJobDerivesPackageInternally() {
        assertFalse(JobScopedPackageNameResolver.hasExplicitPackageName(null));
        assertFalse(JobScopedPackageNameResolver.hasExplicitPackageName("   "));
        assertTrue(JobScopedPackageNameResolver.hasExplicitPackageName("com.etl.model.target"));
    }
}


