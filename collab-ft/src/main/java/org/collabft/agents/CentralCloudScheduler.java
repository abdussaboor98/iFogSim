package org.collabft.agents;

import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.events.CollabSimTags;
import org.collabft.model.ContainerModule;
import org.collabft.model.MigrationRequest;
import org.collabft.model.MigrationTransfer;
import org.collabft.metrics.MetricsCollector;
import org.collabft.config.SimulationConfig;

import java.util.List;

/**
 * Centralized scheduler used in simulation mode 2.
 */
public class CentralCloudScheduler extends SimEntity {
    private final List<FogNodeController> controllers;
    private final CloudDevice cloud;
    private final SimulationConfig.Network network;

    public CentralCloudScheduler(String name, List<FogNodeController> controllers, CloudDevice cloud, SimulationConfig.Network network) {
        super(name);
        this.controllers = controllers;
        this.cloud = cloud;
        this.network = network;
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
                if (!server.canHost(container.getProfile())) {
                    continue;
                }
                double score = server.getAvailableCpu();
                if (score > bestScore) {
                    bestScore = score;
                    bestController = controller;
                }
            }
        }
        if (bestController != null) {
            container.recordLastBid(bestController.getId(), 0.0);
            send(bestController.getId(), 0, CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, request.getOriginId(), sourceName(container, request.getOriginId()), MetricsCollector.MigrationKind.INTER_FOG,
                            Math.min(bestController.getCapacity().getUplinkBandwidth(), network.getInterFogBandwidthMbps()),
                            network.getInterFogLatencyMs() / 1000.0,
                            container.getMigrationTrigger()));
        } else {
            container.recordLastBid(cloud.getId(), 0.0);
            send(cloud.getId(), 0, CollabSimTags.MIGRATION_START,
                    new MigrationTransfer(container, request.getOriginId(), sourceName(container, request.getOriginId()), MetricsCollector.MigrationKind.CLOUD,
                            Math.min(cloud.getCapacity().getUplinkBandwidth(), network.getFogCloudBandwidthMbps()),
                            network.getFogCloudLatencyMs() / 1000.0,
                            container.getMigrationTrigger()));
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
}
