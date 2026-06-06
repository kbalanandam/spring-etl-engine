package com.etl.controlplane.ui;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps Operator UI assets fresh between iterations where the same browser session stays open.
 */
@Configuration
public class OperatorUiWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/operator/**")
                .addResourceLocations("classpath:/static/operator/")
                .setCacheControl(CacheControl.noStore());
    }
}

