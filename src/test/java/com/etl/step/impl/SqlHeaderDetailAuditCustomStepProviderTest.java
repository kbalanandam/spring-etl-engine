package com.etl.step.impl;

import com.etl.config.EtlConfigProperties;
import com.etl.config.job.JobConfig;
import com.etl.config.relational.RelationalConnectionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlHeaderDetailAuditCustomStepProviderTest {

    private static final String JDBC_URL = "jdbc:h2:mem:sql_header_detail_provider_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

    private final SqlHeaderDetailAuditCustomStepProvider provider = new SqlHeaderDetailAuditCustomStepProvider();

    @AfterEach
    void cleanup() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS dbo.etl_run_detail");
            statement.execute("DROP TABLE IF EXISTS dbo.etl_run_header");
        }
    }

    @Test
    void startLoadCompletePersistsHeaderAndDetailsWithSuccessStatus() throws Exception {
        setupSchema();
        JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution();
        StepContribution startContribution = contribution(jobExecution, "start");
        StepContribution loadContribution = contribution(jobExecution, "load-details");
        StepContribution completeContribution = contribution(jobExecution, "complete");

        provider.createHandler(config("start", false, List.of())).execute(startContribution, null);
        provider.createHandler(config("load_details", false, List.of(
                Map.of("customerId", 1001, "customerName", "Alice", "customerEmail", "alice@example.com"),
                Map.of("customerId", 1002, "customerName", "Bob", "customerEmail", "bob@example.com")
        ))).execute(loadContribution, null);
        provider.createHandler(config("complete", false, List.of())).execute(completeContribution, null);

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet header = statement.executeQuery("SELECT status, detail_count FROM dbo.etl_run_header")) {
                header.next();
                assertEquals("SUCCESS", header.getString("status"));
                assertEquals(2, header.getInt("detail_count"));
            }
            try (ResultSet details = statement.executeQuery("SELECT COUNT(*) FROM dbo.etl_run_detail")) {
                details.next();
                assertEquals(2, details.getInt(1));
            }
        }
    }

    @Test
    void failureFinalizerMarksHeaderFailedAfterLoadFailure() throws Exception {
        setupSchema();
        JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution();
        StepContribution startContribution = contribution(jobExecution, "start");
        StepContribution loadContribution = contribution(jobExecution, "load-details");

        JobConfig.CustomStepConfig stepConfig = config("load_details", true, List.of(
                Map.of("customerId", 1001, "customerName", "Alice", "customerEmail", "alice@example.com")
        ));

        provider.createHandler(config("start", false, List.of())).execute(startContribution, null);
        assertThrows(IllegalStateException.class,
                () -> provider.createHandler(stepConfig).execute(loadContribution, null));
        provider.createFailureFinalizer(stepConfig).onFailure(jobExecution, "load-details", stepConfig);

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet header = statement.executeQuery("SELECT status, detail_count FROM dbo.etl_run_header")) {
                header.next();
                assertEquals("FAILED", header.getString("status"));
                assertEquals(1, header.getInt("detail_count"));
            }
        }
    }

    @Test
    void completeUsesNamedStandardStepWriteCountForRealFileLoadFlow() throws Exception {
        setupSchema();
        JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution();
        StepContribution startContribution = contribution(jobExecution, "header-start");
        StepContribution completeContribution = contribution(jobExecution, "header-complete");
        StepExecution fileLoadStepExecution = MetaDataInstanceFactory.createStepExecution(jobExecution, "customers-file-load", 2L);
        fileLoadStepExecution.setWriteCount(3);

        provider.createHandler(config("start", false, List.of(), "", List.of())).execute(startContribution, null);
        provider.createHandler(config("complete", false, List.of(), "customers-file-load", List.of())).execute(completeContribution, null);

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet header = statement.executeQuery("SELECT status, detail_count FROM dbo.etl_run_header")) {
                header.next();
                assertEquals("SUCCESS", header.getString("status"));
                assertEquals(3, header.getInt("detail_count"));
            }
        }
    }

    @Test
    void failureFinalizerUsesNamedStandardStepWriteCountWhenStandardLoadFails() throws Exception {
        setupSchema();
        JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution();
        StepContribution startContribution = contribution(jobExecution, "header-start");
        StepExecution fileLoadStepExecution = MetaDataInstanceFactory.createStepExecution(jobExecution, "customers-file-load", 2L);
        fileLoadStepExecution.setWriteCount(2);
        JobConfig.CustomStepConfig finalizerConfig = config("complete", false, List.of(), "customers-file-load", List.of());

        provider.createHandler(config("start", false, List.of(), "", List.of())).execute(startContribution, null);
        provider.createFailureFinalizer(finalizerConfig).onFailure(jobExecution, "header-complete", finalizerConfig);

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet header = statement.executeQuery("SELECT status, detail_count FROM dbo.etl_run_header")) {
                header.next();
                assertEquals("FAILED", header.getString("status"));
                assertEquals(2, header.getInt("detail_count"));
            }
        }
    }

    @Test
    void startAndCompleteSupportConnectionRefForCustomStepJdbcSettings() throws Exception {
        setupSchema();
        SqlHeaderDetailAuditCustomStepProvider connectionRefProvider = new SqlHeaderDetailAuditCustomStepProvider(propertiesWithConnection("sqlserver-main"));
        JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution();
        StepContribution startContribution = contribution(jobExecution, "header-start");
        StepContribution completeContribution = contribution(jobExecution, "header-complete");

        connectionRefProvider.createHandler(configWithConnectionRef("start", "sqlserver-main", false, List.of(), "", List.of()))
                .execute(startContribution, null);
        connectionRefProvider.createHandler(configWithConnectionRef("complete", "sqlserver-main", false, List.of(), "", List.of()))
                .execute(completeContribution, null);

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet header = statement.executeQuery("SELECT status FROM dbo.etl_run_header")) {
                header.next();
                assertEquals("SUCCESS", header.getString("status"));
            }
        }
    }

    @Test
    void connectionRefFailsFastWhenRegistryEntryIsMissing() {
        JobConfig.CustomStepConfig config = configWithConnectionRef("start", "missing-connection", false, List.of(), "", List.of());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> provider.createHandler(config)
        );
        assertEquals("sqlHeaderDetailAudit custom.config.connectionRef 'missing-connection' is not configured in etl.config.relational.connections.*.", ex.getMessage());
    }

    private StepContribution contribution(JobExecution jobExecution, String stepName) {
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution(jobExecution, stepName, 1L);
        return new StepContribution(stepExecution);
    }

    private JobConfig.CustomStepConfig config(String action, boolean failAfterInsert, List<Map<String, Object>> detailRows) {
        return config(action, failAfterInsert, detailRows, "", List.of());
    }

    private JobConfig.CustomStepConfig config(String action,
                                              boolean failAfterInsert,
                                              List<Map<String, Object>> detailRows,
                                              String countStepName,
                                              List<String> prepareSql) {
        JobConfig.CustomStepConfig config = new JobConfig.CustomStepConfig();
        config.setType("sqlHeaderDetailAudit");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("action", action);
        values.put("jdbcUrl", JDBC_URL);
        values.put("username", "sa");
        values.put("password", "");
        values.put("driverClassName", "org.h2.Driver");
        values.put("schema", "dbo");
        values.put("headerTable", "etl_run_header");
        values.put("detailTable", "etl_run_detail");
        values.put("failAfterInsert", failAfterInsert);
        values.put("countStepName", countStepName);
        values.put("prepareSql", prepareSql);
        values.put("detailRows", detailRows);
        config.setConfig(values);
        return config;
    }

    private JobConfig.CustomStepConfig configWithConnectionRef(String action,
                                                              String connectionRef,
                                                              boolean failAfterInsert,
                                                              List<Map<String, Object>> detailRows,
                                                              String countStepName,
                                                              List<String> prepareSql) {
        JobConfig.CustomStepConfig config = new JobConfig.CustomStepConfig();
        config.setType("sqlHeaderDetailAudit");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("action", action);
        values.put("connectionRef", connectionRef);
        values.put("schema", "dbo");
        values.put("headerTable", "etl_run_header");
        values.put("detailTable", "etl_run_detail");
        values.put("failAfterInsert", failAfterInsert);
        values.put("countStepName", countStepName);
        values.put("prepareSql", prepareSql);
        values.put("detailRows", detailRows);
        config.setConfig(values);
        return config;
    }

    private EtlConfigProperties propertiesWithConnection(String connectionName) {
        EtlConfigProperties properties = new EtlConfigProperties();
        EtlConfigProperties.Relational relational = new EtlConfigProperties.Relational();
        RelationalConnectionConfig connection = new RelationalConnectionConfig();
        connection.setVendor("h2");
        connection.setJdbcUrl(JDBC_URL);
        connection.setUsername("sa");
        connection.setPassword("");
        connection.setDriverClassName("org.h2.Driver");
        relational.setConnections(Map.of(connectionName, connection));
        properties.setRelational(relational);
        return properties;
    }

    private void setupSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS dbo");
            statement.execute("DROP TABLE IF EXISTS dbo.etl_run_detail");
            statement.execute("DROP TABLE IF EXISTS dbo.etl_run_header");
            statement.execute("CREATE TABLE dbo.etl_run_header ("
                    + "run_id BIGINT IDENTITY PRIMARY KEY,"
                    + "scenario_name NVARCHAR(128) NOT NULL,"
                    + "status NVARCHAR(32) NOT NULL,"
                    + "created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP(),"
                    + "updated_at DATETIME2 NULL,"
                    + "detail_count INT NULL)");
            statement.execute("CREATE TABLE dbo.etl_run_detail ("
                    + "detail_id BIGINT IDENTITY PRIMARY KEY,"
                    + "run_id BIGINT NOT NULL,"
                    + "customer_id INT NOT NULL,"
                    + "customer_name NVARCHAR(255) NOT NULL,"
                    + "customer_email NVARCHAR(255) NULL,"
                    + "created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP(),"
                    + "CONSTRAINT FK_run_detail_header FOREIGN KEY (run_id) REFERENCES dbo.etl_run_header(run_id))");
        }
    }
}



