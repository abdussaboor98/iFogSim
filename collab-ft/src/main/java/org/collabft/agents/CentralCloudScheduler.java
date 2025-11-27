package org.collabft.agents;

import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.CloudSim;
import org.collabft.events.CollabSimTags;
import org.collabft.model.ContainerModule;
import org.collabft.model.MigrationRequest;
import org.collabft.model.MigrationTransfer;
import org.collabft.model.MigrationResult;
import org.collabft.metrics.MetricsCollector;
import org.collabft.config.SimulationConfig;
import org.collabft.model.Position;

import java.util.List;
import java.util.Map;

/**
 * Centralized scheduler used in simulation mode 2.
 */
public class CentralCloudScheduler extends SimEntity {
    private final List<FogNodeController> controllers;
    private final CloudDevice cloud;
    private final SimulationConfig.Network network;
    private final Map<Integer, Position> locations;

    public CentralCloudScheduler(String name, List<FogNodeController> controllers, CloudDevice cloud, SimulationConfig.Network network, Map<Integer, Position> locations) {
        super(name);
        this.controllers = controllers;
        this.cloud = cloud;
        this.network = network;
        this.locations = locations;
    }

    @Override
    public void startEntity() {
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() instanceof CollabSimTags tag && tag == CollabSimTags.MIGRATION_REQUEST
                && ev.getData() instanceof MigrationRequest request) {
            handleRequest(request);
        }
    }

    @Override
    public void shutdownEntity() {
    }

    private void handleRequest(MigrationRequest request) {
        ContainerModule container = request.getContainer();
        FogNodeController bestController = null;
        double bestScore = -1;
        for (FogNodeController controller : controllers) {
            for (FogServer server : controller.getServers()) {
                double score = server.residualScore(container.getProfile());
                if (score > bestScore) {
                    bestScore = score;
                    bestController = controller;
                }
            }
        }
        if (bestController != null && bestScore > 0) {
            container.recordLastBid(bestController.getId(), 0.0);
            send(bestController.getId(), 0, CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, request.getOriginId(), sourceName(container, request.getOriginId()), MetricsCollector.MigrationKind.INTER_FOG,
                            Math.min(bestController.getCapacity().getUplinkBandwidth(), network.getInterFogBandwidthMbps()),
                            latencyMs(request.getOriginId(), bestController.getId()) / 1000.0,
                            container.getMigrationTrigger()));
        } else if (cloud.canHost(container.getProfile())) {
            container.recordLastBid(cloud.getId(), 0.0);
            send(cloud.getId(), 0, CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, request.getOriginId(), sourceName(container, request.getOriginId()), MetricsCollector.MigrationKind.CLOUD,
                            Math.min(cloud.getCapacity().getUplinkBandwidth(), network.getFogCloudBandwidthMbps()),
                            latencyMs(request.getOriginId(), cloud.getId()) / 1000.0,
                            container.getMigrationTrigger()));
        } else {
            send(request.getOriginId(), CloudSim.getMinTimeBetweenEvents(), CollabSimTags.MIGRATION_FINISH,
                    new MigrationResult(container, MetricsCollector.MigrationKind.CLOUD, sourceName(container, request.getOriginId()),
                            "unplaced", container.getMigrationStart(), CloudSim.clock(), 0, 0, false, container.getMigrationTrigger()));
        }
    }

    private String sourceName(ContainerModule container, int originId) {
        if (container.getHostName() != null) {
            return container.getHostName();
        }
        for (FogNodeController controller : controllers) {
            if (controller.getId() == originId) {
                return controller.getName();
            }
        }
        return "unknown";
    }

    private double latencyMs(int fromId, int toId) {
        Position from = locations.get(fromId);
        Position to = locations.get(toId);
        if (from != null && to != null) {
            return network.getBaseLatencyMs() + from.distanceTo(to) * network.getLatencyMsPerUnit();
        }
        boolean isCloud = toId == cloud.getId() || fromId == cloud.getId();
        return isCloud ? network.getFogCloudLatencyMs() : network.getInterFogLatencyMs();
    }
}
