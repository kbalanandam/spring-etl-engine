package com.etl.controlplane;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlPlanePersistenceContractGuardTest {

    @Test
    void allowsMemoryModeWithoutJdbcSettings() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "memory",
                "memory",
                "memory",
                "",
                "",
                ""
        );

        assertDoesNotThrow(guard::validateContract);
    }

    @Test
    void allowsJdbcMysqlContract() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "jdbc",
                "jdbc",
                "jdbc",
                "mysql",
                "jdbc:mysql://localhost:3306/etl_controlplane",
                "com.mysql.cj.jdbc.Driver"
        );

        assertDoesNotThrow(guard::validateContract);
    }

    @Test
    void allowsJdbcSqlServerContract() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "jdbc",
                "jdbc",
                "jdbc",
                "sqlserver",
                "jdbc:sqlserver://localhost:1433;databaseName=etl_controlplane",
                "com.microsoft.sqlserver.jdbc.SQLServerDriver"
        );

        assertDoesNotThrow(guard::validateContract);
    }

    @Test
    void allowsJdbcPostgresqlContract() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "jdbc",
                "jdbc",
                "jdbc",
                "postgresql",
                "jdbc:postgresql://localhost:5432/etl_controlplane",
                "org.postgresql.Driver"
        );

        assertDoesNotThrow(guard::validateContract);
    }

    @Test
    void allowsJdbcOracleContract() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "jdbc",
                "jdbc",
                "jdbc",
                "oracle",
                "jdbc:oracle:thin:@localhost:1521/XEPDB1",
                "oracle.jdbc.OracleDriver"
        );

        assertDoesNotThrow(guard::validateContract);
    }

    @Test
    void allowsJdbcSqliteContract() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "jdbc",
                "jdbc",
                "jdbc",
                "sqlite",
                "jdbc:sqlite:C:/tmp/controlplane.db",
                "org.sqlite.JDBC"
        );

        assertDoesNotThrow(guard::validateContract);
    }

    @Test
    void rejectsMixedPersistenceModes() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "memory",
                "jdbc",
                "memory",
                "mysql",
                "jdbc:mysql://localhost:3306/etl_controlplane",
                "com.mysql.cj.jdbc.Driver"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::validateContract);
        assertTrue(ex.getMessage().contains("Ambiguous control-plane persistence mode selection"));
    }

    @Test
    void rejectsUnsupportedVendorInJdbcMode() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "jdbc",
                "jdbc",
                "jdbc",
                "db2",
                "jdbc:db2://localhost:50000/sample",
                "com.ibm.db2.jcc.DB2Driver"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::validateContract);
        assertTrue(ex.getMessage().contains("Unsupported controlplane.db.vendor"));
    }

    @Test
    void rejectsJdbcModeWithoutDatasourceUrl() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "jdbc",
                "jdbc",
                "jdbc",
                "mysql",
                "",
                "com.mysql.cj.jdbc.Driver"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::validateContract);
        assertTrue(ex.getMessage().contains("controlplane.db.url"));
    }

    @Test
    void rejectsVendorJdbcUrlMismatch() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "jdbc",
                "jdbc",
                "jdbc",
                "oracle",
                "jdbc:mysql://localhost:3306/etl_controlplane",
                "oracle.jdbc.OracleDriver"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::validateContract);
        assertTrue(ex.getMessage().contains("Vendor/JDBC URL mismatch"));
    }

    @Test
    void rejectsVendorDriverMismatch() {
        ControlPlanePersistenceContractGuard guard = new ControlPlanePersistenceContractGuard(
                "jdbc",
                "jdbc",
                "jdbc",
                "postgres",
                "jdbc:postgresql://localhost:5432/etl_controlplane",
                "com.mysql.cj.jdbc.Driver"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::validateContract);
        assertTrue(ex.getMessage().contains("Vendor/driver mismatch"));
    }
}


