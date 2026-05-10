package com.etl.common.util;

import com.etl.config.job.JobConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}


