package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPlaneFlywayMigrationScriptsTest {

    @Test
    void mysqlControlplaneFlywayBaselineExistsWithRetainedSchemaAndSeedData() throws IOException {
        String sql = normalizedSql(Path.of("src", "main", "resources", "db", "migration", "controlplane", "mysql", "V1__controlplane_baseline.sql"));

        assertAll(
                () -> assertTrue(sql.contains("create table controlplane_schedule")),
                () -> assertTrue(sql.contains("create table controlplane_trigger_source")),
                () -> assertTrue(sql.contains("create table controlplane_run_summary")),
                () -> assertTrue(sql.contains("create table controlplane_log_checkpoint")),
                () -> assertTrue(sql.contains("insert into controlplane_trigger_source")),
                () -> assertTrue(sql.contains("insert into controlplane_pk_sequence"))
        );
    }

    @Test
    void sqlServerControlplaneFlywayBaselineExistsWithRetainedSchemaAndSeedData() throws IOException {
        String sql = normalizedSql(Path.of("src", "main", "resources", "db", "migration", "controlplane", "mssql", "V1__controlplane_baseline.sql"));

        assertAll(
                () -> assertTrue(sql.contains("create table dbo.controlplane_schedule")),
                () -> assertTrue(sql.contains("create table dbo.controlplane_trigger_source")),
                () -> assertTrue(sql.contains("create table dbo.controlplane_run_summary")),
                () -> assertTrue(sql.contains("create table dbo.controlplane_log_checkpoint")),
                () -> assertTrue(sql.contains("insert into dbo.controlplane_trigger_source")),
                () -> assertTrue(sql.contains("insert into dbo.controlplane_pk_sequence"))
        );
    }

    @Test
    void vendorSpringBatchFlywayBaselinesExist() throws IOException {
        String mysqlSql = normalizedSql(Path.of("src", "main", "resources", "db", "migration", "controlplane", "mysql", "V1_1__spring_batch_metadata.sql"));
        String mssqlSql = normalizedSql(Path.of("src", "main", "resources", "db", "migration", "controlplane", "mssql", "V1_1__spring_batch_metadata.sql"));

        assertAll(
                () -> assertTrue(mysqlSql.contains("batch_job_instance")),
                () -> assertTrue(mysqlSql.contains("batch_step_execution")),
                () -> assertTrue(mysqlSql.contains("batch_job_seq")),
                () -> assertTrue(mssqlSql.contains("dbo.batch_job_instance")),
                () -> assertTrue(mssqlSql.contains("batch_job_seq")),
                () -> assertTrue(mssqlSql.contains("batch_step_execution_seq"))
        );
    }

    private String normalizedSql(Path path) throws IOException {
        return Files.readString(path)
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}


