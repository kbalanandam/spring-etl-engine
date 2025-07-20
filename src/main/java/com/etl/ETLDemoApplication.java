package com.etl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.etl")
public class ETLDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ETLDemoApplication.class, args);
	}

}
