package org.collabft;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.collabft.agents.*;
import org.collabft.config.ConfigLoader;
import org.collabft.config.SimulationConfig;
import org.collabft.economy.TokenManager;
import org.collabft.metrics.MetricsRegistry;
import org.collabft.metrics.MetricsSummarizer;
import org.collabft.model.ContainerProfile;
import org.collabft.model.ResourceCapacity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Entry point for the collaborative fault tolerance experiments.
 */
public class SimulationMain {
    public static void main(String[] args) throws Exception {
        Path configPath = args.length > 0 ? Path.of(args[0]) : null;
        SimulationConfig config = configPath != null ? ConfigLoader.load(configPath)
                : ConfigLoader.loadFromClasspath("collabft-config.yaml");

        // Enable console logging so runs show visible progress.
        Log.enable();
        Log.printLine("Starting collab-ft simulation; mode=" + config.getSimulation().getMode());
        CloudSim.init(1, Calendar.getInstance(), false);
        CloudSim.terminateSimulation(config.getSimulation().getDurationSeconds());

        TokenManager tokenManager = new TokenManager();
        ResourceCapacity cloudCap = config.getTopology().getCloud().getCapacity();
        CloudDevice cloud = new CloudDevice("cloud", cloudCap);
        MetricsRegistry.collector(); // initialize

        List<FogNodeController> controllers = new ArrayList<>();
        List<FogServer> allServers = new ArrayList<>();

        int fogIndex = 0;
        for (SimulationConfig.FogNodeConfig nodeConfig : config.getTopology().getFogNodes()) {
            List<FogServer> servers = new ArrayList<>();
            for (int i = 0; i < nodeConfig.getServers(); i++) {
                ResourceCapacity capacity = nodeConfig.getServerCapacity();
                FogServer server = new FogServer(nodeConfig.getName() + "-server-" + i, capacity);
                servers.add(server);
                allServers.add(server);
            }
            FogNodeController controller = new FogNodeController(nodeConfig.getName(), config, nodeConfig.getServerCapacity(), servers, tokenManager);
            controller.setCloudId(cloud.getId());
            controllers.add(controller);
            fogIndex++;
        }

        // Ring neighbors
        for (int i = 0; i < controllers.size(); i++) {
            FogNodeController current = controllers.get(i);
            FogNodeController next = controllers.get((i + 1) % controllers.size());
            current.setNeighborIds(List.of(next.getId()));
        }

        // Optional centralized scheduler
        if (config.getSimulation().getMode() == 2) {
            CentralCloudScheduler scheduler = new CentralCloudScheduler("central-scheduler", controllers, cloud);
            for (FogNodeController controller : controllers) {
                controller.setSchedulerId(scheduler.getId());
            }
        }

        // Edge devices per fog node
        for (FogNodeController controller : controllers) {
            for (int i = 0; i < config.getTopology().getEdge().getDevicesPerFog(); i++) {
                ContainerProfile profile = config.getTask().getDefaultProfile();
                ResourceCapacity tiny = new ResourceCapacity(500, 512, 1000);
                EdgeDevice edge = new EdgeDevice(controller.getName() + "-edge-" + i, tiny, profile,
                        config.getTask().getTasksPerEdge(), config.getTask().getMeanInterArrivalSeconds(),
                        config.getSimulation().getSeed() + i);
                edge.setParentId(controller.getId());
            }
        }

        if (config.getSimulation().getMode() == 1) {
            double interval = config.getGossip().getIntervalSeconds();
            new GossipAgent("gossip-agent", controllers, interval);
        }

        new FaultInjector("fault-injector", controllers, config.getFault().getMeanTimeBetweenFailureSeconds(),
                config.getFault().getRecoverySeconds(), config.getFault().getPredictionLeadSeconds(), config.getSimulation().getSeed() + 42);

        CloudSim.startSimulation();
        CloudSim.stopSimulation();
        // Export metrics to logs/metrics_*.json
        Path metricsDir = Path.of("logs");
        try {
            MetricsRegistry.collector().export(metricsDir);
            Log.printLine("Metrics exported to " + metricsDir.toAbsolutePath());
        } catch (Exception e) {
            Log.printLine("Failed to export metrics: " + e.getMessage());
        }
        try {
            MetricsSummarizer.summarize(MetricsRegistry.collector(), Path.of("results"));
            Log.printLine("Summary exported to results/summary.json");
        } catch (Exception e) {
            Log.printLine("Failed to write summary: " + e.getMessage());
        }
        Log.printLine("Simulation finished.");
    }
}
