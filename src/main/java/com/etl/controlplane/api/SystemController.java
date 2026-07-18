package com.etl.controlplane.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

	private final String serviceName;
	private final Environment environment;
	private final boolean schedulerEnabled;
	private final String schedulerMissedRunPolicy;
	private final String schedulerOverlapPolicy;
	private final String databaseVendor;
	private final String databaseDisplayName;

	public SystemController(@Value("${spring.application.name:spring-etl-engine-control-plane}") String serviceName,
	                        Environment environment,
	                        @Value("${controlplane.scheduler.enabled:false}") boolean schedulerEnabled,
	                        @Value("${controlplane.scheduler.missed-run-policy:SKIP}") String schedulerMissedRunPolicy,
	                        @Value("${controlplane.scheduler.overlap-policy:ALLOW}") String schedulerOverlapPolicy,
	                        @Value("${controlplane.db.vendor:mysql}") String configuredDatabaseVendor) {
		this.serviceName = serviceName;
		this.environment = environment;
		this.schedulerEnabled = schedulerEnabled;
		this.schedulerMissedRunPolicy = schedulerMissedRunPolicy;
		this.schedulerOverlapPolicy = schedulerOverlapPolicy;
		this.databaseVendor = normalizeDatabaseVendor(configuredDatabaseVendor);
		this.databaseDisplayName = toDatabaseDisplayName(this.databaseVendor);
	}

	@GetMapping("/health")
	public SystemHealthResponse health() {
		return new SystemHealthResponse("UP", Instant.now());
	}

	@GetMapping("/info")
	public SystemInfoResponse info() {
		String profile = Arrays.stream(environment.getActiveProfiles()).findFirst().orElse("default");
		return new SystemInfoResponse(
				serviceName,
				System.getProperty("java.version"),
				profile,
				schedulerEnabled,
				schedulerMissedRunPolicy,
				schedulerOverlapPolicy,
				databaseVendor,
				databaseDisplayName
		);
	}

	private static String normalizeDatabaseVendor(String configuredDatabaseVendor) {
		String normalized = configuredDatabaseVendor == null ? "" : configuredDatabaseVendor.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "sqlserver", "mssql" -> "mssql";
			case "mysql" -> "mysql";
			case "" -> "unknown";
			default -> normalized;
		};
	}

	private static String toDatabaseDisplayName(String normalizedDatabaseVendor) {
		return switch (normalizedDatabaseVendor) {
			case "mssql" -> "SQL Server";
			case "mysql" -> "MySQL";
			case "unknown" -> "Unknown";
			default -> normalizedDatabaseVendor;
		};
	}
}

