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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

import static java.nio.file.Files.readString;

@Configuration
public class ConfigLoader {

	private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    /**
     * ConfigLoader is a Spring configuration class that loads YAML configuration
     * files for source, target, and processor configurations. It uses Jackson's
     * ObjectMapper to read the YAML files and convert them into Java objects.
     */

    @Bean
    SourceWrapper sourceWrapper() {
		
		logger.info("Loading source configuration from YAML file");

		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			return mapper.readValue(new File("D:/ETLDemo/config/source-config.yaml"), SourceWrapper.class);

		} catch (Exception e) {
			throw new ConfigException("Failed to load source config YAML", e);
		}
	}

	@Bean
	TargetWrapper targetWrapper() {
		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			File yamlFile = new File("D:/ETLDemo/config/target-config.yaml");
			return mapper.readValue(yamlFile, TargetWrapper.class);
		} catch (Exception e) {

			throw new ConfigException("Failed to load target config YAML", e);
		}
	}

	@Bean
	public ProcessorConfig processorConfig() {
		try {
			ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			mapper.findAndRegisterModules();
            File yamlFile = new File("D:/ETLDemo/config/processor-config.yaml");
            logger.debug("YAML content:\n{}", readString(yamlFile.toPath()));
			ProcessorConfig config = mapper.readValue(yamlFile, ProcessorConfig.class);

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


}
