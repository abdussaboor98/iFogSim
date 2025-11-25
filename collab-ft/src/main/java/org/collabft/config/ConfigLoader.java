package org.collabft.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads YAML configuration into {@link SimulationConfig}.
 */
public final class ConfigLoader {
    private ConfigLoader() {
    }

    public static SimulationConfig load(Path path) {
        if (path == null || !Files.exists(path)) {
            return withDefaults();
        }
        try (InputStream in = Files.newInputStream(path)) {
            return postProcess(new Yaml(new Constructor(SimulationConfig.class, new LoaderOptions())).load(in));
        } catch (Exception ex) {
            ex.printStackTrace();
            return withDefaults();
        }
    }

    public static SimulationConfig loadFromClasspath(String resource) {
        InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            return withDefaults();
        }
        return postProcess(new Yaml(new Constructor(SimulationConfig.class, new LoaderOptions())).load(in));
    }

    private static SimulationConfig withDefaults() {
        SimulationConfig config = new SimulationConfig();
        config.getTopology().setFogNodes(defaultFogNodes());
        return config;
    }

    private static List<SimulationConfig.FogNodeConfig> defaultFogNodes() {
        List<SimulationConfig.FogNodeConfig> fogs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            SimulationConfig.FogNodeConfig node = new SimulationConfig.FogNodeConfig();
            node.setName("fog-" + (i + 1));
            fogs.add(node);
        }
        return fogs;
    }

    private static SimulationConfig postProcess(SimulationConfig config) {
        if (config == null) {
            return withDefaults();
        }
        if (config.getTopology().getFogNodes() == null || config.getTopology().getFogNodes().isEmpty()) {
            config.getTopology().setFogNodes(defaultFogNodes());
        }
        return config;
    }
}
