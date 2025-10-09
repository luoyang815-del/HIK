package com.example.hik;

import com.example.hik.client.HikEventFetcher;
import com.example.hik.client.HikEventParser;
import com.example.hik.client.HttpHikEventFetcher;
import com.example.hik.client.InsecureTlsHttpClientFactory;
import com.example.hik.config.ApplicationConfig;
import com.example.hik.config.ConfigLoader;
import com.example.hik.config.DatabaseConfig;
import com.example.hik.service.DatabaseEventSink;
import com.example.hik.service.EventPullService;
import com.example.hik.service.EventSink;
import com.example.hik.service.LoggingEventSink;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

/**
 * CLI entry point.
 */
public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        try {
            new Main().run(args);
        } catch (Exception ex) {
            LOGGER.error("Application failed", ex);
            System.exit(1);
        }
    }

    private void run(String[] args) throws Exception {
        if (args.length == 0 || isHelp(args[0])) {
            printUsage();
            return;
        }
        ApplicationConfig config = loadConfig();
        String command = args[0].toLowerCase();
        if ("pull".equals(command)) {
            executePull(config, args);
        } else {
            LOGGER.error("Unknown command: {}", command);
            printUsage();
        }
    }

    private void executePull(ApplicationConfig config, String[] args) throws Exception {
        if (args.length < 3) {
            LOGGER.error("pull command requires start and end arguments");
            printUsage();
            return;
        }
        OffsetDateTime start = parseDate(args[1]);
        OffsetDateTime end = parseDate(args[2]);
        try (CloseableHttpClient client = InsecureTlsHttpClientFactory.create(
            config.getHikvision().isInsecureTls(),
            config.getHikvision().getRequestTimeout())) {
            HikEventParser parser = new HikEventParser();
            HikEventFetcher fetcher = new HttpHikEventFetcher(config.getHikvision(), client, parser);
            EventSink sink = createSink(config.getDatabase());
            try {
                EventPullService service = new EventPullService(fetcher, sink, config);
                service.pull(start, end);
            } finally {
                closeQuietly(sink);
            }
        }
    }

    private EventSink createSink(DatabaseConfig databaseConfig) {
        if (databaseConfig == null || !databaseConfig.isEnabled()) {
            return new LoggingEventSink();
        }
        try {
            DatabaseEventSink sink = DatabaseEventSink.create(databaseConfig);
            LOGGER.info("Database sink initialised for table {}", databaseConfig.getTable());
            return sink;
        } catch (Exception ex) {
            LOGGER.warn("Failed to initialise database sink, falling back to logging sink", ex);
            return new LoggingEventSink();
        }
    }

    private ApplicationConfig loadConfig() throws IOException {
        String configPath = System.getProperty("config", "config/application.yaml");
        Path path = Paths.get(configPath);
        ConfigLoader loader = new ConfigLoader();
        if (!Files.exists(path)) {
            LOGGER.warn("Configuration file {} not found, using defaults", configPath);
            ApplicationConfig config = new ApplicationConfig();
            config.applyDefaults();
            return config;
        }
        LOGGER.info("Using configuration at {}", path.toAbsolutePath());
        return loader.load(path);
    }

    private OffsetDateTime parseDate(String text) {
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date time: " + text, ex);
        }
    }

    private boolean isHelp(String value) {
        if (value == null) {
            return true;
        }
        String lower = value.toLowerCase();
        return Arrays.asList("-h", "--help", "help").contains(lower);
    }

    private void printUsage() {
        System.out.println("Usage: java -jar <jar> pull <start> <end>");
        System.out.println("  start/end must be ISO-8601 date time values with offset, e.g. 2025-09-11T00:00:00+08:00");
    }

    private void closeQuietly(EventSink sink) {
        if (!(sink instanceof AutoCloseable)) {
            return;
        }
        try {
            ((AutoCloseable) sink).close();
        } catch (Exception ex) {
            LOGGER.warn("Error while closing event sink", ex);
        }
    }
}
