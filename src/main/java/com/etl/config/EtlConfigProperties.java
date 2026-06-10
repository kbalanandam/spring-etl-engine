package com.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
}


