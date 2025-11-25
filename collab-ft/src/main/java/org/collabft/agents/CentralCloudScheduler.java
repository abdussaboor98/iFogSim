package org.collabft.agents;

import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.collabft.events.CollabSimTags;
import org.collabft.model.ContainerModule;
import org.collabft.model.MigrationRequest;

import java.util.List;

/**
 * Centralized scheduler used in simulation mode 2.
 */
public class CentralCloudScheduler extends SimEntity {
    private final List<FogNodeController> controllers;
    private final CloudDevice cloud;

    public CentralCloudScheduler(String name, List<FogNodeController> controllers, CloudDevice cloud) {
        super(name);
        this.controllers = controllers;
        this.cloud = cloud;
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
            send(bestController.getId(), 0, CollabSimTags.MIGRATION_START, container);
        } else {
            send(cloud.getId(), 0, CollabSimTags.MIGRATION_START, container);
        }
    }
}
