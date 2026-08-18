package com.etl.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApplicationDevProfileDatasourceTest {

    @Test
    void devProfileUsesMysqlForBatchMetadata() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application-dev.properties")) {
            assertNotNull(input, "application-dev.properties should be available on the classpath");
            properties.load(input);
        }

        assertEquals("${ETL_DEV_MYSQL_URL:jdbc:mysql://localhost:3306/etl_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}", properties.getProperty("spring.datasource.url"));
        assertEquals("com.mysql.cj.jdbc.Driver", properties.getProperty("spring.datasource.driver-class-name"));
        assertEquals("${ETL_DEV_MYSQL_USERNAME:root}", properties.getProperty("spring.datasource.username"));
        assertEquals("${ETL_DEV_MYSQL_PASSWORD:}", properties.getProperty("spring.datasource.password"));
        assertEquals("10", properties.getProperty("spring.datasource.hikari.maximum-pool-size"));
        assertEquals("2", properties.getProperty("spring.datasource.hikari.minimum-idle"));
        assertEquals("always", properties.getProperty("spring.batch.jdbc.initialize-schema"));
        assertEquals("never", properties.getProperty("spring.sql.init.mode"));
    }
}
