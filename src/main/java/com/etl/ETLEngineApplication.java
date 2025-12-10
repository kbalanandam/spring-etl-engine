package com.etl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.etl")
public class ETLEngineApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(ETLEngineApplication.class, args);
		int exitCode = SpringApplication.exit(context, () -> 0);
		System.exit(exitCode);
	}

}
