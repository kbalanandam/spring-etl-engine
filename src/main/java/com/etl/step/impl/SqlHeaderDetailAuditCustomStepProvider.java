package com.etl.step.impl;

import com.etl.config.EtlConfigProperties;
import com.etl.config.job.JobConfig;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.config.relational.RelationalDataSourceFactory;
import com.etl.step.CustomStepBinding;
import com.etl.step.CustomStepFailureFinalizer;
import com.etl.step.CustomStepHandler;
import com.etl.step.CustomStepProvider;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Header/detail SQL audit provider used by preserved A7/A7b relational proof bundles.
 */
@Component
@CustomStepBinding(type = "sqlHeaderDetailAudit")
public class SqlHeaderDetailAuditCustomStepProvider implements CustomStepProvider {

    private static final String CONTEXT_RUN_ID_KEY = "custom.sqlHeaderDetailAudit.runId";
    private final Map<String, RelationalConnectionConfig> relationalConnections;

    public SqlHeaderDetailAuditCustomStepProvider() {
        this(new EtlConfigProperties());
    }

    @Autowired
    public SqlHeaderDetailAuditCustomStepProvider(EtlConfigProperties etlConfigProperties) {
        Map<String, RelationalConnectionConfig> configured = etlConfigProperties == null || etlConfigProperties.getRelational() == null
                ? Map.of()
                : etlConfigProperties.getRelational().getConnections();
        this.relationalConnections = configured == null ? Map.of() : Map.copyOf(configured);
    }

    @Override
    public CustomStepHandler createHandler(JobConfig.CustomStepConfig config) {
        StepConfig stepConfig = StepConfig.from(config, relationalConnections);
        return (contribution, chunkContext) -> {
            switch (stepConfig.action()) {
                case "start" -> startHeader(contribution, stepConfig);
                case "load_details" -> loadDetails(contribution, stepConfig);
                case "complete" -> completeHeader(contribution, stepConfig);
                default -> throw new IllegalArgumentException("Unsupported sqlHeaderDetailAudit action '" + stepConfig.action() + "'.");
            }
            return RepeatStatus.FINISHED;
        };
    }

    @Override
    public CustomStepFailureFinalizer createFailureFinalizer(JobConfig.CustomStepConfig config) {
        StepConfig stepConfig = StepConfig.from(config, relationalConnections);
        return (jobExecution, stepName, customConfig) -> markFailure(jobExecution, stepConfig);
    }

