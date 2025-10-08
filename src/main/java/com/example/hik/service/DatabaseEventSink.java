package com.example.hik.service;

import com.example.hik.config.DatabaseConfig;
import com.example.hik.model.AccessEvent;
import com.example.hik.model.DateRange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Persists events into a relational database using JDBC.
 */
public class DatabaseEventSink implements EventSink, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseEventSink.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DatabaseConfig config;

    private DatabaseEventSink(DatabaseConfig config) {
        this.config = config;
    }

    public static DatabaseEventSink create(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        if (config.getUrl() == null || config.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("database.url is required when database.enabled=true");
        }
        return new DatabaseEventSink(config);
    }

    @Override
    public void accept(DateRange slice, List<AccessEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword())) {
            connection.setAutoCommit(false);
            try {
                insertBatch(connection, events);
                connection.commit();
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                throw new RuntimeException("Failed to persist events", ex);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to persist events", ex);
        }
    }

    private String buildInsertSql() {
        return "INSERT INTO " + config.getTable()
            + " (event_time, card_no, person_id, door_name, success, raw_payload) VALUES (?,?,?,?,?,?)";
    }

    private void insertBatch(Connection connection, List<AccessEvent> events) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(buildInsertSql())) {
            for (AccessEvent event : events) {
                bind(statement, event);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException rollbackEx) {
            LOGGER.warn("Failed to rollback transaction", rollbackEx);
        }
    }

    private void bind(PreparedStatement statement, AccessEvent event) throws SQLException {
        OffsetDateTime time = event.getEventTime();
        if (time == null) {
            statement.setObject(1, null);
        } else {
            statement.setObject(1, time);
        }
        statement.setString(2, event.getCardNumber().orElse(null));
        statement.setString(3, event.getPersonId().orElse(null));
        statement.setString(4, event.getDoorName().orElse(null));
        statement.setBoolean(5, event.isSuccess());
        statement.setString(6, toJson(event));
    }

    private String toJson(AccessEvent event) {
        try {
            return JSON.writeValueAsString(event.getAttributes());
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Failed to serialise event payload, falling back to toString", ex);
            return event.getAttributes().toString();
        }
    }

    @Override
    public void close() {
        // nothing to close because connections are short lived
    }
}
