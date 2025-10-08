package com.example.hik.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Loads the YAML configuration file into {@link ApplicationConfig}.
 */
public class ConfigLoader {
    private final ObjectMapper mapper;

    public ConfigLoader() {
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ApplicationConfig load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    public ApplicationConfig load(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        ApplicationConfig config = mapper.readValue(inputStream, ApplicationConfig.class);
        return afterRead(config);
    }

    private ApplicationConfig read(Reader reader) throws IOException {
        ApplicationConfig config = mapper.readValue(reader, ApplicationConfig.class);
        return afterRead(config);
    }

    private ApplicationConfig afterRead(ApplicationConfig config) {
        if (config == null) {
            config = new ApplicationConfig();
        }
        config.applyDefaults();
        return config;
    }
}
