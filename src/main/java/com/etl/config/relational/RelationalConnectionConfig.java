package com.etl.config.relational;

public class RelationalConnectionConfig {

    private static final String PLACEHOLDER_TOKEN_PATTERN = ".*<[^>]+>.*";
    private static final String SQLSERVER_URL_PREFIX = "jdbc:sqlserver:";
    private static final String H2_URL_PREFIX = "jdbc:h2:";

    private String vendor;
    private String connectionString;
    private String jdbcUrl;
    private String host;
    private Integer port;
    private String database;
    private String schema;
    private String username;
    private String password;
    private String usernameEnvVar;
    private String passwordEnvVar;
    private String driverClassName;

  public String getVendor() {
    return vendor;
  }

  public void setVendor(String vendor) {
    this.vendor = vendor;
  }

  public String getJdbcUrl() {
    if (jdbcUrl != null && !jdbcUrl.isBlank()) {
      return jdbcUrl;
    }
    return connectionString;
  }

  public void setJdbcUrl(String jdbcUrl) {
    this.jdbcUrl = jdbcUrl;
  }

  public String getConnectionString() {
    return connectionString;
  }

  public void setConnectionString(String connectionString) {
    this.connectionString = connectionString;
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

  public String getUsernameEnvVar() {
    return usernameEnvVar;
  }

  public void setUsernameEnvVar(String usernameEnvVar) {
    this.usernameEnvVar = usernameEnvVar;
  }

  public String getPasswordEnvVar() {
    return passwordEnvVar;
  }

  public void setPasswordEnvVar(String passwordEnvVar) {
    this.passwordEnvVar = passwordEnvVar;
  }

    public String resolveUsername() {
        return resolveCredential("username", username, usernameEnvVar, false, true);
    }

    public String resolvePassword() {
        return resolveCredential("password", password, passwordEnvVar, true, true);
    }

    public String resolveUsernameOrNull() {
        return resolveCredential("username", username, usernameEnvVar, false, false);
    }

    public String resolvePasswordOrNull() {
        return resolveCredential("password", password, passwordEnvVar, true, false);
    }

    public DatabaseVendor getResolvedVendor() {
        if (vendor != null && !vendor.isBlank()) {
            return DatabaseVendor.fromString(vendor);
        }
        return inferVendorFromJdbcUrl(getJdbcUrl());
    }

    public void validate() {
        String resolvedJdbcUrl = getJdbcUrl();
        if ((vendor == null || vendor.isBlank()) && inferVendorFromJdbcUrl(resolvedJdbcUrl) == null) {
            throw new IllegalArgumentException("Relational connection vendor must be provided (or inferable from jdbcUrl/connectionString).");
        }

        DatabaseVendor resolvedVendor = getResolvedVendor();

        if (port != null && port <= 0) {
            throw new IllegalArgumentException("Relational connection port must be greater than zero when provided.");
        }

        resolveUsernameOrNull();
        resolvePasswordOrNull();

        rejectPlaceholder("connectionString", connectionString);
        rejectPlaceholder("jdbcUrl", resolvedJdbcUrl);
        rejectPlaceholder("host", host);
        rejectPlaceholder("database", database);
        rejectPlaceholder("username", username);
        rejectPlaceholder("password", password);
        rejectPlaceholder("usernameEnvVar", usernameEnvVar);
        rejectPlaceholder("passwordEnvVar", passwordEnvVar);

        if (resolvedJdbcUrl != null && !resolvedJdbcUrl.isBlank()) {
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

    private String resolveCredential(String fieldName, String inlineValue, String envVarName, boolean allowBlankValue, boolean failWhenMissing) {
        if (inlineValue != null && (allowBlankValue || !inlineValue.isBlank())) {
            return inlineValue;
        }

        String credentialFromUrl = extractCredentialFromJdbcUrl(fieldName);
        if (credentialFromUrl != null && (allowBlankValue || !credentialFromUrl.isBlank())) {
            return credentialFromUrl;
        }

        if (envVarName == null || envVarName.isBlank()) {
            if ("username".equals(fieldName) && failWhenMissing) {
                throw new IllegalArgumentException("Relational connection username must be provided.");
            }
            return inlineValue;
        }

        String resolved = System.getenv(envVarName);
        if (resolved == null) {
            resolved = System.getProperty(envVarName);
        }
        if (resolved == null || (!allowBlankValue && resolved.isBlank())) {
            throw new IllegalArgumentException(
                    "Relational connection " + fieldName + "EnvVar '" + envVarName + "' is not set with a usable value."
            );
        }
        return resolved;
    }

    private DatabaseVendor inferVendorFromJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.startsWith(SQLSERVER_URL_PREFIX)) {
            return DatabaseVendor.SQLSERVER;
        }
        if (lowerUrl.startsWith(H2_URL_PREFIX)) {
            return DatabaseVendor.H2;
        }
        return null;
    }

    private String extractCredentialFromJdbcUrl(String fieldName) {
        String url = getJdbcUrl();
        if (url == null || url.isBlank()) {
            return null;
        }

        String lowerUrl = url.toLowerCase();
        if (!lowerUrl.startsWith(SQLSERVER_URL_PREFIX)) {
            return null;
        }

        String token = fieldName.equals("username") ? ";user=" : ";password=";
        int tokenIndex = lowerUrl.indexOf(token);
        if (tokenIndex < 0) {
            return null;
        }

        int valueStart = tokenIndex + token.length();
        int valueEnd = url.indexOf(';', valueStart);
        if (valueEnd < 0) {
            valueEnd = url.length();
        }

        if (valueEnd <= valueStart) {
            return null;
        }
        return url.substring(valueStart, valueEnd);
    }
}


