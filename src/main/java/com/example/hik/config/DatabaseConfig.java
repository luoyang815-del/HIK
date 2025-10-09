package com.example.hik.config;

import java.util.Objects;

/**
 * Configuration for the optional database sink. The application does not require the
 * database section to be present; when disabled the sink simply logs the events.
 */
public class DatabaseConfig {
    private boolean enabled;
    private String url;
    private String username;
    private String password;
    private String table = "acs_events";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    @Override
    public String toString() {
        return "DatabaseConfig{" +
            "enabled=" + enabled +
            ", url='" + url + '\'' +
            ", username='" + username + '\'' +
            ", table='" + table + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DatabaseConfig)) {
            return false;
        }
        DatabaseConfig that = (DatabaseConfig) o;
        return enabled == that.enabled
            && Objects.equals(url, that.url)
            && Objects.equals(username, that.username)
            && Objects.equals(password, that.password)
            && Objects.equals(table, that.table);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, url, username, password, table);
    }
}
