package com.etl.controlplane;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Profile("controlplane")
class ControlPlanePersistenceContractGuard {

    // Startup guard for the optional control-plane persistence contract.
    // Keeps mode/vendor wiring explicit and fail-fast before registries initialize.

    private static final Set<String> SUPPORTED_MODES = Set.of("memory", "jdbc", "jpa");
    private static final Set<String> SUPPORTED_VENDORS = Set.of("postgresql", "mysql", "mssql", "oracle");

    private final String triggerMode;
    private final String runMode;
    private final String scheduleMode;
    private final String dbVendor;
    private final String dbUrl;
    private final String dbDriver;

    ControlPlanePersistenceContractGuard(
            @Value("${controlplane.triggers.persistence.mode:memory}") String triggerMode,
            @Value("${controlplane.runs.persistence.mode:memory}") String runMode,
            @Value("${controlplane.schedules.persistence.mode:memory}") String scheduleMode,
            @Value("${controlplane.db.vendor:}") String dbVendor,
            @Value("${controlplane.db.url:}") String dbUrl,
            @Value("${controlplane.db.driver-class-name:}") String dbDriver) {
        this.triggerMode = normalizeMode(triggerMode);
        this.runMode = normalizeMode(runMode);
        this.scheduleMode = normalizeMode(scheduleMode);
        this.dbVendor = normalizeVendor(dbVendor);
        this.dbUrl = normalize(dbUrl);
        this.dbDriver = normalize(dbDriver);
    }

    @PostConstruct
    void validateContract() {
        validateMode("controlplane.triggers.persistence.mode", triggerMode);
        validateMode("controlplane.runs.persistence.mode", runMode);
        validateMode("controlplane.schedules.persistence.mode", scheduleMode);

        // All control-plane registries must run on one persistence mode to avoid split-brain behavior.
        if (!(triggerMode.equals(runMode) && runMode.equals(scheduleMode))) {
            throw new IllegalStateException("Ambiguous control-plane persistence mode selection:"
                    + " triggers='" + triggerMode + "', runs='" + runMode + "', schedules='" + scheduleMode + "'."
                    + " Use one shared mode across trigger/run/schedule persistence contracts.");
        }

        // Memory mode intentionally avoids datasource requirements.
        if ("memory".equals(triggerMode)) {
            return;
        }

        // JDBC mode requires a recognized vendor plus URL/driver shape aligned to that vendor.
        if (!SUPPORTED_VENDORS.contains(dbVendor)) {
            throw new IllegalStateException("Unsupported controlplane.db.vendor='" + dbVendor + "'."
                    + " Supported values: postgresql, mysql, mssql, oracle.");
        }
        if (dbUrl.isBlank()) {
            throw new IllegalStateException("JDBC persistence mode requires non-empty controlplane.db.url.");
        }
        if (dbDriver.isBlank()) {
            throw new IllegalStateException("JDBC persistence mode requires non-empty controlplane.db.driver-class-name.");
        }

        String expectedUrlPrefix = expectedJdbcUrlPrefix(dbVendor);
        if (!dbUrl.toLowerCase(Locale.ROOT).startsWith(expectedUrlPrefix)) {
            throw new IllegalStateException("Vendor/JDBC URL mismatch: controlplane.db.vendor='" + dbVendor
                    + "' expects URL prefix '" + expectedUrlPrefix + "' but got '" + dbUrl + "'.");
        }

        String expectedDriverToken = expectedDriverToken(dbVendor);
        if (!dbDriver.toLowerCase(Locale.ROOT).contains(expectedDriverToken)) {
            throw new IllegalStateException("Vendor/driver mismatch: controlplane.db.vendor='" + dbVendor
                    + "' expects driver token containing '" + expectedDriverToken + "' but got '" + dbDriver + "'.");
        }
    }

    private void validateMode(String property, String mode) {
        if (!SUPPORTED_MODES.contains(mode)) {
            throw new IllegalStateException("Unsupported " + property + "='" + mode + "'. Supported values: memory, jdbc, jpa.");
        }
    }

    private String normalizeMode(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private String normalizeVendor(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sqlserver" -> "mssql";
            case "postgres" -> "postgresql";
            default -> normalized;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String expectedJdbcUrlPrefix(String vendor) {
        return Map.of(
                "postgresql", "jdbc:postgresql:",
                "mysql", "jdbc:mysql:",
                "mssql", "jdbc:sqlserver:",
                "oracle", "jdbc:oracle:"
        ).get(vendor);
    }

    private String expectedDriverToken(String vendor) {
        return Map.of(
                "postgresql", "postgresql",
                "mysql", "mysql",
                "mssql", "sqlserver",
                "oracle", "oracle"
        ).get(vendor);
    }
}


