package com.etl.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApplicationDevProfileDatasourceTest {

    @Test
    void devProfileUsesSqliteForBatchMetadata() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application-dev.properties")) {
            assertNotNull(input, "application-dev.properties should be available on the classpath");
            properties.load(input);
        }

        assertEquals("jdbc:sqlite:./.etl-dev/etl-dev.db", properties.getProperty("spring.datasource.url"));
        assertEquals("org.sqlite.JDBC", properties.getProperty("spring.datasource.driver-class-name"));
        assertEquals("always", properties.getProperty("spring.batch.jdbc.initialize-schema"));
        assertEquals("always", properties.getProperty("spring.sql.init.mode"));
        assertEquals("classpath:org/springframework/batch/core/schema-sqlite.sql",
                properties.getProperty("spring.sql.init.schema-locations"));
    }
}

