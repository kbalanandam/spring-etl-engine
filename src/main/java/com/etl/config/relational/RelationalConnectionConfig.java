package com.etl.config.relational;

public class RelationalConnectionConfig {

    private static final String PLACEHOLDER_TOKEN_PATTERN = ".*<[^>]+>.*";

    private String vendor;
    private String jdbcUrl;
    private String host;
    private Integer port;
    private String database;
    private String schema;
    private String username;
    private String password;
    private String driverClassName;

  public String getVendor() {
    return vendor;
  }

  public void setVendor(String vendor) {
    this.vendor = vendor;
  }

  public String getJdbcUrl() {
    return jdbcUrl;
  }

  public void setJdbcUrl(String jdbcUrl) {
    this.jdbcUrl = jdbcUrl;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(String database) {
    this.database = database;
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getDriverClassName() {
    return driverClassName;
  }

  public void setDriverClassName(String driverClassName) {
    this.driverClassName = driverClassName;
  }

    public DatabaseVendor getResolvedVendor() {
        return DatabaseVendor.fromString(vendor);
    }

    public void validate() {
        if (vendor == null || vendor.isBlank()) {
            throw new IllegalArgumentException("Relational connection vendor must be provided.");
        }

        DatabaseVendor resolvedVendor = getResolvedVendor();

        if (port != null && port <= 0) {
            throw new IllegalArgumentException("Relational connection port must be greater than zero when provided.");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Relational connection username must be provided.");
        }

        rejectPlaceholder("jdbcUrl", jdbcUrl);
        rejectPlaceholder("host", host);
        rejectPlaceholder("database", database);
        rejectPlaceholder("username", username);
        rejectPlaceholder("password", password);

        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            return;
        }

        if (resolvedVendor == DatabaseVendor.SQLSERVER) {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Relational connection host must be provided when jdbcUrl is not configured.");
            }
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("Relational connection database must be provided when jdbcUrl is not configured.");
            }
        }
    }

    private void rejectPlaceholder(String propertyName, String value) {
        if (value != null && value.matches(PLACEHOLDER_TOKEN_PATTERN)) {
            throw new IllegalArgumentException(
                    "Relational connection " + propertyName + " still contains a placeholder value '" + value + "'. " +
                    "Replace template tokens like <...> with real environment-specific connection settings before runtime."
            );
        }
    }
}


