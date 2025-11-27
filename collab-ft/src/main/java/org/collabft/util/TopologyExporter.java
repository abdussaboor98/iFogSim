package org.collabft.util;

import org.collabft.agents.CloudDevice;
import org.collabft.agents.EdgeDevice;
import org.collabft.agents.FogServer;
import org.collabft.agents.FogNodeController;
import org.collabft.config.SimulationConfig;
import org.collabft.metrics.MetricsCollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dumps the simulated topology to GraphML and JSON for visualization as described in VISUALIZATION.md.
 */
public final class TopologyExporter {
    private TopologyExporter() {
    }

    public static void export(Path exportDir,
                              CloudDevice cloud,
                              List<FogNodeController> controllers,
                              Map<String, List<EdgeDevice>> edgesByController,
                              SimulationConfig.Network network) throws IOException {
        Files.createDirectories(exportDir);
        Path graphMl = exportDir.resolve("topology.graphml");
        Path json = exportDir.resolve("topology.json");
        Files.writeString(graphMl, toGraphMl(cloud, controllers, edgesByController, network));
        Files.writeString(json, toJson(cloud, controllers, edgesByController, network));
    }

    private static String toGraphMl(CloudDevice cloud,
                                    List<FogNodeController> controllers,
                                    Map<String, List<EdgeDevice>> edgesByController,
                                    SimulationConfig.Network network) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <graphml xmlns="http://graphml.graphdrawing.org/xmlns">
                  <key id="type" for="node" attr.name="type" attr.type="string"/>
                  <key id="x" for="node" attr.name="x" attr.type="double"/>
                  <key id="y" for="node" attr.name="y" attr.type="double"/>
                  <key id="bandwidth" for="edge" attr.name="bandwidthMbps" attr.type="double"/>
                  <key id="latency" for="edge" attr.name="latencyMs" attr.type="double"/>
                  <graph id="collabft" edgedefault="undirected">
                """);
        sb.append(node(cloud.getName(), "cloud", cloud.getLocation()));
        for (FogNodeController controller : controllers) {
            sb.append(node(controller.getName(), "fog-node", controller.getLocation()));
            for (FogServer server : controller.getServers()) {
                sb.append(node(server.getName(), "fog-server", null));
            }
            for (EdgeDevice edge : edgesByController.getOrDefault(controller.getName(), List.of())) {
                sb.append(node(edge.getName(), "edge-device", null));
            }
        }

        // Cloud to controllers
        for (FogNodeController controller : controllers) {
            sb.append(edge(cloud.getName(), controller.getName(), network.getFogCloudBandwidthMbps(),
                    latencyMs(cloud.getLocation(), controller.getLocation(), network)));
        }
        // Ring connections between controllers
        for (int i = 0; i < controllers.size(); i++) {
            FogNodeController a = controllers.get(i);
            FogNodeController b = controllers.get((i + 1) % controllers.size());
            sb.append(edge(a.getName(), b.getName(), network.getInterFogBandwidthMbps(),
                    latencyMs(a.getLocation(), b.getLocation(), network)));
        }
        // Controller to servers and edges
        for (FogNodeController controller : controllers) {
            for (FogServer server : controller.getServers()) {
                sb.append(edge(controller.getName(), server.getName(), server.getCapacity().getBandwidth(), 0));
            }
            for (EdgeDevice edgeDevice : edgesByController.getOrDefault(controller.getName(), List.of())) {
                sb.append(edge(controller.getName(), edgeDevice.getName(), edgeDevice.getUplinkBandwidth(), edgeDevice.getUplinkLatency()));
            }
        }

        sb.append("  </graph>\n</graphml>\n");
        return sb.toString();
    }

    private static String node(String id, String type, org.collabft.model.Position position) {
        StringBuilder sb = new StringBuilder("    <node id=\"").append(id).append("\">")
                .append("<data key=\"type\">").append(type).append("</data>");
        if (position != null) {
            sb.append("<data key=\"x\">").append(position.getX()).append("</data>")
                    .append("<data key=\"y\">").append(position.getY()).append("</data>");
        }
        sb.append("</node>\n");
        return sb.toString();
    }

    private static String edge(String source, String target, double bandwidth, double latencyMs) {
        return "    <edge source=\"" + source + "\" target=\"" + target + "\">"
                + "<data key=\"bandwidth\">" + bandwidth + "</data>"
                + "<data key=\"latency\">" + latencyMs + "</data>"
                + "</edge>\n";
    }

    private static String toJson(CloudDevice cloud,
                                 List<FogNodeController> controllers,
                                 Map<String, List<EdgeDevice>> edgesByController,
                                 SimulationConfig.Network network) {
        Map<String, Object> root = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();

        nodes.add(jsonNode(cloud.getName(), "cloud", cloud.getLocation()));
        for (FogNodeController controller : controllers) {
            nodes.add(jsonNode(controller.getName(), "fog-node", controller.getLocation()));
            for (FogServer server : controller.getServers()) {
                nodes.add(jsonNode(server.getName(), "fog-server", null));
                links.add(jsonLink(controller.getName(), server.getName(), server.getCapacity().getBandwidth(), 0));
            }
            for (EdgeDevice edge : edgesByController.getOrDefault(controller.getName(), List.of())) {
                nodes.add(jsonNode(edge.getName(), "edge-device", null));
                links.add(jsonLink(controller.getName(), edge.getName(), edge.getUplinkBandwidth(), edge.getUplinkLatency()));
            }
            links.add(jsonLink(cloud.getName(), controller.getName(), network.getFogCloudBandwidthMbps(),
                    latencyMs(cloud.getLocation(), controller.getLocation(), network)));
        }
        for (int i = 0; i < controllers.size(); i++) {
            FogNodeController a = controllers.get(i);
            FogNodeController b = controllers.get((i + 1) % controllers.size());
            links.add(jsonLink(a.getName(), b.getName(), network.getInterFogBandwidthMbps(),
                    latencyMs(a.getLocation(), b.getLocation(), network)));
        }

        root.put("nodes", nodes);
        root.put("links", links);
        return MetricsCollector.JsonUtil.toJsonObject(root);
    }

    private static Map<String, Object> jsonNode(String id, String type, org.collabft.model.Position position) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("type", type);
        if (position != null) {
            node.put("x", position.getX());
            node.put("y", position.getY());
        }
        return node;
    }

    private static Map<String, Object> jsonLink(String source, String target, double bandwidth, double latencyMs) {
        Map<String, Object> link = new HashMap<>();
        link.put("source", source);
        link.put("target", target);
        link.put("bandwidthMbps", bandwidth);
        link.put("latencyMs", latencyMs);
        return link;
    }

    private static double latencyMs(org.collabft.model.Position a, org.collabft.model.Position b, SimulationConfig.Network network) {
        if (a != null && b != null) {
            return network.getBaseLatencyMs() + a.distanceTo(b) * network.getLatencyMsPerUnit();
        }
        return network.getInterFogLatencyMs();
    }
}
