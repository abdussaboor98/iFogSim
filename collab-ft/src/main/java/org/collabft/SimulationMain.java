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
import org.collabft.util.TopologyExporter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        MetricsRegistry.collector().markSimStart(0);
        CloudSim.init(1, Calendar.getInstance(), false);
        CloudSim.terminateSimulation(config.getSimulation().getDurationSeconds());

        TokenManager tokenManager = new TokenManager();
        ResourceCapacity cloudCap = config.getTopology().getCloud().getCapacity();
        CloudDevice cloud = new CloudDevice("cloud", cloudCap, config.getBidding(), config.getNetwork());
        MetricsRegistry.collector(); // initialize

        List<FogNodeController> controllers = new ArrayList<>();
        List<FogServer> allServers = new ArrayList<>();
        List<EdgeDevice> edges = new ArrayList<>();
        Map<String, List<EdgeDevice>> edgesByController = new HashMap<>();

        int fogIndex = 0;
        for (SimulationConfig.FogNodeConfig nodeConfig : config.getTopology().getFogNodes()) {
            List<FogServer> servers = new ArrayList<>();
            for (int i = 0; i < nodeConfig.getServers(); i++) {
                ResourceCapacity capacity = nodeConfig.getServerCapacity();
                ResourceCapacity scaled = new ResourceCapacity(
                        (long) (capacity.getCpuMips() * nodeConfig.getCapacityMultiplier()),
                        (int) (capacity.getRamMb() * nodeConfig.getCapacityMultiplier()),
                        (long) (capacity.getBandwidth() * nodeConfig.getCapacityMultiplier()));
                scaled.setStorageMb(capacity.getStorageMb());
                scaled.setDownlinkBandwidth(capacity.getDownlinkBandwidth());
                scaled.setUplinkBandwidth(capacity.getUplinkBandwidth());
                scaled.setRatePerMips(capacity.getRatePerMips());
                FogServer server = new FogServer(nodeConfig.getName() + "-server-" + i, scaled);
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
            current.setAllFogIds(controllers.stream().map(FogNodeController::getId).toList());
        }

        // Optional centralized scheduler
        if (config.getSimulation().getMode() == 2) {
            CentralCloudScheduler scheduler = new CentralCloudScheduler("central-scheduler", controllers, cloud, config.getNetwork());
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
                        config.getTask(), config.getTopology().getEdge(),
                        config.getSimulation().getSeed() + i);
                edge.setParentId(controller.getId());
                edges.add(edge);
                edgesByController.computeIfAbsent(controller.getName(), k -> new ArrayList<>()).add(edge);
            }
        }

        if (config.getSimulation().getMode() == 1 && config.getGossip().isEnabled()) {
            double interval = config.getGossip().getIntervalSeconds();
            new GossipAgent("gossip-agent", controllers, interval);
        }

        new FaultInjector("fault-injector", controllers, config.getFault().getMeanTimeBetweenFailureSeconds(),
                config.getFault().getRecoverySeconds(), config.getFault().getPredictionLeadSeconds(), config.getSimulation().getSeed() + 42,
                config.getFault().getCpuFailureProb(), config.getFault().getBandwidthDegradationProb(), config.getFault().getServerCrashProb(),
                config.getFault().getStartDelaySeconds());

        try {
            TopologyExporter.export(Path.of("exports"), cloud, controllers, edgesByController, config.getNetwork());
            Log.printLine("Topology exported to exports/topology.graphml and exports/topology.json");
        } catch (Exception e) {
            Log.printLine("Failed to export topology: " + e.getMessage());
        }

        CloudSim.startSimulation();
        CloudSim.stopSimulation();
        MetricsRegistry.collector().markSimFinish(config.getSimulation().getDurationSeconds());
        // Export metrics to logs/metrics_*.json
        Path metricsDir = Path.of("logs");
        try {
            MetricsRegistry.collector().export(metricsDir);
            Log.printLine("Metrics exported to " + metricsDir.toAbsolutePath());
        } catch (Exception e) {
            Log.printLine("Failed to export metrics: " + e.getMessage());
        }
        try {
            MetricsSummarizer.summarize(MetricsRegistry.collector(), Path.of("logs"));
            Log.printLine("Summary exported to logs/summary.json");
        } catch (Exception e) {
            Log.printLine("Failed to write summary: " + e.getMessage());
        }
        Log.printLine("Simulation finished.");
    }
}
