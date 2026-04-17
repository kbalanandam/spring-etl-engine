package com.etl.config;

import com.etl.config.exception.ConfigException;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static java.nio.file.Files.readString;

@Configuration
public class ConfigLoader {

	private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

	@Value("${etl.config.source:C:/ETLDemo/config/source-config.yaml}")
	private String sourceConfigPath;

	@Value("${etl.config.target:C:/ETLDemo/config/target-config.yaml}")
	private String targetConfigPath;

	@Value("${etl.config.processor:C:/ETLDemo/config/processor-config.yaml}")
	private String processorConfigPath;

    /**
     * ConfigLoader is a Spring configuration class that loads YAML configuration
     * files for source, target, and processor configurations. It uses Jackson's
     * ObjectMapper to read the YAML files and convert them into Java objects.
     */

    @Bean
    SourceWrapper sourceWrapper() {
		try {
			return loadYamlConfig(sourceConfigPath, "source-config.yaml", SourceWrapper.class);

		} catch (Exception e) {
			throw new ConfigException("Failed to load source config YAML", e);
		}
	}

	@Bean
	TargetWrapper targetWrapper() {
		try {
			return loadYamlConfig(targetConfigPath, "target-config.yaml", TargetWrapper.class);
		} catch (Exception e) {

			throw new ConfigException("Failed to load target config YAML", e);
		}
	}

	@Bean
	public ProcessorConfig processorConfig() {
		try {
			ObjectMapper mapper = buildYamlMapper();
			ProcessorConfig config = loadYamlConfig(processorConfigPath, "processor-config.yaml", ProcessorConfig.class, mapper);

			// --- Validation step ---

			if (config.getMappings() == null || config.getMappings().isEmpty()) {
				throw new IllegalStateException("No entity mappings found in processor YAML");
			}

			for (ProcessorConfig.EntityMapping em : config.getMappings()) {
				for (int i = 0; i < config.getMappings().size(); i++) {
					ProcessorConfig.EntityMapping m = config.getMappings().get(i);
                    logger.debug("Mapping {}: source={}, target={}", i, m.getSource(), m.getTarget());
				}
                logger.debug("Validating EntityMapping size: {}", config.getMappings().size());
				if (em.getSource() == null || em.getSource().isEmpty()) {
					throw new IllegalStateException("EntityMapping missing 'source' property: " + em);
				}
				if (em.getTarget() == null || em.getTarget().isEmpty()) {
					throw new IllegalStateException("EntityMapping missing 'target' property: " + em);
				}
				if (em.getFields() == null || em.getFields().isEmpty()) {
					throw new IllegalStateException("EntityMapping for source=" + em.getSource() + " and target=" + em.getTarget() + " has no field mappings");
				}

				// Optional: validate individual field mappings
				for (ProcessorConfig.FieldMapping fm : em.getFields()) {
					if (fm.getFrom() == null || fm.getFrom().isEmpty()) {
						throw new IllegalStateException("FieldMapping missing 'from' in entity " + em.getSource());
					}
					if (fm.getTo() == null || fm.getTo().isEmpty()) {
						throw new IllegalStateException("FieldMapping missing 'to' in entity " + em.getSource());
					}
				}
			}
			logger.info("Processor configuration loaded and validated successfully from YAML");
			return config;
		} catch (Exception e) {
			throw new ConfigException("Failed to load or validate processor config YAML", e);
		}
	}

	private ObjectMapper buildYamlMapper() {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.findAndRegisterModules();
		return mapper;
	}

	private <T> T loadYamlConfig(String configuredPath, String fallbackClasspathResource, Class<T> targetType) throws IOException {
		return loadYamlConfig(configuredPath, fallbackClasspathResource, targetType, buildYamlMapper());
	}

	private <T> T loadYamlConfig(String configuredPath, String fallbackClasspathResource, Class<T> targetType, ObjectMapper mapper) throws IOException {
		File externalFile = new File(configuredPath);
		if (externalFile.exists() && externalFile.isFile()) {
			logger.info("Loading {} from external YAML file: {}", targetType.getSimpleName(), configuredPath);
			if (logger.isDebugEnabled()) {
				logger.debug("YAML content from {}:\n{}", configuredPath, readString(externalFile.toPath()));
			}
			return mapper.readValue(externalFile, targetType);
		}

		logger.warn("Configured YAML file not found at {}. Falling back to classpath resource: {}", configuredPath, fallbackClasspathResource);
		ClassPathResource classPathResource = new ClassPathResource(fallbackClasspathResource);
		if (!classPathResource.exists()) {
			throw new IOException("Fallback classpath resource not found: " + fallbackClasspathResource);
		}

		try (InputStream inputStream = classPathResource.getInputStream()) {
			return mapper.readValue(inputStream, targetType);
		}
	}


}
