package com.example.hik.config;

import java.util.Objects;

/**
 * Root configuration object that mirrors the structure of the YAML configuration file.
 */
public class ApplicationConfig {
    private HikvisionConfig hikvision = new HikvisionConfig();
    private DatabaseConfig database = new DatabaseConfig();
    private boolean onlySuccess = true;
    private boolean skipBlankRecords = true;

    public HikvisionConfig getHikvision() {
        if (hikvision == null) {
            hikvision = new HikvisionConfig();
        }
        return hikvision;
    }

    public void setHikvision(HikvisionConfig hikvision) {
        this.hikvision = hikvision;
    }

    public DatabaseConfig getDatabase() {
        if (database == null) {
            database = new DatabaseConfig();
        }
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }

    public boolean isOnlySuccess() {
        return onlySuccess;
    }

    public void setOnlySuccess(boolean onlySuccess) {
        this.onlySuccess = onlySuccess;
    }

    public boolean isSkipBlankRecords() {
        return skipBlankRecords;
    }

    public void setSkipBlankRecords(boolean skipBlankRecords) {
        this.skipBlankRecords = skipBlankRecords;
    }

    /**
     * Apply default values to nested objects. This is invoked after deserialisation
     * to make sure optional sections are still initialised.
     */
    public void applyDefaults() {
        getHikvision().applyDefaults();
        getDatabase();
    }

    @Override
    public String toString() {
        return "ApplicationConfig{" +
            "hikvision=" + getHikvision() +
            ", database=" + getDatabase() +
            ", onlySuccess=" + onlySuccess +
            ", skipBlankRecords=" + skipBlankRecords +
            '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHikvision(), getDatabase(), onlySuccess, skipBlankRecords);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApplicationConfig)) {
            return false;
        }
        ApplicationConfig that = (ApplicationConfig) o;
        return onlySuccess == that.onlySuccess
            && skipBlankRecords == that.skipBlankRecords
            && Objects.equals(getHikvision(), that.getHikvision())
            && Objects.equals(getDatabase(), that.getDatabase());
    }
}
