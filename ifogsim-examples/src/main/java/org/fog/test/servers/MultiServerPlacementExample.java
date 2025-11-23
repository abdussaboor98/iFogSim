package org.fog.test.servers;

import org.cloudbus.cloudsim.core.CloudSim;
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
 * Demonstrates how a FogDevice distributes containers across multiple internal servers.
 */
public class MultiServerPlacementExample {

    public static void main(String[] args) {
        try {
            CloudSim.init(1, Calendar.getInstance(), false);

            List<FogServer> servers = Arrays.asList(
                    ServerExampleUtils.createServer("rack-0", 2000, 2048, 500000, 10000),
                    ServerExampleUtils.createServer("rack-1", 2500, 4096, 500000, 15000),
                    ServerExampleUtils.createServer("rack-2", 3000, 4096, 750000, 20000)
            );
            FogDevice edgeCluster = ServerExampleUtils.createFogDeviceWithServers(
                    "edge-cluster",
                    8000,
                    8192,
                    10000,
                    20000,
                    1,
                    0.01,
                    120,
                    85,
                    servers);

            List<FogDevice> devices = Collections.singletonList(edgeCluster);
            new Controller("multi-server-controller", devices, new ArrayList<>(), new ArrayList<>());

            List<ContainerInstance> workloads = Arrays.asList(
                    new ContainerInstance("video-analytics", 1200, 512, 400, 200, 200, 20, 64),
                    new ContainerInstance("sensor-fusion", 800, 256, 200, 100, 150, 15, 32),
                    new ContainerInstance("alerting", 700, 256, 150, 80, 90, 10, 16),
                    new ContainerInstance("reporting", 1500, 1024, 300, 250, 220, 25, 96)
            );
            for (ContainerInstance container : workloads) {
                if (!edgeCluster.allocateContainer(container)) {
                    throw new IllegalStateException("Unable to place container " + container.getName());
                }
            }

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            System.out.println("=== Placement summary for " + edgeCluster.getName() + " ===");
            for (FogServer server : edgeCluster.getServers()) {
                System.out.printf("Server %s hosts %d containers with %.2f%% CPU utilization%n",
                        server.getId(),
                        server.getContainers().size(),
                        server.getCpuUtilizationFraction() * 100);
            }
        } catch (Exception e) {
            throw new RuntimeException("MultiServerPlacementExample failed", e);
        }
    }
}
