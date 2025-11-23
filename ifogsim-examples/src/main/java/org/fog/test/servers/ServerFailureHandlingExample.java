package org.fog.test.servers;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.entities.FogDevice;
import org.fog.entities.FogServer;
import org.fog.entities.FogServerHealthState;
import org.fog.entities.FogServerStateChange;
import org.fog.entities.container.ContainerInstance;
import org.fog.placement.Controller;
import org.fog.utils.FogEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Illustrates how server-specific failures and recoveries are handled with graceful container migration.
 */
public class ServerFailureHandlingExample {

    private enum FailureEvents implements CloudSimTags {
        WARN_FAILURE,
        TRIGGER_FAILURE,
        TRIGGER_RECOVERY
    }

    public static void main(String[] args) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            List<FogServer> servers = Arrays.asList(
                    ServerExampleUtils.createServer("edge-slot-0", 1800, 2048, 400000, 8000),
                    ServerExampleUtils.createServer("edge-slot-1", 2200, 4096, 400000, 12000),
                    ServerExampleUtils.createServer("edge-slot-2", 2500, 4096, 500000, 15000)
            );
            FogDevice node = ServerExampleUtils.createFogDeviceWithServers(
                    "resilient-node",
                    7000,
                    8192,
                    9000,
                    20000,
                    1,
                    0.009,
                    125,
                    90,
                    servers);

            List<FogDevice> devices = Collections.singletonList(node);
            new Controller("server-failure-controller", devices, new ArrayList<>(), new ArrayList<>());

            List<ContainerInstance> workloads = Arrays.asList(
                    new ContainerInstance("stream-preprocessor", 900, 512, 200, 120, 300, 20, 48),
                    new ContainerInstance("inference-engine", 1500, 1024, 350, 200, 400, 25, 64),
                    new ContainerInstance("aggregator", 800, 512, 200, 150, 320, 20, 48)
            );
            for (ContainerInstance container : workloads) {
                if (!node.allocateContainer(container)) {
                    throw new IllegalStateException("Unable to deploy " + container.getName());
                }
            }

            new ServerFailureOrchestrator(node, servers.get(0).getId(), 100, 130, 250);

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("=== Post-failure container distribution ===");
            for (FogServer server : node.getServers()) {
                System.out.printf("%s -> %d containers, health=%s%n",
                        server.getId(),
                        server.getContainers().size(),
                        server.getHealthState());
            }
        } catch (Exception e) {
            throw new RuntimeException("ServerFailureHandlingExample failed", e);
        }
    }

    private static class ServerFailureOrchestrator extends SimEntity {
        private final FogDevice device;
        private final String failingServerId;
        private final double warnTime;
        private final double failureTime;
        private final double recoveryTime;

        ServerFailureOrchestrator(FogDevice device, String failingServerId, double warnTime, double failureTime, double recoveryTime) {
            super("server-failure-orchestrator");
            this.device = device;
            this.failingServerId = failingServerId;
            this.warnTime = warnTime;
            this.failureTime = failureTime;
            this.recoveryTime = recoveryTime;
        }

        @Override
        public void startEntity() {
            schedule(getId(), warnTime, FailureEvents.WARN_FAILURE);
            schedule(getId(), failureTime, FailureEvents.TRIGGER_FAILURE);
            schedule(getId(), recoveryTime, FailureEvents.TRIGGER_RECOVERY);
        }

        @Override
        public void processEvent(SimEvent ev) {
            if (ev.getTag() == FailureEvents.WARN_FAILURE) {
                System.out.printf("[t=%.2f] Predicting failure on %s%n", CloudSim.clock(), failingServerId);
                sendStateChange(new FogServerStateChange(failingServerId, FogServerHealthState.PREDICTED_FAIL));
            } else if (ev.getTag() == FailureEvents.TRIGGER_FAILURE) {
                System.out.printf("[t=%.2f] Injecting failure on %s%n", CloudSim.clock(), failingServerId);
                sendStateChange(new FogServerStateChange(failingServerId, FogServerHealthState.FAILED));
            } else if (ev.getTag() == FailureEvents.TRIGGER_RECOVERY) {
                System.out.printf("[t=%.2f] Recovering server %s%n", CloudSim.clock(), failingServerId);
                sendStateChange(new FogServerStateChange(failingServerId, FogServerHealthState.HEALTHY));
            }
        }

        private void sendStateChange(FogServerStateChange change) {
            schedule(device.getId(), 0, FogEvents.SERVER_STATE_CHANGE, change);
        }

        @Override
        public void shutdownEntity() {
        }
    }
}
