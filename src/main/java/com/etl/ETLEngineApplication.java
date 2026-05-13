package com.etl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot bootstrap entry point for the ETL runtime.
 *
 * <p>The application starts the container, resolves one selected runtime configuration,
 * launches the Spring Batch job, and then exits with the resulting process code. It is a
 * thin bootstrapping shell; operational behavior lives in the runtime/config beans rather
 * than in this class.</p>
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.etl")
public class ETLEngineApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(ETLEngineApplication.class, args);
		int exitCode = SpringApplication.exit(context, () -> 0);
		System.exit(exitCode);
	}

}
