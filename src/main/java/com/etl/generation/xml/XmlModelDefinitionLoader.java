package com.etl.generation.xml;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
/**
 * Loads standalone XML model definitions from YAML.
 */
public class XmlModelDefinitionLoader {
    private final ObjectMapper mapper;
    public XmlModelDefinitionLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndRegisterModules();
    }
    public XmlModelDefinition load(Path path) throws IOException {
        return mapper.readValue(path.toFile(), XmlModelDefinition.class);
    }
}
