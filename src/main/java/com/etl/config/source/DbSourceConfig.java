package com.etl.config.source;

import com.etl.config.FieldDefinition;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public class DbSourceConfig extends SourceConfig {

    private String url;
    private String username;
    private String password;
    private String query;

    @JsonDeserialize(contentAs = ColumnConfig.class)
    private List<ColumnConfig> fields;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    // You may later encrypt / externalize this
    public void setPassword(String password) {
        this.password = password;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    @Override
    public String getType() {
        return "db";
    }

    @Override
    public List<? extends FieldDefinition> getFields() {
        return fields;
    }

    public void setFields(List<ColumnConfig> fields) {
        this.fields = fields;
    }
}
