package org.fog.test.servers;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.entities.FogDevice;
import org.fog.entities.FogServer;
import org.fog.entities.container.ContainerInstance;
import org.fog.placement.Controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Shows how a container can migrate between servers inside the same fog node.
 */
public class IntraFogMigrationExample {

    private enum MigrationEvents implements CloudSimTags {
        TRIGGER_MIGRATION
    }

    public static void main(String[] args) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            List<FogServer> servers = Arrays.asList(
                    ServerExampleUtils.createServer("compute-0", 2000, 2048, 300000, 10000),
                    ServerExampleUtils.createServer("compute-1", 2500, 4096, 300000, 12000)
            );
            FogDevice node = ServerExampleUtils.createFogDeviceWithServers(
                    "dual-server-node",
                    5000,
                    6144,
                    8000,
                    15000,
                    1,
                    0.008,
                    110,
                    80,
                    servers);

            List<FogDevice> devices = Collections.singletonList(node);
            new Controller("intra-migration-controller", devices, new ArrayList<>(), new ArrayList<>());

            ContainerInstance analytics = new ContainerInstance("anomaly-detector", 1500, 1024, 400, 220, 400, 30, 64);
            if (!node.allocateContainer(analytics)) {
                throw new IllegalStateException("Failed to place anomaly-detector on " + node.getName());
            }

            new IntraNodeMigrationOrchestrator(node, analytics, servers.get(1).getId(), 150);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            for (FogServer server : node.getServers()) {
                System.out.printf("Server %s final containers: %s%n", server.getId(), server.getContainers());
            }
        } catch (Exception e) {
            throw new RuntimeException("IntraFogMigrationExample failed", e);
        }
    }

    private static class IntraNodeMigrationOrchestrator extends SimEntity {
        private final FogDevice device;
        private final ContainerInstance container;
        private final String destinationServerId;
        private final double migrationDelay;

        IntraNodeMigrationOrchestrator(FogDevice device, ContainerInstance container, String destinationServerId, double migrationDelay) {
            super("intra-node-migrator");
            this.device = device;
            this.container = container;
            this.destinationServerId = destinationServerId;
            this.migrationDelay = migrationDelay;
        }

        @Override
        public void startEntity() {
            schedule(getId(), migrationDelay, MigrationEvents.TRIGGER_MIGRATION);
        }

        @Override
        public void processEvent(SimEvent ev) {
            if (ev.getTag() == MigrationEvents.TRIGGER_MIGRATION) {
                boolean result = device.migrateContainerWithinNode(container, destinationServerId);
                System.out.printf("[t=%.2f] Requested intra-node migration to %s -> %s%n",
                        CloudSim.clock(),
                        destinationServerId,
                        result ? "SUCCESS" : "FAILED");
            }
        }

        @Override
        public void shutdownEntity() {
        }
    }
}
