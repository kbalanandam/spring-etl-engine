package com.etl.config;

import com.etl.config.relational.RelationalConnectionConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed binding for selected-job and demo-fallback config paths.
 *
 * <p>Normal runtime should prefer explicit {@code etl.config.job}. The direct
 * {@code source}/{@code target}/{@code processor} defaults are compatibility
 * values used only when demo fallback is explicitly enabled.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "etl.config")
public class EtlConfigProperties {

    private String source = "src/main/resources/source-config.yaml";
    private String target = "src/main/resources/target-config.yaml";
    private String processor = "src/main/resources/processor-config.yaml";
    private String job = "";
    private boolean allowDemoFallback = false;
    private Relational relational = new Relational();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getProcessor() {
        return processor;
    }

    public void setProcessor(String processor) {
        this.processor = processor;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public boolean isAllowDemoFallback() {
        return allowDemoFallback;
    }

    public void setAllowDemoFallback(boolean allowDemoFallback) {
        this.allowDemoFallback = allowDemoFallback;
    }

    public Relational getRelational() {
        return relational;
    }

    public void setRelational(Relational relational) {
        this.relational = relational == null ? new Relational() : relational;
    }

    public static class Relational {
        private Map<String, RelationalConnectionConfig> connections = new LinkedHashMap<>();

        public Map<String, RelationalConnectionConfig> getConnections() {
            return connections;
        }

        public void setConnections(Map<String, RelationalConnectionConfig> connections) {
            this.connections = connections == null ? new LinkedHashMap<>() : new LinkedHashMap<>(connections);
        }
    }
}


