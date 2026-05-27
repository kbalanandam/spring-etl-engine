package com.etl.controlplane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

/**
 * Optional control-plane API launcher.
 *
 * <p>This process intentionally scans only control-plane packages so ETL worker
 * startup beans (for example batch launch runner) remain out of this runtime.
 * Run this as a separate process from the worker for plug-and-play deployment.</p>
 */
@SpringBootApplication(scanBasePackages = "com.etl.controlplane")
public class ControlPlaneApiApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(ControlPlaneApiApplication.class);
		application.setDefaultProperties(Map.of("spring.main.web-application-type", "servlet"));
		application.run(args);
	}
}


