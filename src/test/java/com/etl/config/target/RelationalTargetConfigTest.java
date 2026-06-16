package com.etl.config.target;

import com.etl.config.ColumnConfig;
import com.etl.config.relational.RelationalConnectionConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelationalTargetConfigTest {

    @Test
    void validateAllowsConnectionRefWithoutInlineConnection() {
        RelationalTargetConfig config = relationalTargetConfig();
        config.setConnection(null);
        config.setConnectionRef("sqlserver-main");

        assertDoesNotThrow(config::validate);
    }

    @Test
    void validateRejectsWhenConnectionAndConnectionRefAreBothConfigured() {
        RelationalTargetConfig config = relationalTargetConfig();
        config.setConnectionRef("sqlserver-main");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, config::validate);
        assertEquals("Relational target must define exactly one of 'connection' or 'connectionRef'.", ex.getMessage());
    }

    private static RelationalTargetConfig relationalTargetConfig() {
        RelationalConnectionConfig connection = new RelationalConnectionConfig();
        connection.setVendor("h2");
        connection.setJdbcUrl("jdbc:h2:mem:relational_target_cfg_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        connection.setUsername("sa");
        connection.setPassword("");

        ColumnConfig id = new ColumnConfig();
        id.setName("id");
        id.setType("int");

        return new RelationalTargetConfig(
                "CustomersSql",
                "com.etl.model.target",
                List.of(id),
                connection,
                "customers",
                "dbo",
                "insert",
                100
        );
    }
}

