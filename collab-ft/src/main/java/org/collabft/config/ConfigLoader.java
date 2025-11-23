package org.collabft.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static SimulationConfig load(Path path) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            try (var in = Files.newInputStream(path)) {
                return mapper.readValue(in, SimulationConfig.class);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config from " + path.toAbsolutePath(), e);
        }
    }
}