    private void startHeader(StepContribution contribution, StepConfig stepConfig) throws SQLException, ClassNotFoundException {
        JobExecution jobExecution = contribution.getStepExecution().getJobExecution();
        try (Connection connection = open(stepConfig)) {
            executePrepareSql(connection, stepConfig.prepareSql());
            String sql = "INSERT INTO " + qualified(stepConfig.schema(), stepConfig.headerTable())
                    + " (scenario_name, status, updated_at, detail_count) "
                    + "VALUES (?, ?, NULL, NULL)";
            try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, scenarioName(jobExecution));
                statement.setString(2, stepConfig.inProgressStatus());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new IllegalStateException("sqlHeaderDetailAudit did not receive a generated run_id from header insert.");
                    }
                    long runId = keys.getLong(1);
                    jobExecution.getExecutionContext().putLong(CONTEXT_RUN_ID_KEY, runId);
                }
            }
        }
    }

    private void loadDetails(StepContribution contribution, StepConfig stepConfig) throws SQLException, ClassNotFoundException {
        JobExecution jobExecution = contribution.getStepExecution().getJobExecution();
        long runId = requiredRunId(jobExecution);
        List<DetailRow> rows = detailRows(stepConfig.detailRows());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("sqlHeaderDetailAudit action 'load_details' requires custom.config.detailRows[].");
        }

        try (Connection connection = open(stepConfig)) {
            String sql = "INSERT INTO " + qualified(stepConfig.schema(), stepConfig.detailTable())
                    + " (run_id, customer_id, customer_name, customer_email) "
                    + "VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int inserted = 0;
                for (DetailRow row : rows) {
                    statement.setLong(1, runId);
                    statement.setInt(2, row.customerId());
                    statement.setString(3, row.customerName());
                    statement.setString(4, row.customerEmail());
                    statement.addBatch();
                    inserted++;
                }
                statement.executeBatch();
                if (stepConfig.failAfterInsert()) {
                    throw new IllegalStateException("sqlHeaderDetailAudit forced failure after detail insert for negative-flow verification.");
                }
                jobExecution.getExecutionContext().putInt(stepConfig.insertedCountKey(), inserted);
            }
        }
    }

    private void completeHeader(StepContribution contribution, StepConfig stepConfig) throws SQLException, ClassNotFoundException {
        JobExecution jobExecution = contribution.getStepExecution().getJobExecution();
        long runId = requiredRunId(jobExecution);
        Integer insertedCount = resolvedDetailCount(jobExecution, stepConfig);
        try (Connection connection = open(stepConfig)) {
            String sql = "UPDATE " + qualified(stepConfig.schema(), stepConfig.headerTable())
                    + " SET status = ?, updated_at = CURRENT_TIMESTAMP, detail_count = ? WHERE run_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, stepConfig.successStatus());
                if (insertedCount == null) {
                    statement.setNull(2, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(2, insertedCount);
                }
                statement.setLong(3, runId);
                statement.executeUpdate();
            }
        }
    }

    private void markFailure(JobExecution jobExecution, StepConfig stepConfig) throws SQLException, ClassNotFoundException {
        if (jobExecution == null || !jobExecution.getExecutionContext().containsKey(CONTEXT_RUN_ID_KEY)) {
            return;
        }
        long runId = requiredRunId(jobExecution);
        Integer resolvedDetailCount = resolvedDetailCount(jobExecution, stepConfig);
        int detailCount = resolvedDetailCount == null ? 0 : resolvedDetailCount;
        try (Connection connection = open(stepConfig)) {
            String sql = "UPDATE " + qualified(stepConfig.schema(), stepConfig.headerTable())
                    + " SET status = ?, updated_at = CURRENT_TIMESTAMP, detail_count = ? WHERE run_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, stepConfig.failedStatus());
                statement.setInt(2, detailCount);
                statement.setLong(3, runId);
                statement.executeUpdate();
            }
        }
    }

    private int detailCount(StepConfig stepConfig, long runId) throws SQLException, ClassNotFoundException {
        try (Connection connection = open(stepConfig)) {
            String sql = "SELECT COUNT(*) FROM " + qualified(stepConfig.schema(), stepConfig.detailTable()) + " WHERE run_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, runId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(1) : 0;
                }
            }
        }
    }

    private Integer resolvedDetailCount(JobExecution jobExecution, StepConfig stepConfig) throws SQLException, ClassNotFoundException {
        if (jobExecution == null) {
            return null;
        }
        if (jobExecution.getExecutionContext().containsKey(stepConfig.insertedCountKey())) {
            return jobExecution.getExecutionContext().getInt(stepConfig.insertedCountKey());
        }
        Integer stepWriteCount = stepWriteCount(jobExecution, stepConfig.countStepName());
        if (stepWriteCount != null) {
            return stepWriteCount;
        }
        if (jobExecution.getExecutionContext().containsKey(CONTEXT_RUN_ID_KEY)) {
            return detailCount(stepConfig, requiredRunId(jobExecution));
        }
        return null;
    }

    private Integer stepWriteCount(JobExecution jobExecution, String countStepName) {
        if (jobExecution == null || countStepName == null || countStepName.isBlank()) {
            return null;
        }
        return jobExecution.getStepExecutions().stream()
                .filter(stepExecution -> countStepName.equals(stepExecution.getStepName()))
                .max(Comparator.comparingLong(stepExecution -> stepExecution.getId()))
                .map(StepExecution::getWriteCount)
                .map(Long::intValue)
                .orElse(null);
    }

    private void executePrepareSql(Connection connection, List<String> prepareSql) throws SQLException {
        for (String sql : prepareSql) {
            if (sql == null || sql.isBlank()) {
                continue;
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.execute();
            }
        }
    }

    private long requiredRunId(JobExecution jobExecution) {
        if (jobExecution == null || !jobExecution.getExecutionContext().containsKey(CONTEXT_RUN_ID_KEY)) {
            throw new IllegalStateException("sqlHeaderDetailAudit requires a previously created header run_id in execution context.");
        }
        return jobExecution.getExecutionContext().getLong(CONTEXT_RUN_ID_KEY);
    }

    private String scenarioName(JobExecution jobExecution) {
        if (jobExecution == null || jobExecution.getJobParameters() == null) {
            return "unknown-scenario";
        }
        String scenario = jobExecution.getJobParameters().getString("scenario", "unknown-scenario");
        return scenario == null || scenario.isBlank() ? "unknown-scenario" : scenario;
    }

    private Connection open(StepConfig stepConfig) throws SQLException, ClassNotFoundException {
        if (!stepConfig.driverClassName().isBlank()) {
            Class.forName(stepConfig.driverClassName());
        }
        return DriverManager.getConnection(stepConfig.jdbcUrl(), stepConfig.username(), stepConfig.password());
    }

    private String qualified(String schema, String table) {
        return (schema == null || schema.isBlank()) ? table : schema + "." + table;
    }

    private List<DetailRow> detailRows(List<Map<String, Object>> configuredRows) {
        List<DetailRow> rows = new ArrayList<>();
        for (Map<String, Object> row : configuredRows) {
            int customerId = integerValue(row.get("customerId"), "detailRows[].customerId");
            String customerName = requiredText(row.get("customerName"), "detailRows[].customerName");
            String customerEmail = optionalText(row.get("customerEmail"));
            rows.add(new DetailRow(customerId, customerName, customerEmail));
        }
        return rows;
    }

    private int integerValue(Object value, String property) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        throw new IllegalArgumentException("Missing required numeric value for " + property + ".");
    }

    private String requiredText(Object value, String property) {
        String text = optionalText(value);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Missing required text value for " + property + ".");
        }
        return text;
    }

    private String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private record DetailRow(int customerId, String customerName, String customerEmail) {
    }

    private record StepConfig(
            String action,
            String connectionRef,
            String jdbcUrl,
            String username,
            String password,
            String driverClassName,
            String schema,
            String headerTable,
            String detailTable,
            String inProgressStatus,
            String successStatus,
            String failedStatus,
            boolean failAfterInsert,
            String insertedCountKey,
            String countStepName,
            List<String> prepareSql,
            List<Map<String, Object>> detailRows
    ) {
        static StepConfig from(JobConfig.CustomStepConfig config,
                               Map<String, RelationalConnectionConfig> relationalConnections) {
            if (config == null || config.getConfig() == null) {
                throw new IllegalArgumentException("sqlHeaderDetailAudit requires custom.config.");
            }
            Map<String, Object> values = config.getConfig();
            String action = required(values, "action").toLowerCase(Locale.ROOT);
            String connectionRef = optional(values, "connectionRef");
            ConnectionSettings connectionSettings = resolveConnectionSettings(values, connectionRef, relationalConnections);
            String schema = optional(values, "schema");
            String headerTable = optional(values, "headerTable");
            if (headerTable == null) {
                headerTable = "etl_run_header";
            }
            String detailTable = optional(values, "detailTable");
            if (detailTable == null) {
                detailTable = "etl_run_detail";
            }
            String inProgressStatus = optional(values, "inProgressStatus");
            if (inProgressStatus == null) {
                inProgressStatus = "IN_PROGRESS";
            }
            String successStatus = optional(values, "successStatus");
            if (successStatus == null) {
                successStatus = "SUCCESS";
            }
            String failedStatus = optional(values, "failedStatus");
            if (failedStatus == null) {
                failedStatus = "FAILED";
            }
            boolean failAfterInsert = booleanValue(values.get("failAfterInsert"));
            String insertedCountKey = optional(values, "insertedCountKey");
            if (insertedCountKey == null) {
                insertedCountKey = "custom.sqlHeaderDetailAudit.insertedCount";
            }
            String countStepName = optional(values, "countStepName");
            List<String> prepareSql = listOfStrings(values.get("prepareSql"));
            List<Map<String, Object>> detailRows = listOfMaps(values.get("detailRows"));

            return new StepConfig(
                    action,
                    connectionRef == null ? "" : connectionRef,
                    connectionSettings.jdbcUrl(),
                    connectionSettings.username(),
                    connectionSettings.password(),
                    connectionSettings.driverClassName(),
                    schema == null ? "" : schema,
                    headerTable,
                    detailTable,
                    inProgressStatus,
                    successStatus,
                    failedStatus,
                    failAfterInsert,
                    insertedCountKey,
                    countStepName == null ? "" : countStepName,
                    prepareSql,
                    detailRows
            );
        }

        private static ConnectionSettings resolveConnectionSettings(Map<String, Object> values,
                                                                    String connectionRef,
                                                                    Map<String, RelationalConnectionConfig> relationalConnections) {
            String jdbcUrl = optional(values, "jdbcUrl");
            String username = optional(values, "username");
            boolean passwordProvided = values.containsKey("password");
            String password = passwordProvided ? requiredAllowBlank(values, "password") : null;
            String driverClassName = optional(values, "driverClassName");

            if (connectionRef == null) {
                return new ConnectionSettings(
                        required(values, "jdbcUrl"),
                        required(values, "username"),
                        requiredAllowBlank(values, "password"),
                        driverClassName == null ? "" : driverClassName
                );
            }

            if (jdbcUrl != null || username != null || passwordProvided || driverClassName != null) {
                throw new IllegalArgumentException("sqlHeaderDetailAudit custom.config.connectionRef cannot be combined with jdbcUrl/username/password/driverClassName.");
            }

            RelationalConnectionConfig connection = relationalConnections == null ? null : relationalConnections.get(connectionRef);
            if (connection == null) {
                throw new IllegalArgumentException("sqlHeaderDetailAudit custom.config.connectionRef '" + connectionRef
                        + "' is not configured in etl.config.relational.connections.*.");
            }

            try {
                connection.validate();
                String resolvedJdbcUrl = RelationalDataSourceFactory.resolveJdbcUrl(connection);
                String resolvedUsername = connection.resolveUsername();
                String resolvedPassword = connection.resolvePassword();
                String resolvedDriverClassName = RelationalDataSourceFactory.resolveDriverClassName(connection);
                return new ConnectionSettings(
                        resolvedJdbcUrl,
                        resolvedUsername,
                        resolvedPassword,
                        resolvedDriverClassName == null ? "" : resolvedDriverClassName
                );
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("sqlHeaderDetailAudit custom.config.connectionRef '" + connectionRef
                        + "' is invalid: " + e.getMessage(), e);
            }
        }

        private static String required(Map<String, Object> values, String key) {
            String value = optional(values, key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("sqlHeaderDetailAudit custom.config." + key + " is required.");
            }
            return value;
        }

        private static String requiredAllowBlank(Map<String, Object> values, String key) {
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("sqlHeaderDetailAudit custom.config." + key + " is required.");
            }
            Object value = values.get(key);
            return value == null ? "" : String.valueOf(value);
        }

        private static String optional(Map<String, Object> values, String key) {
            Object value = values.get(key);
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value).trim();
            return text.isBlank() ? null : text;
        }

        private static boolean booleanValue(Object value) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text) {
                return Boolean.parseBoolean(text.trim());
            }
            return false;
        }

        private static List<String> listOfStrings(Object value) {
            if (value == null) {
                return List.of();
            }
            if (!(value instanceof List<?> items)) {
                throw new IllegalArgumentException("sqlHeaderDetailAudit custom.config.prepareSql must be a list.");
            }
            List<String> statements = new ArrayList<>();
            for (Object item : items) {
                if (item == null) {
                    continue;
                }
                statements.add(String.valueOf(item));
            }
            return List.copyOf(statements);
        }

        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> listOfMaps(Object value) {
            if (value == null) {
                return List.of();
            }
            if (!(value instanceof List<?> items)) {
                throw new IllegalArgumentException("sqlHeaderDetailAudit custom.config.detailRows must be a list.");
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("sqlHeaderDetailAudit custom.config.detailRows entries must be objects.");
                }
                rows.add((Map<String, Object>) map);
            }
            return List.copyOf(rows);
        }

        private record ConnectionSettings(String jdbcUrl, String username, String password, String driverClassName) {
        }
    }
}







