package com.etl.controlplane.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

	private final String serviceName;
	private final Environment environment;
	private final boolean schedulerEnabled;
	private final String schedulerMissedRunPolicy;
	private final String schedulerOverlapPolicy;

	public SystemController(@Value("${spring.application.name:spring-etl-engine-control-plane}") String serviceName,
	                        Environment environment,
	                        @Value("${controlplane.scheduler.enabled:false}") boolean schedulerEnabled,
	                        @Value("${controlplane.scheduler.missed-run-policy:SKIP}") String schedulerMissedRunPolicy,
	                        @Value("${controlplane.scheduler.overlap-policy:ALLOW}") String schedulerOverlapPolicy) {
		this.serviceName = serviceName;
		this.environment = environment;
		this.schedulerEnabled = schedulerEnabled;
		this.schedulerMissedRunPolicy = schedulerMissedRunPolicy;
		this.schedulerOverlapPolicy = schedulerOverlapPolicy;
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
				schedulerOverlapPolicy
		);
	}
}

