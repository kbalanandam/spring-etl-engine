package com.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Typed binding for batch execution thresholds.
 */
@Configuration
@ConfigurationProperties(prefix = "etl.chunk")
public class EtlBatchProperties {

    private int threshold = 10000;

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}

